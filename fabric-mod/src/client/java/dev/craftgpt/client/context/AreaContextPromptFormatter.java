package dev.craftgpt.client.context;

import dev.craftgpt.client.config.ContextMode;
import dev.craftgpt.context.AreaContext;
import dev.craftgpt.context.ExactBlockContext;

import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class AreaContextPromptFormatter {
    public static final int HARD_MAX_FULL_CONTEXT_BLOCKS = 32_768;

    private AreaContextPromptFormatter() {
    }

    public static void requireAvailable(AreaContext context, ContextMode mode, int maximumFullBlocks) {
        if (mode == null) {
            throw new IllegalArgumentException("context_mode_missing");
        }
        if (maximumFullBlocks < 1 || maximumFullBlocks > HARD_MAX_FULL_CONTEXT_BLOCKS) {
            throw new IllegalArgumentException("full_context_limit_invalid");
        }
        if (!mode.full()) {
            return;
        }
        if (!context.exactBlocks().complete()) {
            throw new IllegalArgumentException("full_context_unavailable");
        }
        if (context.exactBlocks().blockCount() > maximumFullBlocks) {
            throw new IllegalArgumentException("full_context_too_large");
        }
    }

    public static String format(AreaContext context, ContextMode mode, int maximumFullBlocks) {
        requireAvailable(context, mode, maximumFullBlocks);
        String materials = context.materials().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.joining(","));
        String layers = context.layers().stream()
            .sorted(Comparator.comparingInt(layer -> layer.relativeY()))
            .map(layer -> layer.relativeY() + ":" + layer.nonAirBlocks() + ":" + layer.dominantBlock())
            .collect(Collectors.joining(";"));
        String surfaces = context.surfaceSamples().stream()
            .sorted(Comparator.comparingInt((dev.craftgpt.context.SurfaceSample sample) -> sample.relativeX())
                .thenComparingInt(sample -> sample.relativeZ()))
            .map(sample -> sample.relativeX() + "," + sample.relativeZ() + "@"
                + sample.relativeY() + ":" + sample.blockId())
            .collect(Collectors.joining(";"));

        String summary = """
            - Context mode: %s
            - Dimension type: %s
            - Relative origin: selected area minimum corner = 0,0,0
            - Axes: +X east, +Y up, +Z south
            - Dimensions: %d x %d x %d
            - Volume: %d; existing non-air blocks: %d; unavailable/unloaded: %d
            - Occupied X/Z columns: %d
            - Relative surface Y range: Y=%d..%d
            - Existing material counts: %s
            - Relative layers (y:nonAir:dominantBlock): %s
            - Relative sampled surfaces (x,z@y:block): %s
            """.formatted(
            mode.serializedName(),
            context.dimension(),
            context.width(), context.height(), context.depth(),
            context.volume(), context.nonAirBlocks(), context.unloadedBlocks(),
            context.occupiedColumns(),
            context.minimumSurfaceY() - context.min().y(),
            context.maximumSurfaceY() - context.min().y(),
            materials.isBlank() ? "none" : materials,
            layers.isBlank() ? "none" : layers,
            surfaces.isBlank() ? "none" : surfaces
        );
        if (!mode.full()) {
            return summary + "- Exact coordinate map: omitted in compressed mode.\n";
        }

        ExactBlockContext exact = context.exactBlocks();
        String palette = IntStream.range(0, exact.palette().size())
            .mapToObj(index -> index + "=" + exact.palette().get(index))
            .collect(Collectors.joining(";"));
        return summary + """
            - Exact coordinate semantics: every listed entry is x,y,z,paletteIndex using relative coordinates.
            - Every unlisted coordinate inside the dimensions is minecraft:air.
            - Exact palette: %s
            - Exact non-air blocks (%d): %s
            """.formatted(
            palette.isBlank() ? "none" : palette,
            exact.blockCount(),
            exact.blocks().isBlank() ? "none" : exact.blocks()
        );
    }
}
