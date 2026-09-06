package dev.craftgpt.context;

import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Treats area context as untrusted input before it reaches prompts or local storage.
 */
public final class AreaContextValidator {
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_AXIS_LENGTH = 128;
    private static final long MAX_VOLUME = 32_768L;
    private static final int MAX_MATERIALS = 48;
    private static final int MAX_SURFACE_SAMPLES = 1_024;
    private static final int MAX_IDENTIFIER_LENGTH = 256;
    private static final int MAX_BLOCK_STATE_LENGTH = 512;
    private static final int MAX_EXACT_CONTEXT_CHARACTERS = 1_000_000;

    private AreaContextValidator() {
    }

    public static AreaContext validate(AreaContext context) {
        require(context != null, "Area context is missing");
        require(context.schemaVersion() == SCHEMA_VERSION, "Unsupported area context schema");
        validateSelectionId(context.selectionId());
        validateIdentifier(context.dimension(), "dimension");
        require(context.min() != null && context.max() != null, "Area bounds are missing");

        validateAxis(context.width(), "width");
        validateAxis(context.height(), "height");
        validateAxis(context.depth(), "depth");
        require(axisLength(context.min().x(), context.max().x()) == context.width(), "Width does not match bounds");
        require(axisLength(context.min().y(), context.max().y()) == context.height(), "Height does not match bounds");
        require(axisLength(context.min().z(), context.max().z()) == context.depth(), "Depth does not match bounds");

        long expectedVolume = (long) context.width() * context.height() * context.depth();
        require(expectedVolume == context.volume(), "Volume does not match dimensions");
        require(context.volume() <= MAX_VOLUME, "Area context exceeds volume limit");
        require(context.sampledBlocks() >= 0, "Sampled block count is negative");
        require(context.unloadedBlocks() >= 0, "Unloaded block count is negative");
        require((long) context.sampledBlocks() + context.unloadedBlocks() == context.volume(),
            "Sampled and unloaded counts do not match volume");
        require(context.nonAirBlocks() >= 0 && context.nonAirBlocks() <= context.sampledBlocks(),
            "Non-air block count is invalid");

        long columnCount = (long) context.width() * context.depth();
        require(context.occupiedColumns() >= 0 && context.occupiedColumns() <= columnCount,
            "Occupied column count is invalid");
        require((context.nonAirBlocks() == 0) == (context.occupiedColumns() == 0),
            "Occupied columns do not match non-air blocks");
        require((long) context.occupiedColumns() * context.height() >= context.nonAirBlocks(),
            "Occupied columns cannot contain the reported non-air blocks");
        validateSurfaceRange(context);
        validateMaterials(context);
        validateWorldStateHash(context.worldStateHash());
        validateLayers(context);
        validateSurfaceSamples(context);
        validateExactBlocks(context);
        return context;
    }

    private static void validateSelectionId(String selectionId) {
        require(selectionId != null, "Selection id is missing");
        try {
            UUID parsed = UUID.fromString(selectionId);
            require(parsed.toString().equalsIgnoreCase(selectionId), "Selection id is not canonical");
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Selection id is invalid", exception);
        }
    }

    private static void validateAxis(int value, String name) {
        require(value >= 1 && value <= MAX_AXIS_LENGTH, "Invalid " + name);
    }

    private static long axisLength(int min, int max) {
        require(max >= min, "Area bounds are reversed");
        return (long) max - min + 1L;
    }

    private static void validateSurfaceRange(AreaContext context) {
        if (context.occupiedColumns() == 0) {
            require(context.minimumSurfaceY() == context.min().y()
                    && context.maximumSurfaceY() == context.min().y(),
                "Empty area has an invalid surface range");
            return;
        }
        require(context.minimumSurfaceY() >= context.min().y(), "Minimum surface is below the area");
        require(context.maximumSurfaceY() <= context.max().y(), "Maximum surface is above the area");
        require(context.minimumSurfaceY() <= context.maximumSurfaceY(), "Surface range is reversed");
    }

    private static void validateMaterials(AreaContext context) {
        Map<String, Integer> materials = context.materials();
        require(materials != null, "Material summary is missing");
        require(materials.size() <= MAX_MATERIALS, "Material summary exceeds limit");
        require(context.sampledBlocks() == 0 || !materials.isEmpty(), "Material summary is empty");
        long total = 0L;
        for (Map.Entry<String, Integer> entry : materials.entrySet()) {
            validateIdentifier(entry.getKey(), "material");
            require(entry.getValue() != null && entry.getValue() > 0, "Material count is invalid");
            total += entry.getValue();
            require(total <= context.sampledBlocks(), "Material counts exceed sampled blocks");
        }
    }

    private static void validateWorldStateHash(String worldStateHash) {
        require(worldStateHash != null && worldStateHash.matches("[0-9a-f]{64}"),
            "World-state hash is invalid");
    }

    private static void validateLayers(AreaContext context) {
        require(context.layers() != null, "Layer summary is missing");
        require(context.layers().size() == context.height(), "Layer count does not match area height");
        long nonAirTotal = 0L;
        int blocksPerLayer = context.width() * context.depth();
        for (int index = 0; index < context.layers().size(); index++) {
            LayerSummary layer = context.layers().get(index);
            require(layer != null, "Layer summary contains null");
            require(layer.relativeY() == index, "Layer summaries are not contiguous");
            require(layer.nonAirBlocks() >= 0 && layer.nonAirBlocks() <= blocksPerLayer,
                "Layer non-air count is invalid");
            validateIdentifier(layer.dominantBlock(), "dominant block");
            nonAirTotal += layer.nonAirBlocks();
        }
        require(nonAirTotal == context.nonAirBlocks(), "Layer counts do not match non-air total");
    }

