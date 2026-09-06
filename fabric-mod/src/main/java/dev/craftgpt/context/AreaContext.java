package dev.craftgpt.context;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AreaContext(
    int schemaVersion,
    String selectionId,
    String dimension,
    AreaPoint min,
    AreaPoint max,
    int width,
    int height,
    int depth,
    long volume,
    int sampledBlocks,
    int nonAirBlocks,
    int unloadedBlocks,
    int occupiedColumns,
    int minimumSurfaceY,
    int maximumSurfaceY,
    Map<String, Integer> materials,
    String worldStateHash,
    List<LayerSummary> layers,
    List<SurfaceSample> surfaceSamples,
    ExactBlockContext exactBlocks
) {
    public AreaContext {
        materials = Collections.unmodifiableMap(new LinkedHashMap<>(materials));
        layers = List.copyOf(layers);
        surfaceSamples = List.copyOf(surfaceSamples);
        exactBlocks = exactBlocks == null ? ExactBlockContext.unavailable() : exactBlocks;
    }

    /** Keeps persisted Phase 3 contexts and older test/build integrations readable. */
    public AreaContext(
        int schemaVersion,
        String selectionId,
        String dimension,
        AreaPoint min,
        AreaPoint max,
        int width,
        int height,
        int depth,
        long volume,
        int sampledBlocks,
        int nonAirBlocks,
        int unloadedBlocks,
        int occupiedColumns,
        int minimumSurfaceY,
        int maximumSurfaceY,
        Map<String, Integer> materials,
        String worldStateHash,
        List<LayerSummary> layers,
        List<SurfaceSample> surfaceSamples
    ) {
        this(
            schemaVersion, selectionId, dimension, min, max,
            width, height, depth, volume, sampledBlocks, nonAirBlocks, unloadedBlocks,
            occupiedColumns, minimumSurfaceY, maximumSurfaceY, materials, worldStateHash,
            layers, surfaceSamples, ExactBlockContext.unavailable()
        );
    }

    public boolean sameArea(AreaContext other) {
        return other != null
            && dimension.equals(other.dimension)
            && min.equals(other.min)
            && max.equals(other.max);
    }

    public boolean sameSelection(AreaContext other) {
        return sameArea(other) && selectionId.equals(other.selectionId);
    }
}
