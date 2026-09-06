package dev.craftgpt.client.planning.api;

import dev.craftgpt.client.planning.model.IntentionSpec;
import dev.craftgpt.client.planning.model.MaterialRole;
import dev.craftgpt.client.planning.model.PlanDimensions;
import dev.craftgpt.context.AreaContext;
import dev.craftgpt.context.AreaPoint;
import dev.craftgpt.context.LayerSummary;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlannerResponseValidatorTest {
    @Test
    void reportsAllIndependentPlanProblemsTogether() {
        IntentionSpec base = plan(8, 7, 8, 500);
        IntentionSpec broken = new IntentionSpec(
            base.title(), base.summary(), List.of(" "), " ",
            new PlanDimensions(99, 0, 8), base.orientation(),
            List.of(new MaterialRole(
                "walls",
                List.of("minecraft:water", "stone"),
                "Durable shell"
            )),
            base.requiredFeatures(), base.preferredFeatures(), base.constraints(),
            base.avoid(), base.assumptions(), 999, base.designRationale(),
            base.implementationBrief()
        );

        PlannerException failure = assertThrows(PlannerException.class, () ->
            PlannerResponseValidator.validate(broken, context(), 800));

        assertTrue(failure.problems().stream().anyMatch(problem ->
            problem.location().equals("plan.style")));
        assertTrue(failure.problems().stream().anyMatch(problem ->
            problem.code().equals("invalid_plan_dimensions")));
        assertTrue(failure.problems().stream().anyMatch(problem ->
            problem.code().equals("invalid_change_estimate")));
        assertTrue(failure.problems().stream().anyMatch(problem ->
            problem.location().equals("plan.goals[0]")));
        assertTrue(failure.problems().stream().filter(problem ->
            problem.code().startsWith("invalid_material_roles")).count() >= 2);
    }

    @Test
    void acceptsPlanWithinAreaAndBudget() {
        assertDoesNotThrow(() -> PlannerResponseValidator.validate(plan(8, 7, 8, 500), context(), 800));
    }

    @Test
    void rejectsPlanOutsideAreaOrBudget() {
        assertThrows(PlannerException.class, () ->
            PlannerResponseValidator.validate(plan(11, 7, 8, 500), context(), 800));
        assertThrows(PlannerException.class, () ->
            PlannerResponseValidator.validate(plan(8, 7, 8, 801), context(), 800));
    }

    @Test
    void rejectsBlankStyleOrientationAndDesignRationale() {
        assertThrows(PlannerException.class, () ->
            PlannerResponseValidator.validate(planWithText(" ", "Entrance south", "Fits the palette."), context(), 800));
        assertThrows(PlannerException.class, () ->
            PlannerResponseValidator.validate(planWithText("Medieval", " ", "Fits the palette."), context(), 800));
        assertThrows(PlannerException.class, () ->
            PlannerResponseValidator.validate(planWithText("Medieval", "Entrance south", " "), context(), 800));
    }

    @Test
    void rejectsBlankOrOversizedMaterialCandidates() {
        assertThrows(PlannerException.class, () ->
            PlannerResponseValidator.validate(planWithCandidates(List.of(" ")), context(), 800));
        assertThrows(PlannerException.class, () ->
            PlannerResponseValidator.validate(planWithCandidates(List.of("x".repeat(257))), context(), 800));
        assertThrows(PlannerException.class, () ->
            PlannerResponseValidator.validate(planWithCandidates(List.of("stone bricks")), context(), 800));
    }

    @Test
    void reportsTheExactInvalidMaterialCandidate() {
        PlannerException failure = assertThrows(PlannerException.class, () ->
            PlannerResponseValidator.validate(planWithCandidates(List.of("minecraft:water")), context(), 800));

        assertEquals(
            "invalid_material_roles:role=0:candidate=0:dangerous_block_state",
            failure.getMessage()
        );
    }

    @Test
    void acceptsCanonicalStatesInMaterialCandidateMetadata() {
        assertDoesNotThrow(() -> PlannerResponseValidator.validate(
            planWithCandidates(List.of("minecraft:brick_slab[type=bottom,waterlogged=false]")),
            context(),
            800
        ));
    }

    @Test
    void acceptsAllMaterialCandidatesFromTheCompactCottageResult() {
        assertDoesNotThrow(() -> PlannerResponseValidator.validate(
            planWithCandidates(List.of(
                "minecraft:oak_log",
                "minecraft:oak_planks",
                "minecraft:glass_pane",
                "minecraft:oak_door",
                "minecraft:brick_slab[type=bottom,waterlogged=false]",
                "minecraft:air"
            )),
            context(),
            800
        ));
    }

    @Test
    void rejectsExcessiveAggregatePlanText() {
        IntentionSpec base = plan(8, 7, 8, 500);
        IntentionSpec oversized = new IntentionSpec(
            base.title(), base.summary(), Collections.nCopies(9, "x".repeat(16_384)), base.style(),
            base.targetDimensions(), base.orientation(), base.materialRoles(), base.requiredFeatures(),
            base.preferredFeatures(), base.constraints(), base.avoid(), base.assumptions(),
            base.estimatedBlockChanges(), base.designRationale(), base.implementationBrief()
        );

        assertThrows(PlannerException.class, () ->
            PlannerResponseValidator.validate(oversized, context(), 800));
    }

    private IntentionSpec plan(int width, int height, int depth, int changes) {
        return new IntentionSpec(
            "Forge", "Compact forge", List.of("Functional workshop"), "Medieval",
            new PlanDimensions(width, height, depth), "Entrance south",
            List.of(new MaterialRole("walls", List.of("minecraft:stone_bricks"), "Durable shell")),
            List.of("Chimney"), List.of("Wood pile"), List.of("Stay in area"),
            List.of("Modern blocks"), List.of("Flat ground"), changes,
            "Fits the palette.", "Build a compact stone forge with a timber roof."
        );
    }

    private IntentionSpec planWithText(String style, String orientation, String designRationale) {
        IntentionSpec base = plan(8, 7, 8, 500);
        return new IntentionSpec(
            base.title(), base.summary(), base.goals(), style, base.targetDimensions(), orientation,
            base.materialRoles(), base.requiredFeatures(), base.preferredFeatures(), base.constraints(),
            base.avoid(), base.assumptions(), base.estimatedBlockChanges(), designRationale,
            base.implementationBrief()
        );
    }

    private IntentionSpec planWithCandidates(List<String> blockCandidates) {
        IntentionSpec base = plan(8, 7, 8, 500);
        return new IntentionSpec(
            base.title(), base.summary(), base.goals(), base.style(), base.targetDimensions(), base.orientation(),
            List.of(new MaterialRole("walls", blockCandidates, "Durable shell")), base.requiredFeatures(),
            base.preferredFeatures(), base.constraints(), base.avoid(), base.assumptions(),
            base.estimatedBlockChanges(), base.designRationale(), base.implementationBrief()
        );
    }

    private AreaContext context() {
        return new AreaContext(
            1, "00000000-0000-0000-0000-000000000001", "minecraft:overworld",
            new AreaPoint(0, 0, 0), new AreaPoint(9, 9, 9),
            10, 10, 10, 1_000, 1_000, 500, 0,
            100, 0, 5, Map.of("minecraft:stone", 500),
            "a".repeat(64),
            IntStream.range(0, 10)
                .mapToObj(y -> new LayerSummary(y, 50, "minecraft:stone"))
                .toList(),
            List.of()
        );
    }
}
