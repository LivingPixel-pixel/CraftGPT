package dev.craftgpt.placement.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.craftgpt.build.BuildLimits;
import dev.craftgpt.placement.PlacementLimits;
import dev.craftgpt.placement.PlacementStatusCodes;
import dev.craftgpt.placement.model.PlacementChange;
import dev.craftgpt.placement.model.PlacementSnapshot;
import dev.craftgpt.placement.model.PlacementState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Server-world storage for immutable before snapshots and mutable crash-recovery progress. */
public final class PlacementJournalRepository {
    private static final Pattern SHA_256 = Pattern.compile("[a-f0-9]{64}");
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    private final Path root;
    private final Map<String, VerifiedSnapshot> verifiedSnapshots = new HashMap<>();

    public PlacementJournalRepository(Path root) {
        if (root == null) {
            throw new IllegalArgumentException("Placement storage root is required");
        }
        this.root = root.toAbsolutePath().normalize();
    }

    public synchronized LoadedPlacement create(PlacementSnapshot snapshot) throws IOException {
        validateSnapshot(snapshot);
        String snapshotHash = PlacementSnapshotHasher.sha256(snapshot);
        PlacementState state = new PlacementState(
            PlacementLimits.SCHEMA_VERSION,
            snapshot.placementId(),
            snapshotHash,
            PlacementStatusCodes.PREPARED,
            0,
            0,
            0,
            Instant.now().toString()
        );
        validateStateWithHash(snapshot, state, snapshotHash);

        Path ownerDirectory = ownerDirectory(snapshot.ownerId());
        writeImmutableJson(ownerDirectory.resolve(snapshot.placementId() + ".snapshot.json"), snapshot);
        writeMutableJson(ownerDirectory.resolve(snapshot.placementId() + ".state.json"), state);
        writeMutableJson(ownerDirectory.resolve("head.json"), new PlacementHead(
            PlacementLimits.SCHEMA_VERSION,
            snapshot.placementId()
        ));
        verifiedSnapshots.put(snapshot.placementId(), new VerifiedSnapshot(snapshot, snapshotHash));
        return new LoadedPlacement(snapshot, state);
    }

    public synchronized boolean exists(UUID ownerId, UUID placementId) throws IOException {
        if (ownerId == null || placementId == null) return false;
        Path directory = ownerDirectory(ownerId.toString());
        return Files.isRegularFile(directory.resolve(placementId + ".snapshot.json"), LinkOption.NOFOLLOW_LINKS)
            && Files.isRegularFile(directory.resolve(placementId + ".state.json"), LinkOption.NOFOLLOW_LINKS);
    }

    public synchronized LoadedPlacement restore(PlacementSnapshot snapshot, PlacementState state) throws IOException {
        validateSnapshot(snapshot);
        validateState(snapshot, state);
        UUID ownerId = UUID.fromString(snapshot.ownerId());
        UUID placementId = UUID.fromString(snapshot.placementId());
        if (exists(ownerId, placementId)) throw new FileAlreadyExistsException(placementId.toString());
        PlacementState restored = PlacementStatusCodes.isBusy(state.status())
            ? new PlacementState(
                PlacementLimits.SCHEMA_VERSION, state.placementId(), state.snapshotHash(),
                PlacementStatusCodes.INTERRUPTED, state.processedChanges(), state.revertedChanges(),
                state.conflicts(), Instant.now().toString())
            : state;
        Path directory = ownerDirectory(snapshot.ownerId());
        writeImmutableJson(directory.resolve(snapshot.placementId() + ".snapshot.json"), snapshot);
        writeMutableJson(directory.resolve(snapshot.placementId() + ".state.json"), restored);
        writeMutableJson(directory.resolve("head.json"), new PlacementHead(PlacementLimits.SCHEMA_VERSION, snapshot.placementId()));
        verifiedSnapshots.put(snapshot.placementId(), new VerifiedSnapshot(snapshot, PlacementSnapshotHasher.sha256(snapshot)));
        return new LoadedPlacement(snapshot, restored);
    }

