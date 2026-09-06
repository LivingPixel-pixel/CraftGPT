package dev.craftgpt.placement.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.craftgpt.placement.PlacementLimits;
import dev.craftgpt.placement.model.PlacementSnapshot;
import dev.craftgpt.placement.model.PlacementState;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Atomic, integrity-checked server-local recovery archives. Archive content is never networked. */
public final class PlacementArchiveRepository {
    private static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path root;

    public PlacementArchiveRepository(Path root) {
        if (root == null) throw new IllegalArgumentException("Archive root is required");
        this.root = root.toAbsolutePath().normalize();
    }

    public synchronized RecoveryArchive export(PlacementJournalRepository.LoadedPlacement loaded)
        throws IOException {
        if (loaded == null) throw new IOException("Placement is required");
        PlacementJournalRepository.validateSnapshot(loaded.snapshot());
        PlacementJournalRepository.validateState(loaded.snapshot(), loaded.state());
        String exportedAt = Instant.now().toString();
        RecoveryArchive archive = new RecoveryArchive(
            FORMAT_VERSION,
            exportedAt,
            PlacementSnapshotHasher.sha256(loaded.snapshot()),
            contentHash(exportedAt, loaded.snapshot(), loaded.state()),
            loaded.snapshot(),
            loaded.state()
        );
        validate(archive, loaded.snapshot().ownerId(), loaded.snapshot().placementId());
        writeJson(archivePath(loaded.snapshot().ownerId(), loaded.snapshot().placementId()), archive);
        return archive;
    }

    public synchronized Optional<RecoveryArchive> load(UUID ownerId, UUID placementId) {
        if (ownerId == null || placementId == null) return Optional.empty();
        try {
            RecoveryArchive archive = readJson(archivePath(ownerId.toString(), placementId.toString()));
            validate(archive, ownerId.toString(), placementId.toString());
            return Optional.of(archive);
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    public synchronized List<RecoveryArchive> list(UUID ownerId) throws IOException {
        if (ownerId == null) return List.of();
        Path directory = ownerDirectory(ownerId.toString());
        List<RecoveryArchive> archives = new ArrayList<>();
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(candidate -> candidate.getFileName().toString().endsWith(".recovery.json")).toList()) {
                String file = path.getFileName().toString();
                String id = file.substring(0, file.length() - ".recovery.json".length());
                try {
                    load(ownerId, UUID.fromString(id)).ifPresent(archives::add);
                } catch (RuntimeException ignored) {
                    // A damaged archive never hides the remaining valid recovery history.
                }
            }
        }
        archives.sort(Comparator.comparing(
            (RecoveryArchive archive) -> Instant.parse(archive.snapshot().createdAt())
        ).reversed());
        return List.copyOf(archives.stream().limit(PlacementLimits.MAX_HISTORY_ENTRIES).toList());
    }

    private void validate(RecoveryArchive archive, String ownerId, String placementId) throws IOException {
        if (archive == null || archive.formatVersion() != FORMAT_VERSION) {
            throw new IOException("Invalid recovery archive format");
        }
        Instant.parse(archive.exportedAt());
        PlacementJournalRepository.validateSnapshot(archive.snapshot());
        PlacementJournalRepository.validateState(archive.snapshot(), archive.state());
        if (!ownerId.equals(archive.snapshot().ownerId())
            || !placementId.equals(archive.snapshot().placementId())
            || !archive.snapshotHash().equals(PlacementSnapshotHasher.sha256(archive.snapshot()))
            || !archive.archiveHash().equals(contentHash(archive.exportedAt(), archive.snapshot(), archive.state()))) {
            throw new IOException("Recovery archive integrity check failed");
        }
    }

    private String contentHash(String exportedAt, PlacementSnapshot snapshot, PlacementState state) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(exportedAt.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(GSON.toJson(snapshot).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(GSON.toJson(state).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private RecoveryArchive readJson(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Archive is not a regular file");
        long size = Files.size(path);
        if (size <= 0 || size > PlacementLimits.MAX_ARCHIVE_JSON_BYTES) throw new IOException("Archive exceeds size limit");
        try {
            RecoveryArchive value = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), RecoveryArchive.class);
            if (value == null) throw new IOException("Archive is empty");
            return value;
        } catch (RuntimeException exception) {
            throw new IOException("Archive JSON is invalid", exception);
        }
    }

    private void writeJson(Path path, RecoveryArchive archive) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "." + path.getFileName() + ".", ".tmp");
        try {
            byte[] bytes = GSON.toJson(archive).getBytes(StandardCharsets.UTF_8);
            if (bytes.length <= 0 || bytes.length > PlacementLimits.MAX_ARCHIVE_JSON_BYTES) {
                throw new IOException("Archive exceeds size limit");
            }
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Path archivePath(String ownerId, String placementId) throws IOException {
        UUID.fromString(placementId);
        Path path = ownerDirectory(ownerId).resolve(placementId + ".recovery.json").normalize();
        if (!path.startsWith(root)) throw new IOException("Invalid archive path");
        return path;
    }

    private Path ownerDirectory(String ownerId) throws IOException {
        UUID.fromString(ownerId);
        Path directory = root.resolve(ownerId).normalize();
        if (!directory.startsWith(root)) throw new IOException("Invalid archive owner path");
        Files.createDirectories(directory);
        return directory;
    }

    public record RecoveryArchive(
        int formatVersion,
        String exportedAt,
        String snapshotHash,
        String archiveHash,
        PlacementSnapshot snapshot,
        PlacementState state
    ) {}
}
