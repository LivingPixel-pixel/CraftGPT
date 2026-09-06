package dev.craftgpt.client.planning.api;

import dev.craftgpt.context.AreaContext;
import dev.craftgpt.context.AreaPoint;
import dev.craftgpt.context.LayerSummary;
import dev.craftgpt.context.SurfaceSample;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlannerPromptFactoryTest {
    @Test
    void promptUsesRelativeGeometryAndOmitsAbsoluteCoordinates() {
        AreaContext context = new AreaContext(
            1, "00000000-0000-0000-0000-000000000099", "minecraft:overworld",
            new AreaPoint(123_456, 64, -234_567),
            new AreaPoint(123_465, 73, -234_558),
            10, 10, 10, 1_000, 1_000, 600, 0,
            100, 66, 71, Map.of("minecraft:stone", 600),
            "a".repeat(64),
            IntStream.range(0, 10)
                .mapToObj(y -> new LayerSummary(y, 60, "minecraft:stone"))
                .toList(),
            List.of(new SurfaceSample(2, 3, 7, "minecraft:stone"))
        );

        String prompt = PlannerPromptFactory.userPrompt(context, "Build a forge", null, 900);

        assertFalse(prompt.contains("123456"));
        assertFalse(prompt.contains("-234567"));
        assertFalse(prompt.contains("00000000-0000-0000-0000-000000000099"));
        assertTrue(prompt.contains("10 x 10 x 10"));
        assertTrue(prompt.contains("Y=2..7"));
        assertTrue(prompt.contains("0:60:minecraft:stone"));
        assertTrue(prompt.contains("2,3@7:minecraft:stone"));
        assertFalse(prompt.contains("a".repeat(64)));
    }
}
