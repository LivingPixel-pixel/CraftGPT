package dev.craftgpt.client.context;

import dev.craftgpt.client.config.ContextMode;
import dev.craftgpt.context.AreaContext;
import dev.craftgpt.context.AreaPoint;
import dev.craftgpt.context.ExactBlockContext;
import dev.craftgpt.context.LayerSummary;
import dev.craftgpt.context.SurfaceSample;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AreaContextPromptFormatterTest {
    @Test
    void compressedModeUsesSummariesWithoutExactCoordinates() {
        String prompt = AreaContextPromptFormatter.format(context(), ContextMode.COMPRESSED, 8_192);

        assertTrue(prompt.contains("Context mode: compressed"));
        assertTrue(prompt.contains("Relative sampled surfaces"));
        assertTrue(prompt.contains("Exact coordinate map: omitted"));
        assertFalse(prompt.contains("0,0,0,0;1,1,1,0;0,1,1,1"));
    }

    @Test
    void fullModeIncludesEveryExactNonAirCoordinateAndAirSemantics() {
        String prompt = AreaContextPromptFormatter.format(context(), ContextMode.FULL, 3);

        assertTrue(prompt.contains("Context mode: full"));
        assertTrue(prompt.contains("0=minecraft:stone;1=minecraft:oak_log[axis=y]"));
        assertTrue(prompt.contains("0,0,0,0;1,1,1,0;0,1,1,1"));
        assertTrue(prompt.contains("Every unlisted coordinate inside the dimensions is minecraft:air"));
    }

    @Test
    void fullModeRefusesToTruncateAboveConfiguredLimit() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> AreaContextPromptFormatter.format(context(), ContextMode.FULL, 2)
        );
        assertTrue(exception.getMessage().contains("too_large"));
    }

    private AreaContext context() {
        return new AreaContext(
            1,
            "00000000-0000-0000-0000-000000000123",
            "minecraft:overworld",
            new AreaPoint(100, 64, 200),
            new AreaPoint(101, 65, 201),
            2, 2, 2, 8, 8, 3, 0, 2, 64, 65,
            Map.of("minecraft:air", 5, "minecraft:stone", 2, "minecraft:oak_log", 1),
            "a".repeat(64),
            List.of(
                new LayerSummary(0, 1, "minecraft:air"),
                new LayerSummary(1, 2, "minecraft:air")
            ),
            List.of(
                new SurfaceSample(0, 0, 0, "minecraft:stone"),
                new SurfaceSample(0, 1, 1, "minecraft:oak_log"),
                new SurfaceSample(1, 1, 1, "minecraft:stone")
            ),
            new ExactBlockContext(
                true,
                3,
                List.of("minecraft:stone", "minecraft:oak_log[axis=y]"),
                "0,0,0,0;1,1,1,0;0,1,1,1"
            )
        );
    }
}
