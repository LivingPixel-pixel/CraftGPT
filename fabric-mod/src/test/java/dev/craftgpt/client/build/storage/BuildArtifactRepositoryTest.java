package dev.craftgpt.client.build.storage;

import dev.craftgpt.build.model.BuildOperation;
import dev.craftgpt.build.model.CompiledBuildArtifact;
import dev.craftgpt.client.api.ApiCallMetrics;
import dev.craftgpt.client.api.ApiUsage;
import dev.craftgpt.client.planning.model.IntentionSpec;
import dev.craftgpt.client.planning.model.MaterialRole;
import dev.craftgpt.client.planning.model.PlanDimensions;
import dev.craftgpt.client.planning.model.PlanProjectManifest;
import dev.craftgpt.client.planning.model.PlanVersion;
import dev.craftgpt.client.planning.model.ProjectSnapshot;
import dev.craftgpt.client.planning.storage.PlanContentHasher;
import dev.craftgpt.context.AreaContext;
import dev.craftgpt.context.AreaContextHasher;
import dev.craftgpt.context.AreaPoint;
import dev.craftgpt.context.LayerSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BuildArtifactRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void saveAcceptReloadAndAbandonKeepImmutableArtifact() throws Exception {
        ProjectSnapshot source = source();
        AreaContext context = source.project().areaContext();
        CompiledBuildArtifact artifact = artifact(source, context);
        BuildArtifactRepository repository = new BuildArtifactRepository(temporaryDirectory);

        BuildArtifactSnapshot saved = repository.save(source, context, artifact, 2);
        Path artifactFile = temporaryDirectory.resolve(source.project().projectId())
            .resolve("builds")
            .resolve(artifact.buildId() + ".json");
        String immutableBefore = Files.readString(artifactFile);

        assertFalse(saved.accepted());
        assertEquals(2, saved.actualChanges());
        assertEquals(artifact, repository.load(source, context).orElseThrow().artifact());

        BuildArtifactSnapshot accepted = repository.accept(source, context);
        assertTrue(accepted.accepted());
        assertEquals(immutableBefore, Files.readString(artifactFile));

        BuildArtifactRepository reloaded = new BuildArtifactRepository(temporaryDirectory);
        assertTrue(reloaded.load(source, context).orElseThrow().accepted());
        reloaded.abandon(source);
        assertTrue(reloaded.load(source, context).isEmpty());
        assertEquals(immutableBefore, Files.readString(artifactFile));
    }

    @Test
    void tamperedArtifactIsNotLoaded() throws Exception {
        ProjectSnapshot source = source();
        AreaContext context = source.project().areaContext();
        CompiledBuildArtifact artifact = artifact(source, context);
        BuildArtifactRepository repository = new BuildArtifactRepository(temporaryDirectory);
        repository.save(source, context, artifact, 2);
        Path artifactFile = temporaryDirectory.resolve(source.project().projectId())
            .resolve("builds")
            .resolve(artifact.buildId() + ".json");
        Files.writeString(artifactFile, Files.readString(artifactFile).replace(
            "Validated forge preview",
            "Tampered preview"
        ));

        assertTrue(new BuildArtifactRepository(temporaryDirectory).load(source, context).isEmpty());
    }

    @Test
    void artifactMustMatchCurrentSelectionAndPlan() {
        ProjectSnapshot source = source();
        AreaContext context = source.project().areaContext();
        CompiledBuildArtifact artifact = artifact(source, context);
        CompiledBuildArtifact wrongSelection = new CompiledBuildArtifact(
            artifact.schemaVersion(), artifact.buildId(), UUID.randomUUID().toString(), artifact.projectId(),
            artifact.planVersionId(), artifact.planContentHash(), artifact.contextHash(), artifact.createdAt(),
            artifact.model(), artifact.reasoningLevel(), artifact.summary(), artifact.palette(), artifact.operations()
        );

        assertThrows(IllegalArgumentException.class, () ->
            new BuildArtifactRepository(temporaryDirectory).save(source, context, wrongSelection, 2));
    }

    @Test
    void usageSidecarSurvivesReloadWithoutChangingArtifact() throws Exception {
        ProjectSnapshot source = source();
        AreaContext context = source.project().areaContext();
        CompiledBuildArtifact artifact = artifact(source, context);
        ApiCallMetrics metrics = new ApiCallMetrics(
            artifact.model(),
            artifact.reasoningLevel(),
            new ApiUsage(true, 700, 0, 0, 250, 80, 950),
            3_000
        );
        BuildArtifactRepository repository = new BuildArtifactRepository(temporaryDirectory);

        repository.save(source, context, artifact, 2, metrics);
        Path artifactFile = temporaryDirectory.resolve(source.project().projectId())
            .resolve("builds")
            .resolve(artifact.buildId() + ".json");
        String immutableBefore = Files.readString(artifactFile);

        BuildArtifactRepository reloaded = new BuildArtifactRepository(temporaryDirectory);
        assertEquals(metrics, reloaded.loadMetrics(source, artifact.buildId()).orElseThrow());
        assertEquals(immutableBefore, Files.readString(artifactFile));
    }

    private ProjectSnapshot source() {
        AreaContext context = context();
        IntentionSpec intention = new IntentionSpec(
            "Forge", "Compact forge", List.of("Workshop"), "Medieval",
            new PlanDimensions(8, 7, 8), "South",
            List.of(new MaterialRole("walls", List.of("minecraft:stone"), "Shell")),
            List.of("Chimney"), List.of(), List.of("Stay in area"), List.of(), List.of(),
            500, "Fits the terrain", "Build a compact forge"
        );
        String now = Instant.now().toString();
        PlanVersion version = new PlanVersion(
            1, "v1", null, null, now, "PROMPT", "Build a forge", intention.summary(),
            AreaContextHasher.sha256(context), PlanContentHasher.sha256(intention),
            "planner-test", "medium", intention
        );
        String projectId = UUID.randomUUID().toString();
        PlanProjectManifest manifest = new PlanProjectManifest(
            1, projectId, "test-scope", "Forge", now, now, context, "v1", 2, List.of("v1")
        );
        return new ProjectSnapshot(manifest, version);
    }

    private CompiledBuildArtifact artifact(ProjectSnapshot source, AreaContext context) {
        return new CompiledBuildArtifact(
            1,
            UUID.randomUUID().toString(),
            context.selectionId(),
            source.project().projectId(),
            source.activeVersion().id(),
            source.activeVersion().contentHash(),
            source.activeVersion().contextHash(),
            Instant.now().toString(),
            "builder-test",
            "low",
            "Validated forge preview",
            List.of("minecraft:stone", "minecraft:air"),
            List.of(new BuildOperation(0, 0, 0, 0), new BuildOperation(1, 0, 0, 1))
        );
    }

    private AreaContext context() {
        return new AreaContext(
            1,
            "00000000-0000-0000-0000-000000000001",
            "minecraft:overworld",
            new AreaPoint(100, 64, 200),
            new AreaPoint(109, 73, 209),
            10, 10, 10, 1_000, 1_000, 500, 0,
            100, 64, 70,
            Map.of("minecraft:stone", 500),
            "a".repeat(64),
            IntStream.range(0, 10)
                .mapToObj(y -> new LayerSummary(y, 50, "minecraft:stone"))
                .toList(),
            List.of()
        );
    }
}
