package dev.craftgpt.client.build.api;

import dev.craftgpt.client.planning.model.IntentionSpec;
import dev.craftgpt.client.planning.model.MaterialRole;
import dev.craftgpt.client.planning.model.PlanDimensions;
import dev.craftgpt.client.planning.model.PlanVersion;
import dev.craftgpt.client.planning.storage.PlanContentHasher;
import dev.craftgpt.context.AreaContext;
import dev.craftgpt.context.AreaContextHasher;
import dev.craftgpt.context.AreaPoint;
import dev.craftgpt.context.LayerSummary;
import dev.craftgpt.context.SurfaceSample;

import java.util.List;
import java.util.Map;

final class BuilderTestFixtures {
    static final String PROJECT_ID = "00000000-0000-0000-0000-000000000099";

    private BuilderTestFixtures() {
    }

    static AreaContext context() {
        return new AreaContext(
            1,
            "00000000-0000-0000-0000-000000000001",
            "minecraft:overworld",
            new AreaPoint(123_456, 64, -234_567),
            new AreaPoint(123_459, 67, -234_564),
            4,
            4,
            4,
            64,
            64,
            32,
            0,
            16,
            64,
            67,
            Map.of("minecraft:air", 32, "minecraft:stone", 32),
            "a".repeat(64),
            List.of(
                new LayerSummary(0, 8, "minecraft:air"),
                new LayerSummary(1, 8, "minecraft:air"),
                new LayerSummary(2, 8, "minecraft:air"),
                new LayerSummary(3, 8, "minecraft:air")
            ),
            List.of(new SurfaceSample(1, 2, 3, "minecraft:stone"))
        );
    }

    static IntentionSpec intention() {
        return new IntentionSpec(
            "Forge",
            "A compact stone forge",
            List.of("Create a usable workshop"),
            "Medieval",
            new PlanDimensions(4, 4, 4),
            "South-facing",
            List.of(new MaterialRole(
                "walls",
                List.of("minecraft:stone_bricks"),
                "Durable exterior shell"
            )),
            List.of("Work area"),
            List.of("Chimney detail"),
            List.of("Remain inside the selected area"),
            List.of("Fluids"),
            List.of("The entrance faces south"),
            20,
            "The footprint matches the available terrain.",
            "Build a small forge with stone-brick walls and an open entrance."
        );
    }

    static PlanVersion planVersion() {
        AreaContext context = context();
        IntentionSpec intention = intention();
        return new PlanVersion(
            1,
            "v3",
            "v2",
            null,
            "2026-07-11T12:00:00Z",
            "DISCUSS",
            "Use more stone",
            "Strengthened the material plan",
            AreaContextHasher.sha256(context),
            PlanContentHasher.sha256(intention),
            "gpt-planner-test",
            "medium",
            intention
        );
    }

    static BuilderRequestSettings settings(int maximumOperations) {
        return new BuilderRequestSettings(
            "https://api.openai.com/v1/responses",
            "secret-not-serialized",
            "gpt-builder-test",
            "low",
            maximumOperations
        );
    }
}