    private static void validateSurfaceSamples(AreaContext context) {
        require(context.surfaceSamples() != null, "Surface samples are missing");
        require(context.surfaceSamples().size() <= MAX_SURFACE_SAMPLES, "Surface sample limit exceeded");
        Set<Long> columns = new HashSet<>();
        for (SurfaceSample sample : context.surfaceSamples()) {
            require(sample != null, "Surface samples contain null");
            require(sample.relativeX() >= 0 && sample.relativeX() < context.width(),
                "Surface sample x is outside the area");
            require(sample.relativeZ() >= 0 && sample.relativeZ() < context.depth(),
                "Surface sample z is outside the area");
            require(sample.relativeY() >= 0 && sample.relativeY() < context.height(),
                "Surface sample y is outside the area");
            validateIdentifier(sample.blockId(), "surface block");
            require(!"minecraft:air".equals(sample.blockId()), "Surface sample cannot be air");
            long columnKey = ((long) sample.relativeX() << 32) ^ (sample.relativeZ() & 0xffff_ffffL);
            require(columns.add(columnKey), "Surface samples contain duplicate columns");
        }
        require(context.nonAirBlocks() != 0 || context.surfaceSamples().isEmpty(),
            "Empty area cannot have surface samples");
    }

    private static void validateExactBlocks(AreaContext context) {
        ExactBlockContext exact = context.exactBlocks();
        require(exact != null, "Exact block context is missing");
        require(exact.blockCount() >= 0 && exact.blockCount() <= context.nonAirBlocks(),
            "Exact block count is invalid");
        require(exact.palette() != null && exact.blocks() != null, "Exact block data is missing");
        require(exact.blocks().length() <= MAX_EXACT_CONTEXT_CHARACTERS,
            "Exact block context exceeds size limit");

        if (!exact.complete()) {
            require(exact.blockCount() == 0 && exact.palette().isEmpty() && exact.blocks().isEmpty(),
                "Incomplete exact context must not contain partial data");
            return;
        }

        require(exact.blockCount() == context.nonAirBlocks(),
            "Complete exact context does not match non-air count");
        if (exact.blockCount() == 0) {
            require(exact.palette().isEmpty() && exact.blocks().isEmpty(),
                "Empty area has exact block data");
            return;
        }
        require(!exact.palette().isEmpty() && exact.palette().size() <= exact.blockCount(),
            "Exact block palette size is invalid");
        Set<String> states = new HashSet<>();
        for (String state : exact.palette()) {
            validateBlockState(state);
            require(states.add(state), "Exact block palette contains duplicates");
        }

        String[] entries = exact.blocks().split(";", -1);
        require(entries.length == exact.blockCount(), "Exact coordinate count does not match block count");
        Set<Long> coordinates = new HashSet<>();
        for (String entry : entries) {
            String[] parts = entry.split(",", -1);
            require(parts.length == 4, "Exact block entry has invalid shape");
            int x = parseNonNegative(parts[0], "exact x");
            int y = parseNonNegative(parts[1], "exact y");
            int z = parseNonNegative(parts[2], "exact z");
            int paletteIndex = parseNonNegative(parts[3], "exact palette index");
            require(x < context.width() && y < context.height() && z < context.depth(),
                "Exact block coordinate is outside the area");
            require(paletteIndex < exact.palette().size(), "Exact block palette index is invalid");
            long coordinate = ((long) y * context.depth() + z) * context.width() + x;
            require(coordinates.add(coordinate), "Exact block coordinates contain duplicates");
        }
    }

    private static int parseNonNegative(String value, String field) {
        require(value != null && value.matches("0|[1-9][0-9]*"), "Invalid " + field);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid " + field, exception);
        }
    }

    private static void validateBlockState(String state) {
        require(state != null && !state.isEmpty() && state.length() <= MAX_BLOCK_STATE_LENGTH,
            "Exact block state is invalid");
        require(state.indexOf(';') < 0 && state.indexOf('\n') < 0 && state.indexOf('\r') < 0,
            "Exact block state contains forbidden characters");
        int properties = state.indexOf('[');
        String blockId = properties < 0 ? state : state.substring(0, properties);
        validateIdentifier(blockId, "exact block");
        require(!"minecraft:air".equals(blockId), "Exact block state cannot be air");
        if (properties >= 0) {
            require(state.endsWith("]") && properties > 0 && properties < state.length() - 2,
                "Exact block properties are malformed");
            String propertyText = state.substring(properties + 1, state.length() - 1);
            require(propertyText.matches("[a-z0-9_]+=[a-z0-9_.-]+(?:,[a-z0-9_]+=[a-z0-9_.-]+)*"),
                "Exact block properties are malformed");
        }
    }

    private static void validateIdentifier(String value, String field) {
        require(value != null, "Missing " + field);
        require(value.length() <= MAX_IDENTIFIER_LENGTH, field + " identifier is too long");
        Identifier parsed = Identifier.tryParse(value);
        require(parsed != null && parsed.toString().equals(value), "Invalid " + field + " identifier");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
