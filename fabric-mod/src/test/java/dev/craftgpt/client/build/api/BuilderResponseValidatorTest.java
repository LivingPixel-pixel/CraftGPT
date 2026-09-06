package dev.craftgpt.client.build.api;

import dev.craftgpt.build.model.BuildDraft;
import dev.craftgpt.build.model.CompiledBuildArtifact;
import dev.craftgpt.client.planning.model.PlanVersion;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BuilderResponseValidatorTest {
    @Test
    void reportsAllIndependentPaletteAndOperationProblemsTogether() {
        BuildDraft draft = new BuildDraft(
            1,
            "Broken build",
            List.of(
                "minecraft:command_block",
                "stone",
                "minecraft:stone",
                "minecraft:stone"
            ),
            List.of(
                "99,0,0,9",
                "0,0,0,0",
                "0,0,0,0",
                "01,0,0,0"
            )
        );

        BuilderException failure = assertThrows(
            BuilderException.class,
            () -> BuilderResponseValidator.validateAndCompile(
                draft,
                BuilderTestFixtures.context(),
                BuilderTestFixtures.PROJECT_ID,
                BuilderTestFixtures.planVersion(),
                BuilderTestFixtures.settings(20)
            )
        );

        assertTrue(failure.problems().size() >= 7);
        assertTrue(failure.problems().stream().anyMatch(problem ->
            problem.code().equals("dangerous_block_state")
                && problem.location().equals("build.palette[0]")));
        assertTrue(failure.problems().stream().anyMatch(problem ->
            problem.code().equals("invalid_block_state")
                && problem.location().equals("build.palette[1]")));
        assertTrue(failure.problems().stream().anyMatch(problem ->
            problem.code().equals("duplicate_palette_state")
                && problem.location().equals("build.palette[3]")));
        assertTrue(failure.problems().stream().anyMatch(problem ->
            problem.code().equals("build_operation_out_of_bounds")));
        assertTrue(failure.problems().stream().anyMatch(problem ->
            problem.code().equals("invalid_palette_index")));
        assertTrue(failure.problems().stream().anyMatch(problem ->
            problem.code().equals("duplicate_build_coordinate")));
        assertTrue(failure.problems().stream().allMatch(problem ->
            !problem.cause().isBlank() && !problem.suggestion().isBlank()));
    }

    @Test
    void compilesValidatedCompactOperationsAndBindsMetadata() {
        BuildDraft draft = new BuildDraft(
            1,
            "Stone forge shell",
            List.of(
                "minecraft:stone_bricks",
                "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"
            ),
            List.of("0,0,0,0", "3,3,3,1")
        );

        CompiledBuildArtifact artifact = BuilderResponseValidator.validateAndCompile(
            draft,
            BuilderTestFixtures.context(),
            BuilderTestFixtures.PROJECT_ID,
            BuilderTestFixtures.planVersion(),
            BuilderTestFixtures.settings(20)
        );

        assertEquals(1, artifact.schemaVersion());
        UUID.fromString(artifact.buildId());
        Instant.parse(artifact.createdAt());
        assertEquals(BuilderTestFixtures.context().selectionId(), artifact.selectionId());
        assertEquals(BuilderTestFixtures.PROJECT_ID, artifact.projectId());
        assertEquals("v3", artifact.planVersionId());
        assertEquals(BuilderTestFixtures.planVersion().contentHash(), artifact.planContentHash());
        assertEquals(BuilderTestFixtures.planVersion().contextHash(), artifact.contextHash());
        assertEquals("gpt-builder-test", artifact.model());
        assertEquals("low", artifact.reasoningLevel());
        assertEquals(3, artifact.operations().get(1).relativeX());
        assertEquals(1, artifact.operations().get(1).paletteIndex());
    }

    @Test
    void rejectsOutOfBoundsAndDuplicateCoordinates() {
        assertFailure(
            new BuildDraft(1, "Bad", List.of("minecraft:stone"), List.of("4,0,0,0")),
            "build_operation_out_of_bounds"
        );
        assertFailure(
            new BuildDraft(1, "Bad", List.of("minecraft:stone"), List.of("0,0,0,0", "0,0,0,0")),
            "duplicate_build_coordinate"
        );
    }

    @Test
    void rejectsNonCanonicalCompactIntegersAndPaletteIndices() {
        assertFailure(
            new BuildDraft(1, "Bad", List.of("minecraft:stone"), List.of("01,0,0,0")),
            "invalid_build_operation"
        );
        assertFailure(
            new BuildDraft(1, "Bad", List.of("minecraft:stone"), List.of("0,0,0,1")),
            "invalid_palette_index"
        );
    }

    @Test
    void rejectsDangerousDuplicateAndNonCanonicalBlockStates() {
        assertFailure(
            new BuildDraft(1, "Bad", List.of("minecraft:command_block"), List.of("0,0,0,0")),
            "dangerous_block_state"
        );
        assertFailure(
            new BuildDraft(1, "Bad", List.of("minecraft:stone", "minecraft:stone"), List.of("0,0,0,0")),
            "duplicate_palette_state"
        );
        assertFailure(
            new BuildDraft(
                1,
                "Bad",
                List.of("minecraft:oak_stairs[half=bottom,facing=north]"),
                List.of("0,0,0,0")
            ),
            "invalid_block_state"
        );
        assertFailure(
            new BuildDraft(1, "Bad", List.of("stone"), List.of("0,0,0,0")),
            "invalid_block_state"
        );
    }

    @Test
    void rejectsEmptyOrOverBudgetBuilds() {
        assertFailure(
            new BuildDraft(1, "Nothing", List.of("minecraft:air"), List.of()),
            "invalid_build_operations"
        );
        BuilderException exception = assertThrows(
            BuilderException.class,
            () -> BuilderResponseValidator.validateAndCompile(
                new BuildDraft(1, "Too much", List.of("minecraft:stone"), List.of("0,0,0,0", "1,0,0,0")),
                BuilderTestFixtures.context(),
                BuilderTestFixtures.PROJECT_ID,
                BuilderTestFixtures.planVersion(),
                BuilderTestFixtures.settings(1)
            )
        );
        assertEquals("too_many_build_operations", exception.getMessage());
    }

    @Test
    void rejectsPlanThatDoesNotBelongToCurrentContext() {
        PlanVersion valid = BuilderTestFixtures.planVersion();
        PlanVersion wrongContext = new PlanVersion(
            valid.schemaVersion(), valid.id(), valid.parentVersionId(), valid.sourceVersionId(),
            valid.createdAt(), valid.action(), valid.instruction(), valid.changeSummary(),
            "b".repeat(64), valid.contentHash(), valid.model(), valid.reasoningLevel(), valid.intention()
        );

        BuilderException exception = assertThrows(
            BuilderException.class,
            () -> BuilderResponseValidator.validateInputs(
                BuilderTestFixtures.context(),
                BuilderTestFixtures.PROJECT_ID,
                wrongContext,
                BuilderTestFixtures.settings(20)
            )
        );

        assertEquals("plan_context_mismatch", exception.getMessage());
    }

    @Test
    void portableCompilationDoesNotRequireApiSettings() {
        BuildDraft draft = new BuildDraft(
            1,
            "Portable forge",
            List.of("minecraft:stone_bricks"),
            List.of("0,0,0,0")
        );

        CompiledBuildArtifact artifact = BuilderResponseValidator.validateAndCompilePortable(
            draft,
            BuilderTestFixtures.context(),
            BuilderTestFixtures.PROJECT_ID,
            BuilderTestFixtures.planVersion(),
            20
        );

        assertEquals("craftgpt-building-skill", artifact.model());
        assertEquals("subscription", artifact.reasoningLevel());
    }

    private void assertFailure(BuildDraft draft, String expectedMessage) {
        BuilderException exception = assertThrows(
            BuilderException.class,
            () -> BuilderResponseValidator.validateAndCompile(
                draft,
                BuilderTestFixtures.context(),
                BuilderTestFixtures.PROJECT_ID,
                BuilderTestFixtures.planVersion(),
                BuilderTestFixtures.settings(20)
            )
        );
        assertEquals(expectedMessage, exception.getMessage());
    }
}
