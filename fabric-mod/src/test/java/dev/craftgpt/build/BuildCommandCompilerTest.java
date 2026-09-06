package dev.craftgpt.build;

import dev.craftgpt.build.model.BuildOperation;
import dev.craftgpt.build.model.CompiledBuildArtifact;
import dev.craftgpt.context.AreaContext;
import dev.craftgpt.context.AreaPoint;
import dev.craftgpt.context.LayerSummary;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BuildCommandCompilerTest {
    @Test
    void compilesRelativeOperationsToDeterministicAbsoluteCommands() {
        AreaContext context = new AreaContext(
            1, UUID.randomUUID().toString(), "minecraft:overworld",
            new AreaPoint(100, 64, -20), new AreaPoint(101, 64, -20),
            2, 1, 1, 2, 2, 1, 0, 1, 64, 64,
            Map.of("minecraft:stone", 1, "minecraft:air", 1),
            "a".repeat(64), List.of(new LayerSummary(0, 1, "minecraft:air")), List.of()
        );
        CompiledBuildArtifact artifact = new CompiledBuildArtifact(
            1, UUID.randomUUID().toString(), context.selectionId(), UUID.randomUUID().toString(),
            "v1", "b".repeat(64), "c".repeat(64), Instant.now().toString(),
            "builder", "low", "test",
            List.of("minecraft:stone", "minecraft:air"),
            List.of(new BuildOperation(0, 0, 0, 0), new BuildOperation(1, 0, 0, 1))
        );

        assertEquals(List.of(
            "/setblock 100 64 -20 minecraft:stone replace",
            "/setblock 101 64 -20 minecraft:air replace"
        ), BuildCommandCompiler.toSetBlockCommands(artifact, context));
    }
}
