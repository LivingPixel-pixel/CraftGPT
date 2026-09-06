package dev.craftgpt.client.build.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.craftgpt.CraftGptMod;
import dev.craftgpt.build.BuildLimits;
import dev.craftgpt.build.model.BuildOperation;
import dev.craftgpt.build.model.CompiledBuildArtifact;
import dev.craftgpt.client.api.ApiCallMetrics;
import dev.craftgpt.client.planning.model.ProjectSnapshot;
import dev.craftgpt.context.AreaContext;
import dev.craftgpt.context.AreaContextHasher;
import net.fabricmc.loader.api.FabricLoader;

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
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class BuildArtifactRepository {
    private static final int STORAGE_SCHEMA_VERSION = 1;
    private static final Pattern SHA_256 = Pattern.compile("[a-f0-9]{64}");
    private static final Pattern PLAN_VERSION = Pattern.compile("v[1-9][0-9]*");
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    private final Path projectsDirectory;

    public BuildArtifactRepository(String worldScopeId) {
        this(FabricLoader.getInstance().getConfigDir()
            .resolve("craftgpt")
            .resolve("projects")
            .resolve(validateScopeId(worldScopeId)));
    }

    BuildArtifactRepository(Path projectsDirectory) {
        this.projectsDirectory = projectsDirectory.toAbsolutePath().normalize();
    }

    public synchronized BuildArtifactSnapshot save(
        ProjectSnapshot source,
        AreaContext context,
        CompiledBuildArtifact artifact,
        int actualChanges
    ) throws IOException {
        return save(source, context, artifact, actualChanges, null);
    }

    public synchronized BuildArtifactSnapshot save(
        ProjectSnapshot source,
        AreaContext context,
        CompiledBuildArtifact artifact,
        int actualChanges,
        ApiCallMetrics metrics
    ) throws IOException {
        validateAgainstSource(source, context, artifact);
        if (actualChanges <= 0 || actualChanges > artifact.operations().size()) {
            throw new IllegalArgumentException("Invalid actual build-change count");
        }
        Path artifactFile = buildsDirectory(source.project().projectId())
            .resolve(artifact.buildId() + ".json");
        StoredBuild stored = new StoredBuild(
            STORAGE_SCHEMA_VERSION,
            BuildArtifactHasher.sha256(artifact),
            actualChanges,
            artifact
        );
        writeImmutableJson(artifactFile, stored);
        storeMetrics(source, artifact, metrics);
        writeHead(source.project().projectId(), new BuildHead(STORAGE_SCHEMA_VERSION, artifact.buildId(), false));
        return new BuildArtifactSnapshot(artifact, false, actualChanges);
    }

    public synchronized Optional<BuildArtifactSnapshot> load(ProjectSnapshot source, AreaContext context) {
        try {
            BuildHead head = readHead(source.project().projectId());
            if (head == null || head.activeBuildId() == null) {
                return Optional.empty();
            }
            UUID.fromString(head.activeBuildId());
            StoredBuild stored = readJson(
                buildsDirectory(source.project().projectId()).resolve(head.activeBuildId() + ".json"),
                StoredBuild.class
            );
            validateStored(source, context, head, stored);
            return Optional.of(new BuildArtifactSnapshot(
                stored.artifact(),
                head.accepted(),
                stored.actualChanges()
            ));
        } catch (IOException | RuntimeException exception) {
            CraftGptMod.LOGGER.error("Could not load active CraftGPT build artifact", exception);
            return Optional.empty();
        }
    }

    public synchronized Optional<ApiCallMetrics> loadMetrics(ProjectSnapshot source, String buildId) {
        if (source == null || buildId == null) {
            return Optional.empty();
        }
        try {
            UUID.fromString(buildId);
            Path path = buildsDirectory(source.project().projectId()).resolve(buildId + ".usage.json");
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                return Optional.empty();
            }
            ApiCallMetrics metrics = readJson(path, ApiCallMetrics.class);
            if (metrics == null || !metrics.usage().reported()) {
                throw new IOException("Stored build API metrics are invalid");
            }
            return Optional.of(metrics);
        } catch (IOException | RuntimeException exception) {
            CraftGptMod.LOGGER.warn("Could not load CraftGPT build API usage", exception);
            return Optional.empty();
        }
    }

    public synchronized BuildArtifactSnapshot accept(ProjectSnapshot source, AreaContext context) throws IOException {
        BuildArtifactSnapshot current = load(source, context)
            .orElseThrow(() -> new IOException("No active build artifact"));
        writeHead(source.project().projectId(), new BuildHead(
            STORAGE_SCHEMA_VERSION,
            current.artifact().buildId(),
            true
        ));
        return new BuildArtifactSnapshot(current.artifact(), true, current.actualChanges());
    }

    public synchronized void abandon(ProjectSnapshot source) throws IOException {
        writeHead(source.project().projectId(), new BuildHead(STORAGE_SCHEMA_VERSION, null, false));
    }

    private void validateStored(
        ProjectSnapshot source,
        AreaContext context,
        BuildHead head,
        StoredBuild stored
    ) throws IOException {
        if (head.schemaVersion() != STORAGE_SCHEMA_VERSION
            || stored.schemaVersion() != STORAGE_SCHEMA_VERSION
            || stored.artifact() == null
            || stored.actualChanges() <= 0
            || stored.actualChanges() > stored.artifact().operations().size()
            || stored.contentHash() == null
            || !SHA_256.matcher(stored.contentHash()).matches()) {
            throw new IOException("Invalid build storage schema");
        }
        if (!head.activeBuildId().equals(stored.artifact().buildId())) {
            throw new IOException("Build head does not match artifact");
        }
        if (!stored.contentHash().equals(BuildArtifactHasher.sha256(stored.artifact()))) {
            throw new IOException("Build artifact content hash mismatch");
        }
        try {
            validateAgainstSource(source, context, stored.artifact());
        } catch (IllegalArgumentException exception) {
            throw new IOException("Stored build artifact is invalid", exception);
        }
    }

    private void storeMetrics(
        ProjectSnapshot source,
        CompiledBuildArtifact artifact,
        ApiCallMetrics metrics
    ) {
        if (metrics == null || !metrics.usage().reported()) {
            return;
        }
        if (!artifact.model().equals(metrics.model())
            || !artifact.reasoningLevel().equals(metrics.reasoningLevel())) {
            CraftGptMod.LOGGER.warn("Ignoring CraftGPT build API usage that does not match its artifact");
            return;
        }
        try {
            Path path = buildsDirectory(source.project().projectId())
                .resolve(artifact.buildId() + ".usage.json");
            writeImmutableJson(path, metrics);
        } catch (IOException | RuntimeException exception) {
            CraftGptMod.LOGGER.warn("Could not store CraftGPT build API usage", exception);
        }
    }

    private void validateAgainstSource(
        ProjectSnapshot source,
        AreaContext context,
        CompiledBuildArtifact artifact
    ) {
        if (source == null || context == null || artifact == null
            || artifact.schemaVersion() != BuildLimits.SCHEMA_VERSION) {
            throw new IllegalArgumentException("Invalid build artifact schema");
        }
        requireUuid(artifact.buildId(), "build id");
        requireUuid(artifact.selectionId(), "selection id");
        requireUuid(artifact.projectId(), "project id");
        if (!source.project().projectId().equals(artifact.projectId())
            || !source.activeVersion().id().equals(artifact.planVersionId())
            || !source.activeVersion().contentHash().equals(artifact.planContentHash())
            || !source.activeVersion().contextHash().equals(artifact.contextHash())
            || !source.project().areaContext().sameArea(context)
            || !context.selectionId().equals(artifact.selectionId())
            || !AreaContextHasher.sha256(context).equals(artifact.contextHash())) {
            throw new IllegalArgumentException("Build artifact does not match its source plan");
        }
        if (!PLAN_VERSION.matcher(artifact.planVersionId()).matches()
            || !SHA_256.matcher(artifact.planContentHash()).matches()
            || !SHA_256.matcher(artifact.contextHash()).matches()) {
            throw new IllegalArgumentException("Invalid build artifact identity");
        }
        try {
            Instant.parse(artifact.createdAt());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid build timestamp", exception);
        }
        if (artifact.model() == null || artifact.model().isBlank()
            || artifact.reasoningLevel() == null || artifact.reasoningLevel().isBlank()
            || artifact.summary() == null || artifact.summary().isBlank()
            || artifact.summary().length() > BuildLimits.MAX_SUMMARY_LENGTH
            || artifact.palette() == null || artifact.palette().isEmpty()
            || artifact.palette().size() > BuildLimits.MAX_PALETTE_ENTRIES
            || artifact.operations() == null || artifact.operations().isEmpty()
            || artifact.operations().size() > BuildLimits.HARD_MAX_OPERATIONS) {
            throw new IllegalArgumentException("Invalid build artifact content");
        }

        for (String state : artifact.palette()) {
            if (state == null || state.isBlank() || state.length() > BuildLimits.MAX_BLOCK_STATE_LENGTH
                || BuildLimits.isDangerousState(state)) {
                throw new IllegalArgumentException("Invalid build palette state");
            }
        }

        Set<Long> coordinates = new HashSet<>();
        int width = context.width();
        int height = context.height();
        int depth = context.depth();
        for (BuildOperation operation : artifact.operations()) {
            if (operation == null
                || operation.relativeX() < 0 || operation.relativeX() >= width
                || operation.relativeY() < 0 || operation.relativeY() >= height
                || operation.relativeZ() < 0 || operation.relativeZ() >= depth
                || operation.paletteIndex() < 0 || operation.paletteIndex() >= artifact.palette().size()) {
                throw new IllegalArgumentException("Build operation is outside the source area");
            }
            long key = ((long) operation.relativeX() << 42)
                ^ ((long) operation.relativeY() << 21)
                ^ operation.relativeZ();
            if (!coordinates.add(key)) {
                throw new IllegalArgumentException("Build artifact has duplicate coordinates");
            }
        }
    }

    private BuildHead readHead(String projectId) throws IOException {
        Path headFile = projectDirectory(projectId).resolve("build-head.json");
        return Files.exists(headFile) ? readJson(headFile, BuildHead.class) : null;
    }

    private void writeHead(String projectId, BuildHead head) throws IOException {
        writeMutableJson(projectDirectory(projectId).resolve("build-head.json"), head);
    }

    private Path buildsDirectory(String projectId) throws IOException {
        Path directory = projectDirectory(projectId).resolve("builds");
        Files.createDirectories(directory);
        return directory;
    }

    private Path projectDirectory(String projectId) {
        UUID.fromString(projectId);
        Path directory = projectsDirectory.resolve(projectId).normalize();
        if (!directory.startsWith(projectsDirectory)) {
            throw new IllegalArgumentException("Invalid project path");
        }
        return directory;
    }

    private <T> T readJson(Path path, Class<T> type) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Build JSON is not a regular file");
        }
        long size = Files.size(path);
        if (size <= 0 || size > BuildLimits.MAX_ARTIFACT_JSON_BYTES) {
            throw new IOException("Build JSON exceeds its size limit");
        }
        T value = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), type);
        if (value == null) {
            throw new IOException("Build JSON is empty");
        }
        return value;
    }

    private void writeImmutableJson(Path path, Object value) throws IOException {
        Files.createDirectories(path.getParent());
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileAlreadyExistsException(path.toString());
        }
        Path temporary = Files.createTempFile(path.getParent(), "." + path.getFileName() + ".", ".tmp");
        try {
            writeAndSync(temporary, GSON.toJson(value));
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new FileAlreadyExistsException(path.toString());
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("Atomic build commits are unsupported", exception);
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
        if (bytes.length > BuildLimits.MAX_ARTIFACT_JSON_BYTES) {
            throw new IOException("Build artifact exceeds its size limit");
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

    private static void requireUuid(String value, String field) {
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equalsIgnoreCase(value)) {
                throw new IllegalArgumentException("Invalid " + field);
            }
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid " + field, exception);
        }
    }

    private static String validateScopeId(String scopeId) {
        if (scopeId == null || !scopeId.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("Invalid world scope ID");
        }
        return scopeId;
    }

    private record StoredBuild(
        int schemaVersion,
        String contentHash,
        int actualChanges,
        CompiledBuildArtifact artifact
    ) {
    }

    private record BuildHead(int schemaVersion, String activeBuildId, boolean accepted) {
    }
}
