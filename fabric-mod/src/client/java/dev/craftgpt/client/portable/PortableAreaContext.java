package dev.craftgpt.client.portable;

import dev.craftgpt.context.AreaContext;
import dev.craftgpt.context.ExactBlockContext;
import dev.craftgpt.context.LayerSummary;
import dev.craftgpt.context.SurfaceSample;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Coordinate-free area data that is safe to attach to a Codex task. */
public record PortableAreaContext(
    String dimensionType,
    int width,
    int height,
    int depth,
    long volume,
    int nonAirBlocks,
    int occupiedColumns,
    int minimumRelativeSurfaceY,
    int maximumRelativeSurfaceY,
    Map<String, Integer> materials,
    List<LayerSummary> layers,
    List<SurfaceSample> surfaceSamples,
    ExactBlockContext exactBlocks
) {
    public PortableAreaContext {
        materials = Map.copyOf(new LinkedHashMap<>(materials));
        layers = List.copyOf(layers);
        surfaceSamples = List.copyOf(surfaceSamples);
    }

    public static PortableAreaContext from(AreaContext context) {
        return new PortableAreaContext(
            context.dimension(),
            context.width(),
            context.height(),
            context.depth(),
            context.volume(),
            context.nonAirBlocks(),
            context.occupiedColumns(),
            context.minimumSurfaceY() - context.min().y(),
            context.maximumSurfaceY() - context.min().y(),
            context.materials(),
            context.layers(),
            context.surfaceSamples(),
            context.exactBlocks()
        );
    }
}