    public synchronized void remove(UUID ownerId, UUID placementId) throws IOException {
        if (ownerId == null || placementId == null) throw new IOException("Placement identity is required");
        Path directory = ownerDirectory(ownerId.toString());
        Optional<LoadedPlacement> latest = loadLatest(ownerId);
        if (latest.isPresent() && latest.get().snapshot().placementId().equals(placementId.toString())) {
            throw new IOException("Latest placement cannot be pruned");
        }
        Path snapshot = directory.resolve(placementId + ".snapshot.json").normalize();
        Path state = directory.resolve(placementId + ".state.json").normalize();
        if (!snapshot.startsWith(directory) || !state.startsWith(directory)) throw new IOException("Invalid placement path");
        Files.deleteIfExists(snapshot);
        Files.deleteIfExists(state);
        verifiedSnapshots.remove(placementId.toString());
    }

    public synchronized Optional<LoadedPlacement> loadLatest(UUID ownerId) {
        if (ownerId == null) {
            return Optional.empty();
        }
        try {
            Path ownerDirectory = ownerDirectory(ownerId.toString());
            Path headFile = ownerDirectory.resolve("head.json");
            if (!Files.exists(headFile, LinkOption.NOFOLLOW_LINKS)) {
                return Optional.empty();
            }
            PlacementHead head = readJson(headFile, PlacementHead.class);
            if (head.schemaVersion() != PlacementLimits.SCHEMA_VERSION) {
                throw new IOException("Invalid placement head schema");
            }
            requireUuid(head.placementId(), "placement id");
            return Optional.of(load(ownerId, UUID.fromString(head.placementId())));
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    public synchronized LoadedPlacement load(UUID ownerId, UUID placementId) throws IOException {
        if (ownerId == null || placementId == null) {
            throw new IOException("Placement identity is required");
        }
        Path ownerDirectory = ownerDirectory(ownerId.toString());
        PlacementSnapshot snapshot = readJson(
            ownerDirectory.resolve(placementId + ".snapshot.json"),
            PlacementSnapshot.class
        );
        PlacementState state = readJson(
            ownerDirectory.resolve(placementId + ".state.json"),
            PlacementState.class
        );
        validateSnapshot(snapshot);
        String snapshotHash = PlacementSnapshotHasher.sha256(snapshot);
        validateStateWithHash(snapshot, state, snapshotHash);
        if (!ownerId.toString().equals(snapshot.ownerId())
            || !placementId.toString().equals(snapshot.placementId())) {
            throw new IOException("Placement journal identity mismatch");
        }
        verifiedSnapshots.put(snapshot.placementId(), new VerifiedSnapshot(snapshot, snapshotHash));
        return new LoadedPlacement(snapshot, state);
    }

    public synchronized List<LoadedPlacement> listHistory(UUID ownerId) throws IOException {
        if (ownerId == null) {
            return List.of();
        }
        Path directory = ownerDirectory(ownerId.toString());
        List<LoadedPlacement> history = new ArrayList<>();
        try (var paths = Files.list(directory)) {
            for (Path path : paths
                .filter(candidate -> candidate.getFileName().toString().endsWith(".snapshot.json"))
                .toList()) {
                String fileName = path.getFileName().toString();
                String placementId = fileName.substring(0, fileName.length() - ".snapshot.json".length());
                try {
                    history.add(load(ownerId, UUID.fromString(placementId)));
                } catch (IOException | IllegalArgumentException ignored) {
                    // One corrupt or incomplete historical entry must not hide valid recovery data.
                }
            }
        }
        history.sort(Comparator.comparing(
            (LoadedPlacement loaded) -> Instant.parse(loaded.snapshot().createdAt())
        ).reversed());
        return List.copyOf(history.stream()
            .limit(PlacementLimits.MAX_HISTORY_ENTRIES)
            .toList());
    }

    public synchronized LoadedPlacement update(
        PlacementSnapshot snapshot,
        PlacementState state
    ) throws IOException {
        VerifiedSnapshot verified = verifiedSnapshots.get(snapshot.placementId());
        if (verified == null || !verified.snapshot().equals(snapshot)) {
            throw new IOException("Placement snapshot must be loaded before updating it");
        }
        validateStateWithHash(snapshot, state, verified.hash());
        Path stateFile = ownerDirectory(snapshot.ownerId())
            .resolve(snapshot.placementId() + ".state.json");
        writeMutableJson(stateFile, state);
        return new LoadedPlacement(snapshot, state);
    }

    public synchronized LoadedPlacement markInterrupted(LoadedPlacement loaded) throws IOException {
        if (loaded == null || !PlacementStatusCodes.isBusy(loaded.state().status())) {
            return loaded;
        }
        PlacementState previous = loaded.state();
        return update(loaded.snapshot(), new PlacementState(
            PlacementLimits.SCHEMA_VERSION,
            previous.placementId(),
            previous.snapshotHash(),
            PlacementStatusCodes.INTERRUPTED,
            previous.processedChanges(),
            previous.revertedChanges(),
            previous.conflicts(),
            Instant.now().toString()
        ));
    }

    static void validateSnapshot(PlacementSnapshot snapshot) throws IOException {
        if (snapshot == null || snapshot.schemaVersion() != PlacementLimits.SCHEMA_VERSION) {
            throw new IOException("Invalid placement snapshot schema");
        }
        requireUuid(snapshot.placementId(), "placement id");
        requireUuid(snapshot.buildId(), "build id");
        requireUuid(snapshot.ownerId(), "owner id");
        requireUuid(snapshot.selectionId(), "selection id");
        if (snapshot.dimension() == null
            || snapshot.dimension().length() > PlacementLimits.MAX_DIMENSION_LENGTH
            || Identifier.tryParse(snapshot.dimension()) == null) {
            throw new IOException("Invalid placement dimension");
        }
        requireTimestamp(snapshot.createdAt());
        if (snapshot.changes() == null
            || snapshot.changes().isEmpty()
            || snapshot.changes().size() > PlacementLimits.MAX_CHANGES) {
            throw new IOException("Invalid placement change count");
        }

        Set<Coordinate> coordinates = new HashSet<>();
        int blockEntityChanges = 0;
        int totalBlockEntityBytes = 0;
        for (PlacementChange change : snapshot.changes()) {
            if (change == null
                || invalidState(change.beforeState())
                || invalidState(change.afterState())
                || change.beforeState().equals(change.afterState())
                || !coordinates.add(new Coordinate(change.x(), change.y(), change.z()))) {
                throw new IOException("Invalid placement change");
            }
            if (change.hasBlockEntityData()) {
                blockEntityChanges++;
                totalBlockEntityBytes += change.beforeBlockEntityNbt()
                    .getBytes(StandardCharsets.UTF_8).length;
                validateBlockEntityNbt(change.beforeBlockEntityNbt());
            }
        }
        if (blockEntityChanges > PlacementLimits.MAX_BLOCK_ENTITY_CHANGES) {
            throw new IOException("Too many block-entity recovery entries");
        }
        if (totalBlockEntityBytes > PlacementLimits.MAX_TOTAL_BLOCK_ENTITY_NBT_BYTES) {
            throw new IOException("Block-entity recovery data is too large");
        }
    }

    static void validateState(PlacementSnapshot snapshot, PlacementState state) throws IOException {
        validateStateWithHash(snapshot, state, PlacementSnapshotHasher.sha256(snapshot));
    }

    private static void validateStateWithHash(
        PlacementSnapshot snapshot,
        PlacementState state,
        String expectedSnapshotHash
    ) throws IOException {
        if (state == null
            || state.schemaVersion() != PlacementLimits.SCHEMA_VERSION
            || !snapshot.placementId().equals(state.placementId())
            || state.snapshotHash() == null
            || !SHA_256.matcher(state.snapshotHash()).matches()
            || !state.snapshotHash().equals(expectedSnapshotHash)
            || !PlacementStatusCodes.isJournalState(state.status())
            || state.processedChanges() < 0
            || state.processedChanges() > snapshot.changes().size()
            || state.revertedChanges() < 0
            || state.revertedChanges() > snapshot.changes().size()
            || state.conflicts() < 0
            || state.conflicts() > snapshot.changes().size() * 2) {
            throw new IOException("Invalid placement state");
        }
        requireTimestamp(state.updatedAt());
    }

    private Path ownerDirectory(String ownerId) throws IOException {
        requireUuid(ownerId, "owner id");
        Path directory = root.resolve(ownerId).normalize();
        if (!directory.startsWith(root)) {
            throw new IOException("Invalid placement owner path");
        }
        Files.createDirectories(directory);
        return directory;
    }

    private <T> T readJson(Path path, Class<T> type) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Placement journal is not a regular file");
        }
        long size = Files.size(path);
        if (size <= 0 || size > PlacementLimits.MAX_JOURNAL_JSON_BYTES) {
            throw new IOException("Placement journal exceeds its size limit");
        }
        try {
            T value = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), type);
            if (value == null) {
                throw new IOException("Placement journal is empty");
            }
            return value;
        } catch (RuntimeException exception) {
            throw new IOException("Placement journal JSON is invalid", exception);
        }
    }

    private void writeImmutableJson(Path path, Object value) throws IOException {
        Files.createDirectories(path.getParent());
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileAlreadyExistsException(path.toString());
        }
        Path temporary = Files.createTempFile(path.getParent(), "." + path.getFileName() + ".", ".tmp");
        try {
            writeAndSync(temporary, GSON.toJson(value));
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("Atomic snapshot commits are unsupported", exception);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void writeMutableJson(Path path, Object value) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "." + path.getFileName() + ".", ".tmp");
        try {
            writeAndSync(temporary, GSON.toJson(value));
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void writeAndSync(Path path, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= 0 || bytes.length > PlacementLimits.MAX_JOURNAL_JSON_BYTES) {
            throw new IOException("Placement journal exceeds its size limit");
        }
        try (FileChannel channel = FileChannel.open(
            path,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING
        )) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private static boolean invalidState(String value) {
        return value == null
            || value.isBlank()
            || value.length() > BuildLimits.MAX_BLOCK_STATE_LENGTH;
    }

    private static void validateBlockEntityNbt(String value) throws IOException {
        if (value == null
            || value.isBlank()
            || value.getBytes(StandardCharsets.UTF_8).length > PlacementLimits.MAX_BLOCK_ENTITY_NBT_BYTES) {
            throw new IOException("Invalid block-entity recovery data");
        }
        try {
            CompoundTag tag = TagParser.parseCompoundFully(value);
            if (tag.sizeInBytes() > PlacementLimits.MAX_BLOCK_ENTITY_NBT_BYTES
                || !tag.toString().equals(value)) {
                throw new IOException("Non-canonical block-entity recovery data");
            }
        } catch (CommandSyntaxException | RuntimeException exception) {
            throw new IOException("Invalid block-entity recovery data", exception);
        }
    }

    private static void requireUuid(String value, String field) throws IOException {
        try {
            UUID parsed = UUID.fromString(value == null ? "" : value);
            if (!parsed.toString().equals(value)) {
                throw new IllegalArgumentException("Non-canonical UUID");
            }
        } catch (RuntimeException exception) {
            throw new IOException("Invalid " + field, exception);
        }
    }

    private static void requireTimestamp(String value) throws IOException {
        try {
            Instant.parse(value);
        } catch (RuntimeException exception) {
            throw new IOException("Invalid placement timestamp", exception);
        }
    }

    public record LoadedPlacement(PlacementSnapshot snapshot, PlacementState state) {
    }

    private record PlacementHead(int schemaVersion, String placementId) {
    }

    private record VerifiedSnapshot(PlacementSnapshot snapshot, String hash) {
    }

    private record Coordinate(int x, int y, int z) {
    }
}
