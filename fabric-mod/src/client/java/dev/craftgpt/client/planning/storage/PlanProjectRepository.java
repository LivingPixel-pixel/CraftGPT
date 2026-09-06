package dev.craftgpt.client.planning.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.craftgpt.client.api.ApiCallMetrics;
import dev.craftgpt.CraftGptMod;
import dev.craftgpt.client.planning.model.IntentionSpec;
import dev.craftgpt.client.planning.model.PlanProjectManifest;
import dev.craftgpt.client.planning.model.PlanVersion;
import dev.craftgpt.client.planning.model.ProjectSnapshot;
import dev.craftgpt.context.AreaContext;
import dev.craftgpt.context.AreaContextHasher;
import dev.craftgpt.context.AreaContextValidator;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlanProjectRepository {
    private static final int SCHEMA_VERSION = 1;
    private static final Pattern VERSION_ID = Pattern.compile("v[1-9][0-9]*");
    private static final Pattern VERSION_FILE = Pattern.compile("(v[1-9][0-9]*)\\.json");
    private static final Pattern SHA_256 = Pattern.compile("[a-f0-9]{64}");
    private static final Set<String> VERSION_ACTIONS = Set.of("PROMPT", "DISCUSS", "REVERT");
    private static final long MAX_JSON_BYTES = 4L * 1024L * 1024L;
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    private final Path projectsDirectory;
    private final Path indexFile;
    private final String worldScopeId;
    private PlanProjectManifest activeProject;
    private PlanVersion activeVersion;

    public PlanProjectRepository(String worldScopeId) {
        this(
            FabricLoader.getInstance().getConfigDir().resolve("craftgpt").resolve("projects").resolve(validateScopeId(worldScopeId)),
            worldScopeId
        );
    }

    PlanProjectRepository(Path projectsDirectory) {
        this(projectsDirectory, "test-scope");
    }

    private PlanProjectRepository(Path projectsDirectory, String worldScopeId) {
        this.projectsDirectory = projectsDirectory.toAbsolutePath().normalize();
        this.indexFile = this.projectsDirectory.resolve("index.json");
        this.worldScopeId = worldScopeId;
        loadActiveProject();
    }

    public synchronized ProjectSnapshot create(
        AreaContext context,
        String instruction,
        IntentionSpec intention,
        String model,
        String reasoningLevel
    ) throws IOException {
        AreaContext validatedContext = AreaContextValidator.validate(context);
        String projectId = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        PlanVersion firstVersion = new PlanVersion(
            SCHEMA_VERSION,
            "v1",
            null,
            null,
            now,
            "PROMPT",
            instruction,
            intention.summary(),
            AreaContextHasher.sha256(validatedContext),
            PlanContentHasher.sha256(intention),
            model,
            reasoningLevel,
            intention
        );
        PlanProjectManifest manifest = new PlanProjectManifest(
            SCHEMA_VERSION,
            projectId,
            worldScopeId,
            safeProjectName(intention.title(), instruction),
            now,
            now,
            validatedContext,
            firstVersion.id(),
            2,
            List.of(firstVersion.id())
        );

        writeVersion(manifest, firstVersion);
        writeManifest(manifest);
        writeMutableJson(indexFile, new ActiveProjectIndex(projectId));
        activeProject = manifest;
        activeVersion = firstVersion;
        return new ProjectSnapshot(manifest, firstVersion);
    }

    public synchronized boolean saveApiMetrics(ProjectSnapshot snapshot, ApiCallMetrics metrics) {
        if (snapshot == null || metrics == null || !metrics.usage().reported()) {
            return false;
        }
        try {
            PlanProjectManifest project = requireActiveProject();
            PlanVersion version = snapshot.activeVersion();
            if (!project.projectId().equals(snapshot.project().projectId())
                || !project.versionIds().contains(version.id())
                || !version.model().equals(metrics.model())
                || !version.reasoningLevel().equals(metrics.reasoningLevel())) {
                throw new IOException("API metrics do not match their plan version");
            }
            Path path = projectDirectory(project.projectId())
                .resolve("versions")
                .resolve(version.id() + ".usage.json");
            writeImmutableJson(path, metrics);
            return true;
        } catch (IOException | RuntimeException exception) {
            CraftGptMod.LOGGER.warn("Could not store CraftGPT plan API usage", exception);
            return false;
        }
    }

    public synchronized Optional<ApiCallMetrics> apiMetrics(PlanVersion version) {
        if (version == null || activeProject == null) {
            return Optional.empty();
        }
        try {
            validateVersionId(version.id());
            Path path = projectDirectory(activeProject.projectId())
                .resolve("versions")
                .resolve(version.id() + ".usage.json");
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                return Optional.empty();
            }
            ApiCallMetrics metrics = readJson(path, ApiCallMetrics.class);
            if (metrics == null
                || !metrics.usage().reported()
                || !version.model().equals(metrics.model())
                || !version.reasoningLevel().equals(metrics.reasoningLevel())) {
                throw new IOException("Stored plan API metrics are invalid");
            }
            return Optional.of(metrics);
        } catch (IOException | RuntimeException exception) {
            CraftGptMod.LOGGER.warn("Could not load CraftGPT plan API usage", exception);
            return Optional.empty();
        }
    }

    public synchronized ProjectSnapshot iterate(
        AreaContext context,
        String instruction,
        IntentionSpec intention,
        String model,
        String reasoningLevel
    ) throws IOException {
        AreaContext validatedContext = AreaContextValidator.validate(context);
        PlanProjectManifest project = requireActiveProject();
        if (!project.areaContext().sameArea(validatedContext)) {
            throw new IllegalStateException("area_changed");
        }

        String id = "v" + project.nextVersionNumber();
        String now = Instant.now().toString();
        PlanVersion version = new PlanVersion(
            SCHEMA_VERSION,
            id,
            project.activeVersionId(),
            null,
            now,
            "DISCUSS",
            instruction,
            intention.summary(),
            AreaContextHasher.sha256(validatedContext),
            PlanContentHasher.sha256(intention),
            model,
            reasoningLevel,
            intention
        );
        return append(project, version, now, validatedContext);
    }

    public synchronized ProjectSnapshot revert(String targetVersionId, AreaContext context) throws IOException {
        AreaContext validatedContext = AreaContextValidator.validate(context);
        PlanProjectManifest project = requireActiveProject();
        if (!project.areaContext().sameArea(validatedContext)) {
            throw new IllegalStateException("area_changed");
        }
        validateVersionId(targetVersionId);
        if (!project.versionIds().contains(targetVersionId)) {
            throw new IllegalArgumentException("unknown_version");
        }

        PlanVersion target = readVersion(project, targetVersionId);
        String id = "v" + project.nextVersionNumber();
        String now = Instant.now().toString();
        PlanVersion version = new PlanVersion(
            SCHEMA_VERSION,
            id,
            project.activeVersionId(),
            targetVersionId,
            now,
            "REVERT",
            "Revert to " + targetVersionId,
            "Restored from " + targetVersionId,
            AreaContextHasher.sha256(validatedContext),
            PlanContentHasher.sha256(target.intention()),
            target.model(),
            target.reasoningLevel(),
            target.intention()
        );
        return append(project, version, now, validatedContext);
    }

    public synchronized Optional<ProjectSnapshot> active() {
        if (activeProject == null || activeVersion == null) {
            return Optional.empty();
        }
        return Optional.of(new ProjectSnapshot(activeProject, activeVersion));
    }

    public synchronized List<PlanVersion> versionsNewestFirst() {
        if (activeProject == null) {
            return List.of();
        }
        List<PlanVersion> versions = new ArrayList<>();
        for (String id : activeProject.versionIds()) {
            try {
                versions.add(readVersion(activeProject, id));
            } catch (IOException | RuntimeException exception) {
                CraftGptMod.LOGGER.error("Could not load CraftGPT plan version {}", id, exception);
            }
        }
        Collections.reverse(versions);
        return List.copyOf(versions);
    }

    /** Restores the head after a failed candidate without deleting its audit history. */
    public synchronized void restoreHead(ProjectSnapshot checkpoint) throws IOException {
        PlanProjectManifest p=requireActiveProject();
        if(checkpoint==null || !p.projectId().equals(checkpoint.project().projectId())
            || !p.versionIds().contains(checkpoint.activeVersion().id())) throw new IOException("invalid_checkpoint");
        PlanProjectManifest restored=new PlanProjectManifest(p.schemaVersion(),p.projectId(),p.worldScopeId(),
            p.name(),p.createdAt(),p.updatedAt(),checkpoint.project().areaContext(),checkpoint.activeVersion().id(),
            p.nextVersionNumber(),p.versionIds());
        writeManifest(restored); activeProject=restored; activeVersion=checkpoint.activeVersion();
    }

    private ProjectSnapshot append(
        PlanProjectManifest project,
        PlanVersion version,
        String updatedAt,
        AreaContext areaContext
    ) throws IOException {
        List<String> versionIds = new ArrayList<>(project.versionIds());
        versionIds.add(version.id());
        PlanProjectManifest updated = new PlanProjectManifest(
            project.schemaVersion(),
            project.projectId(),
            project.worldScopeId(),
            project.name(),
            project.createdAt(),
            updatedAt,
            areaContext,
            version.id(),
            project.nextVersionNumber() + 1,
            versionIds
        );

        // A version is immutable and lands before the mutable project head.
        writeVersion(updated, version);
        writeManifest(updated);
        activeProject = updated;
        activeVersion = version;
        return new ProjectSnapshot(updated, version);
    }

    private void loadActiveProject() {
        if (!Files.exists(indexFile)) {
            return;
        }
        try {
            ActiveProjectIndex index = readJson(indexFile, ActiveProjectIndex.class);
            if (index.activeProjectId() == null) {
                throw new IOException("Active project index has no project ID");
            }
            Path manifestFile = projectDirectory(index.activeProjectId()).resolve("project.json");
            PlanProjectManifest manifest = readJson(manifestFile, PlanProjectManifest.class);
            validateManifest(manifest, index.activeProjectId());
            if (!worldScopeId.equals(manifest.worldScopeId())) {
                throw new IOException("Project belongs to another world scope");
            }
            validateVersionChain(manifest);
            activeProject = recoverOrphanVersions(manifest);
            activeVersion = readVersion(activeProject, activeProject.activeVersionId());
        } catch (Exception exception) {
            activeProject = null;
            activeVersion = null;
            CraftGptMod.LOGGER.error("Could not load active CraftGPT project", exception);
        }
    }

    private PlanProjectManifest recoverOrphanVersions(PlanProjectManifest manifest) throws IOException {
        Path versionsDirectory = projectDirectory(manifest.projectId()).resolve("versions");
        if (!Files.isDirectory(versionsDirectory)) {
            return manifest;
        }

        Map<Integer, Path> versionsOnDisk = new TreeMap<>();
        try (var files = Files.list(versionsDirectory)) {
            files.forEach(path -> addVersionFile(versionsOnDisk, path));
        }

        if (versionsOnDisk.isEmpty()) {
            return manifest;
        }

        List<String> recoveredIds = new ArrayList<>(manifest.versionIds());
        String recoveredActiveId = manifest.activeVersionId();
        String recoveredUpdatedAt = manifest.updatedAt();
        int nextNumber = manifest.nextVersionNumber();

        while (versionsOnDisk.containsKey(nextNumber)) {
            String candidateId = "v" + nextNumber;
            try {
                PlanVersion candidate = readVersion(manifest, candidateId);
                if (!Objects.equals(recoveredActiveId, candidate.parentVersionId())) {
                    CraftGptMod.LOGGER.warn(
                        "Ignoring orphan CraftGPT version {} because its parent does not match the active version",
                        candidateId
                    );
                    break;
                }
                if (candidate.sourceVersionId() != null && !recoveredIds.contains(candidate.sourceVersionId())) {
                    CraftGptMod.LOGGER.warn(
                        "Ignoring orphan CraftGPT version {} because its source version is not in the project history",
                        candidateId
                    );
                    break;
                }
                recoveredIds.add(candidateId);
                recoveredActiveId = candidateId;
                recoveredUpdatedAt = candidate.createdAt();
                if (nextNumber == Integer.MAX_VALUE) {
                    throw new IOException("CraftGPT version number limit reached");
                }
                nextNumber++;
            } catch (IOException | RuntimeException exception) {
                CraftGptMod.LOGGER.warn("Ignoring invalid orphan CraftGPT version {}", candidateId, exception);
                break;
            }
        }

        // Never reuse a filename already present on disk, even when that file is malformed
        // or is not part of the recoverable parent-linked chain.
        int maximumOnDisk = versionsOnDisk.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (maximumOnDisk >= nextNumber) {
            if (maximumOnDisk == Integer.MAX_VALUE) {
                throw new IOException("CraftGPT version number limit reached");
            }
            nextNumber = maximumOnDisk + 1;
        }

        if (recoveredActiveId.equals(manifest.activeVersionId())
            && nextNumber == manifest.nextVersionNumber()) {
            return manifest;
        }

        PlanProjectManifest recovered = new PlanProjectManifest(
            manifest.schemaVersion(),
            manifest.projectId(),
            manifest.worldScopeId(),
            manifest.name(),
            manifest.createdAt(),
            recoveredUpdatedAt,
            manifest.areaContext(),
            recoveredActiveId,
            nextNumber,
            recoveredIds
        );
        writeManifest(recovered);
        return recovered;
    }

    private void addVersionFile(Map<Integer, Path> versionsOnDisk, Path path) {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Matcher matcher = VERSION_FILE.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            return;
        }
        try {
            int number = Integer.parseInt(matcher.group(1).substring(1));
            versionsOnDisk.put(number, path);
        } catch (NumberFormatException exception) {
            CraftGptMod.LOGGER.warn("Ignoring CraftGPT version file with an unsupported version number");
        }
    }

    private PlanProjectManifest requireActiveProject() {
        if (activeProject == null) {
            throw new IllegalStateException("no_active_project");
        }
        return activeProject;
    }

    private void writeManifest(PlanProjectManifest manifest) throws IOException {
        writeMutableJson(projectDirectory(manifest.projectId()).resolve("project.json"), manifest);
    }

    private void writeVersion(PlanProjectManifest manifest, PlanVersion version) throws IOException {
        validateVersionId(version.id());
        validateVersion(version, version.id());
        writeImmutableJson(
            projectDirectory(manifest.projectId()).resolve("versions").resolve(version.id() + ".json"),
            version
        );
    }

    private PlanVersion readVersion(PlanProjectManifest manifest, String versionId) throws IOException {
        validateVersionId(versionId);
        Path path = projectDirectory(manifest.projectId()).resolve("versions").resolve(versionId + ".json");
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Plan version is missing or is not a regular file: " + versionId);
        }
        PlanVersion version = readJson(path, PlanVersion.class);
        validateVersion(version, versionId);
        return version;
    }

    private void validateManifest(PlanProjectManifest manifest, String expectedProjectId) throws IOException {
        if (manifest.schemaVersion() != SCHEMA_VERSION) {
            throw new IOException("Unsupported CraftGPT project schema");
        }
        if (!expectedProjectId.equals(manifest.projectId())) {
            throw new IOException("CraftGPT project ID does not match its index");
        }
        projectDirectory(manifest.projectId());
        if (manifest.worldScopeId() == null || manifest.worldScopeId().isBlank()) {
            throw new IOException("CraftGPT project has no world scope");
        }
        if (manifest.areaContext() == null) {
            throw new IOException("CraftGPT project has no area context");
        }
        try {
            AreaContextValidator.validate(manifest.areaContext());
        } catch (IllegalArgumentException exception) {
            throw new IOException("CraftGPT project has an invalid area context", exception);
        }
        if (manifest.versionIds().isEmpty()) {
            throw new IOException("CraftGPT project has no plan versions");
        }
        if (!"v1".equals(manifest.versionIds().getFirst())) {
            throw new IOException("CraftGPT project history does not begin at v1");
        }

        Set<String> uniqueIds = new HashSet<>();
        int previousNumber = 0;
        for (String versionId : manifest.versionIds()) {
            try {
                validateVersionId(versionId);
            } catch (IllegalArgumentException exception) {
                throw new IOException("CraftGPT project contains an invalid version ID", exception);
            }
            if (!uniqueIds.add(versionId)) {
                throw new IOException("CraftGPT project contains duplicate version IDs");
            }
            int number = versionNumber(versionId);
            if (number <= previousNumber) {
                throw new IOException("CraftGPT project versions are not ordered");
            }
            previousNumber = number;
        }
        if (!manifest.versionIds().contains(manifest.activeVersionId())) {
            throw new IOException("CraftGPT active version is not in the project history");
        }
        if (manifest.nextVersionNumber() <= previousNumber) {
            throw new IOException("CraftGPT next version number is invalid");
        }
    }

    private void validateVersionChain(PlanProjectManifest manifest) throws IOException {
        Set<String> seen = new HashSet<>();
        for (String versionId : manifest.versionIds()) {
            PlanVersion version = readVersion(manifest, versionId);
            if (seen.isEmpty() ? version.parentVersionId() != null
                : version.parentVersionId() == null || !seen.contains(version.parentVersionId())) {
                throw new IOException("CraftGPT version history has a broken parent chain at " + versionId);
            }
            if (version.sourceVersionId() != null && !seen.contains(version.sourceVersionId())) {
                throw new IOException("CraftGPT version history has an invalid source at " + versionId);
            }
            seen.add(versionId);
        }
    }

    private void validateVersion(PlanVersion version, String expectedId) throws IOException {
        if (version.schemaVersion() != SCHEMA_VERSION) {
            throw new IOException("Unsupported CraftGPT plan version schema: " + expectedId);
        }
        if (!expectedId.equals(version.id())) {
            throw new IOException("CraftGPT plan version ID does not match its filename: " + expectedId);
        }
        validateOptionalVersionId(version.parentVersionId(), "parent", expectedId);
        validateOptionalVersionId(version.sourceVersionId(), "source", expectedId);
        if (version.createdAt() == null || version.action() == null || version.instruction() == null
            || version.changeSummary() == null || version.contextHash() == null
            || version.model() == null || version.reasoningLevel() == null || version.intention() == null) {
            throw new IOException("CraftGPT plan version has missing fields: " + expectedId);
        }
        if (!VERSION_ACTIONS.contains(version.action())) {
            throw new IOException("CraftGPT plan version has an invalid action: " + expectedId);
        }
        try {
            Instant.parse(version.createdAt());
        } catch (RuntimeException exception) {
            throw new IOException("CraftGPT plan version has an invalid timestamp: " + expectedId, exception);
        }
        if (!SHA_256.matcher(version.contextHash()).matches()) {
            throw new IOException("CraftGPT plan version has an invalid context hash: " + expectedId);
        }
        if (version.contentHash() == null || !SHA_256.matcher(version.contentHash()).matches()) {
            throw new IOException("CraftGPT plan version has an invalid content hash: " + expectedId);
        }
        String actualHash;
        try {
            actualHash = PlanContentHasher.sha256(version.intention());
        } catch (RuntimeException exception) {
            throw new IOException("CraftGPT plan version contains invalid intention data: " + expectedId, exception);
        }
        if (!actualHash.equals(version.contentHash())) {
            throw new IOException("CraftGPT plan version content hash mismatch: " + expectedId);
        }
    }

    private void validateOptionalVersionId(String versionId, String field, String ownerId) throws IOException {
        if (versionId == null) {
            return;
        }
        try {
            validateVersionId(versionId);
        } catch (IllegalArgumentException exception) {
            throw new IOException("CraftGPT plan version has an invalid " + field + " ID: " + ownerId, exception);
        }
    }

    private int versionNumber(String versionId) throws IOException {
        try {
            return Integer.parseInt(versionId.substring(1));
        } catch (NumberFormatException exception) {
            throw new IOException("CraftGPT version number is too large", exception);
        }
    }

    private Path projectDirectory(String projectId) {
        UUID.fromString(projectId);
        Path directory = projectsDirectory.resolve(projectId).normalize();
        if (!directory.startsWith(projectsDirectory)) {
            throw new IllegalArgumentException("Invalid project path");
        }
        return directory;
    }

    private void validateVersionId(String versionId) {
        if (versionId == null || !VERSION_ID.matcher(versionId).matches()) {
            throw new IllegalArgumentException("invalid_version");
        }
    }

    private <T> T readJson(Path path, Class<T> type) throws IOException {
        long size = Files.size(path);
        if (size <= 0 || size > MAX_JSON_BYTES) {
            throw new IOException("CraftGPT JSON file has an invalid size: " + path.getFileName());
        }
        T result = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), type);
        if (result == null) {
            throw new IOException("Empty JSON file: " + path.getFileName());
        }
        return result;
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
                // Deliberately omit REPLACE_EXISTING: committed plan versions are immutable.
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("Atomic plan-version commits are not supported by this filesystem", exception);
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

    private static String safeProjectName(String title, String fallback) {
        String candidate = title == null || title.isBlank() ? fallback : title;
        String normalized = candidate.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 61) + "...";
    }

    private record ActiveProjectIndex(String activeProjectId) {
    }

    private static String validateScopeId(String scopeId) {
        if (scopeId == null || !scopeId.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("Invalid world scope ID");
        }
        return scopeId;
    }
}
