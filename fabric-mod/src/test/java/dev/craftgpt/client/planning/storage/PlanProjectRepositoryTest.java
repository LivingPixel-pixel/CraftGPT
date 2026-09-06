package dev.craftgpt.client.planning.storage;

import dev.craftgpt.client.api.ApiCallMetrics;
import dev.craftgpt.client.api.ApiUsage;
import dev.craftgpt.client.planning.model.IntentionSpec;
import dev.craftgpt.client.planning.model.MaterialRole;
import dev.craftgpt.client.planning.model.PlanDimensions;
import dev.craftgpt.client.planning.model.ProjectSnapshot;
import dev.craftgpt.context.AreaContext;
import dev.craftgpt.context.AreaPoint;
import dev.craftgpt.context.LayerSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlanProjectRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void appendReloadAndRevertPreserveImmutableHistory() throws Exception {
        PlanProjectRepository repository = new PlanProjectRepository(temporaryDirectory);
        ProjectSnapshot first = repository.create(context(), "Build a forge", plan("Forge v1"), "test-model", "medium");
        Path firstVersionFile = temporaryDirectory.resolve(first.project().projectId()).resolve("versions/v1.json");
        String firstVersionBefore = Files.readString(firstVersionFile);

        ProjectSnapshot second = repository.iterate(context(), "Raise the roof", plan("Forge v2"), "test-model", "high");
        ProjectSnapshot reverted = repository.revert("v1", context());

        assertEquals("v2", second.activeVersion().id());
        assertEquals("v3", reverted.activeVersion().id());
        assertEquals("v1", reverted.activeVersion().sourceVersionId());
        assertEquals(first.activeVersion().intention(), reverted.activeVersion().intention());
        assertEquals(PlanContentHasher.sha256(first.activeVersion().intention()), first.activeVersion().contentHash());
        assertEquals(first.activeVersion().contentHash(), reverted.activeVersion().contentHash());
        assertEquals(firstVersionBefore, Files.readString(firstVersionFile));
        assertNotEquals(second.activeVersion().intention(), reverted.activeVersion().intention());

        PlanProjectRepository reloaded = new PlanProjectRepository(temporaryDirectory);
        assertEquals("v3", reloaded.active().orElseThrow().activeVersion().id());
        assertEquals(List.of("v3", "v2", "v1"),
            reloaded.versionsNewestFirst().stream().map(version -> version.id()).toList());
        assertTrue(Files.exists(temporaryDirectory.resolve(first.project().projectId()).resolve("project.json")));
    }

    @Test
    void reloadRecoversACommittedParentLinkedOrphan() throws Exception {
        PlanProjectRepository repository = new PlanProjectRepository(temporaryDirectory);
        ProjectSnapshot first = repository.create(context(), "Build a forge", plan("Forge v1"), "test-model", "medium");
        Path manifestFile = temporaryDirectory.resolve(first.project().projectId()).resolve("project.json");
        String manifestBeforeIteration = Files.readString(manifestFile);

        repository.iterate(context(), "Raise the roof", plan("Forge v2"), "test-model", "high");
        repository.iterate(context(), "Add a chimney", plan("Forge v3"), "test-model", "medium");
        Files.writeString(manifestFile, manifestBeforeIteration);

        PlanProjectRepository recovered = new PlanProjectRepository(temporaryDirectory);
        assertEquals("v3", recovered.active().orElseThrow().activeVersion().id());
        assertEquals(List.of("v3", "v2", "v1"),
            recovered.versionsNewestFirst().stream().map(version -> version.id()).toList());
    }

    @Test
    void malformedOrphanIsIgnoredAndItsFilenameIsNeverReused() throws Exception {
        PlanProjectRepository repository = new PlanProjectRepository(temporaryDirectory);
        ProjectSnapshot first = repository.create(context(), "Build a forge", plan("Forge v1"), "test-model", "medium");
        Path versionsDirectory = temporaryDirectory.resolve(first.project().projectId()).resolve("versions");
        Files.writeString(versionsDirectory.resolve("v2.json"), "{not valid json");

        PlanProjectRepository recovered = new PlanProjectRepository(temporaryDirectory);
        assertEquals("v1", recovered.active().orElseThrow().activeVersion().id());
        assertEquals(List.of("v1"),
            recovered.versionsNewestFirst().stream().map(version -> version.id()).toList());

        ProjectSnapshot next = recovered.iterate(
            context(), "Add a chimney", plan("Forge v3"), "test-model", "low"
        );
        assertEquals("v3", next.activeVersion().id());
        assertEquals("{not valid json", Files.readString(versionsDirectory.resolve("v2.json")));
    }

    @Test
    void contentHashMismatchPreventsLoadingTamperedHistory() throws Exception {
        PlanProjectRepository repository = new PlanProjectRepository(temporaryDirectory);
        ProjectSnapshot first = repository.create(context(), "Build a forge", plan("Forge v1"), "test-model", "medium");
        Path versionFile = temporaryDirectory.resolve(first.project().projectId()).resolve("versions/v1.json");
        String tampered = Files.readString(versionFile)
            .replace("\"title\": \"Forge v1\"", "\"title\": \"Tampered\"");
        Files.writeString(versionFile, tampered);

        PlanProjectRepository reloaded = new PlanProjectRepository(temporaryDirectory);
        assertFalse(reloaded.active().isPresent());
    }

    @Test
    void usageSidecarSurvivesReloadWithoutChangingImmutablePlan() throws Exception {
        PlanProjectRepository repository = new PlanProjectRepository(temporaryDirectory);
        ProjectSnapshot first = repository.create(
            context(), "Build a forge", plan("Forge v1"), "test-model", "medium"
        );
        Path versionFile = temporaryDirectory.resolve(first.project().projectId()).resolve("versions/v1.json");
        String immutableBefore = Files.readString(versionFile);
        ApiCallMetrics metrics = new ApiCallMetrics(
            "test-model",
            "medium",
            new ApiUsage(true, 900, 100, 0, 300, 120, 1_200),
            2_500
        );

        assertTrue(repository.saveApiMetrics(first, metrics));
        assertEquals(metrics, repository.apiMetrics(first.activeVersion()).orElseThrow());
        assertEquals(immutableBefore, Files.readString(versionFile));

        PlanProjectRepository reloaded = new PlanProjectRepository(temporaryDirectory);
        assertEquals(
            metrics,
            reloaded.apiMetrics(reloaded.active().orElseThrow().activeVersion()).orElseThrow()
        );
    }

    @Test
    void failedCandidateCanRestoreCheckpointAndBranchWithoutLosingHistory() throws Exception {
        var repository = new PlanProjectRepository(temporaryDirectory);
        var checkpoint = repository.create(context(), "First", plan("Valid"), "model", "low");
        repository.iterate(context(), "Bad candidate", plan("Rejected"), "model", "low");
        repository.restoreHead(checkpoint);
        var reloaded = new PlanProjectRepository(temporaryDirectory);
        assertEquals("v1", reloaded.active().orElseThrow().activeVersion().id());
        assertEquals(2, reloaded.versionsNewestFirst().size());
        var retry = reloaded.iterate(context(), "Retry", plan("Improved"), "model", "low");
        assertEquals("v3", retry.activeVersion().id());
        assertEquals("v1", retry.activeVersion().parentVersionId());
        var finalReload = new PlanProjectRepository(temporaryDirectory);
        assertEquals("v3", finalReload.active().orElseThrow().activeVersion().id());
        assertEquals(List.of("v3", "v2", "v1"), finalReload.versionsNewestFirst().stream().map(v -> v.id()).toList());
    }

    private AreaContext context() {
        return new AreaContext(
            1, "00000000-0000-0000-0000-000000000001", "minecraft:overworld",
            new AreaPoint(0, 64, 0), new AreaPoint(9, 73, 9),
            10, 10, 10, 1_000, 1_000, 500, 0,
            100, 64, 70, Map.of("minecraft:stone", 500),
            "a".repeat(64),
            IntStream.range(0, 10)
                .mapToObj(y -> new LayerSummary(y, 50, "minecraft:stone"))
                .toList(),
            List.of()
        );
    }

    private IntentionSpec plan(String title) {
        return new IntentionSpec(
            title, title + " summary", List.of("Goal"), "Medieval",
            new PlanDimensions(8, 7, 8), "South",
            List.of(new MaterialRole("walls", List.of("minecraft:stone"), "Shell")),
            List.of("Chimney"), List.of(), List.of("Stay in area"), List.of(), List.of(),
            500, "Rationale", "Complete implementation brief"
        );
    }
}
