package dev.craftgpt.context;

import dev.craftgpt.area.AreaBounds;
import dev.craftgpt.area.AreaSelection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AreaContextExtractor {
    private static final int MATERIAL_LIMIT = 48;
    private static final int SURFACE_GRID_AXIS_LIMIT = 32;
    private static final int EXACT_CONTEXT_CHARACTER_LIMIT = 1_000_000;
    private static final String AIR_ID = "minecraft:air";
    private static final byte[] HASH_DOMAIN = "CraftGPT world state v1".getBytes(StandardCharsets.UTF_8);

    private AreaContextExtractor() {
    }

    public static AreaContext extract(ServerLevel level, AreaSelection selection) {
        AreaBounds bounds = selection.bounds().orElseThrow();
        Map<String, Integer> materialCounts = new HashMap<>();
        Map<String, Integer> exactPaletteIndices = new LinkedHashMap<>();
        StringBuilder exactBlocks = new StringBuilder();
        List<Map<String, Integer>> layerMaterialCounts = new ArrayList<>(bounds.height());
        for (int relativeY = 0; relativeY < bounds.height(); relativeY++) {
            layerMaterialCounts.add(new HashMap<>());
        }
        int[] layerNonAirCounts = new int[bounds.height()];
        int[] surfaceHeights = new int[bounds.width() * bounds.depth()];
        String[] surfaceBlockIds = new String[surfaceHeights.length];
        Arrays.fill(surfaceHeights, Integer.MIN_VALUE);
        MessageDigest worldStateDigest = newWorldStateDigest(bounds);

        int sampledBlocks = 0;
        int nonAirBlocks = 0;
        int unloadedBlocks = 0;

        for (BlockPos position : BlockPos.betweenClosed(bounds.min(), bounds.max())) {
            int relativeX = position.getX() - bounds.min().getX();
            int relativeY = position.getY() - bounds.min().getY();
            int relativeZ = position.getZ() - bounds.min().getZ();
            updateCoordinate(worldStateDigest, relativeX, relativeY, relativeZ);

            if (!level.hasChunk(position.getX() >> 4, position.getZ() >> 4)) {
                updateText(worldStateDigest, "!unloaded");
                unloadedBlocks++;
                continue;
            }

            BlockState state = level.getBlockState(position);
            String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            String canonicalState = canonicalBlockState(blockId, state);
            updateText(worldStateDigest, canonicalState);
            sampledBlocks++;
            materialCounts.merge(blockId, 1, Integer::sum);
            layerMaterialCounts.get(relativeY).merge(blockId, 1, Integer::sum);

            if (!state.isAir()) {
                nonAirBlocks++;
                int paletteIndex = exactPaletteIndices.computeIfAbsent(
                    canonicalState,
                    ignored -> exactPaletteIndices.size()
                );
                appendExactBlock(exactBlocks, relativeX, relativeY, relativeZ, paletteIndex);
                layerNonAirCounts[relativeY]++;
                int column = relativeX * bounds.depth() + relativeZ;
                if (position.getY() > surfaceHeights[column]) {
                    surfaceHeights[column] = position.getY();
                    surfaceBlockIds[column] = blockId;
                }
            }
        }

        int occupiedColumns = 0;
        int minimumSurfaceY = Integer.MAX_VALUE;
        int maximumSurfaceY = Integer.MIN_VALUE;
        for (int height : surfaceHeights) {
            if (height != Integer.MIN_VALUE) {
                occupiedColumns++;
                minimumSurfaceY = Math.min(minimumSurfaceY, height);
                maximumSurfaceY = Math.max(maximumSurfaceY, height);
            }
        }
        if (occupiedColumns == 0) {
            minimumSurfaceY = bounds.min().getY();
            maximumSurfaceY = bounds.min().getY();
        }

        List<LayerSummary> layers = new ArrayList<>(bounds.height());
        for (int relativeY = 0; relativeY < bounds.height(); relativeY++) {
            layers.add(new LayerSummary(
                relativeY,
                layerNonAirCounts[relativeY],
                dominantBlock(layerMaterialCounts.get(relativeY))
            ));
        }

        List<SurfaceSample> surfaceSamples = sampleSurfaces(bounds, surfaceHeights, surfaceBlockIds);
        Map<String, Integer> topMaterials = topMaterials(materialCounts);
        String worldStateHash = HexFormat.of().formatHex(worldStateDigest.digest());
        List<String> exactPalette = List.copyOf(exactPaletteIndices.keySet());
        int exactCharacters = exactBlocks.length()
            + exactPalette.stream().mapToInt(String::length).sum();
        ExactBlockContext exactContext = unloadedBlocks == 0
            && exactCharacters <= EXACT_CONTEXT_CHARACTER_LIMIT
            ? new ExactBlockContext(true, nonAirBlocks, exactPalette, exactBlocks.toString())
            : ExactBlockContext.unavailable();

        return AreaContextValidator.validate(new AreaContext(
            1,
            selection.selectionId(),
            selection.dimension(),
            AreaPoint.from(bounds.min()),
            AreaPoint.from(bounds.max()),
            bounds.width(),
            bounds.height(),
            bounds.depth(),
            bounds.volume(),
            sampledBlocks,
            nonAirBlocks,
            unloadedBlocks,
            occupiedColumns,
            minimumSurfaceY,
            maximumSurfaceY,
            topMaterials,
            worldStateHash,
            layers,
            surfaceSamples,
            exactContext
        ));
    }

    private static void appendExactBlock(
        StringBuilder output,
        int relativeX,
        int relativeY,
        int relativeZ,
        int paletteIndex
    ) {
        if (!output.isEmpty()) {
            output.append(';');
        }
        output.append(relativeX).append(',')
            .append(relativeY).append(',')
            .append(relativeZ).append(',')
            .append(paletteIndex);
    }

    private static MessageDigest newWorldStateDigest(AreaBounds bounds) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(HASH_DOMAIN);
            updateInt(digest, bounds.width());
            updateInt(digest, bounds.height());
            updateInt(digest, bounds.depth());
            return digest;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static void updateCoordinate(MessageDigest digest, int relativeX, int relativeY, int relativeZ) {
        updateInt(digest, relativeX);
        updateInt(digest, relativeY);
        updateInt(digest, relativeZ);
    }

    private static void updateText(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        updateInt(digest, bytes.length);
        digest.update(bytes);
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static String canonicalBlockState(String blockId, BlockState state) {
        StringBuilder canonical = new StringBuilder(blockId);
        List<Property<?>> values = state.getProperties().stream()
            .sorted(Comparator.comparing(Property::getName))
            .toList();
        if (!values.isEmpty()) {
            canonical.append('[');
            for (int index = 0; index < values.size(); index++) {
                if (index > 0) {
                    canonical.append(',');
                }
                Property<?> property = values.get(index);
                canonical.append(property.getName()).append('=').append(propertyValue(state, property));
            }
            canonical.append(']');
        }
        return canonical.toString();
    }

    private static <T extends Comparable<T>> String propertyValue(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static String dominantBlock(Map<String, Integer> counts) {
        return counts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry.comparingByKey()))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(AIR_ID);
    }

    private static Map<String, Integer> topMaterials(Map<String, Integer> counts) {
        List<Map.Entry<String, Integer>> sortedMaterials = new ArrayList<>(counts.entrySet());
        sortedMaterials.sort(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
            .thenComparing(Map.Entry.comparingByKey()));
        Map<String, Integer> topMaterials = new LinkedHashMap<>();
        sortedMaterials.stream().limit(MATERIAL_LIMIT)
            .forEach(entry -> topMaterials.put(entry.getKey(), entry.getValue()));
        return topMaterials;
    }

    private static List<SurfaceSample> sampleSurfaces(
        AreaBounds bounds,
        int[] surfaceHeights,
        String[] surfaceBlockIds
    ) {
        List<SurfaceSample> samples = new ArrayList<>();
        for (int relativeX : sampleOffsets(bounds.width())) {
            for (int relativeZ : sampleOffsets(bounds.depth())) {
                int column = relativeX * bounds.depth() + relativeZ;
                if (surfaceHeights[column] != Integer.MIN_VALUE) {
                    samples.add(new SurfaceSample(
                        relativeX,
                        relativeZ,
                        surfaceHeights[column] - bounds.min().getY(),
                        surfaceBlockIds[column]
                    ));
                }
            }
        }
        return List.copyOf(samples);
    }

    private static int[] sampleOffsets(int axisLength) {
        int sampleCount = Math.min(axisLength, SURFACE_GRID_AXIS_LIMIT);
        int[] offsets = new int[sampleCount];
        if (sampleCount == 1) {
            return offsets;
        }
        for (int index = 0; index < sampleCount; index++) {
            offsets[index] = (int) Math.round((double) index * (axisLength - 1) / (sampleCount - 1));
        }
        return offsets;
    }
}
