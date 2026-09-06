package dev.craftgpt.context;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AreaContextTest {
    @Test
    void hashIsIndependentOfMaterialInsertionOrderAndSelectionId() {
        Map<String, Integer> first = new LinkedHashMap<>();
        first.put("minecraft:stone", 3);
        first.put("minecraft:air", 5);
        Map<String, Integer> second = new LinkedHashMap<>();
        second.put("minecraft:air", 5);
        second.put("minecraft:stone", 3);

        AreaContext firstContext = context("00000000-0000-0000-0000-000000000001", first, "a".repeat(64));
        AreaContext secondContext = context("00000000-0000-0000-0000-000000000002", second, "a".repeat(64));
        assertEquals(AreaContextHasher.sha256(firstContext), AreaContextHasher.sha256(secondContext));
    }

    @Test
    void contextHashChangesWithWorldStateHash() {
        AreaContext first = context(defaultMaterials(), "a".repeat(64));
        AreaContext changed = context(defaultMaterials(), "b".repeat(64));

        assertNotEquals(AreaContextHasher.sha256(first), AreaContextHasher.sha256(changed));
    }

    @Test
    void exactPromptRepresentationDoesNotChangeStableWorldContextHash() {
        AreaContext summaryOnly = context(defaultMaterials(), "a".repeat(64));
        AreaContext exact = new AreaContext(
            summaryOnly.schemaVersion(), summaryOnly.selectionId(), summaryOnly.dimension(),
            summaryOnly.min(), summaryOnly.max(), summaryOnly.width(), summaryOnly.height(),
            summaryOnly.depth(), summaryOnly.volume(), summaryOnly.sampledBlocks(),
            summaryOnly.nonAirBlocks(), summaryOnly.unloadedBlocks(), summaryOnly.occupiedColumns(),
            summaryOnly.minimumSurfaceY(), summaryOnly.maximumSurfaceY(), summaryOnly.materials(),
            summaryOnly.worldStateHash(), summaryOnly.layers(), summaryOnly.surfaceSamples(),
            new ExactBlockContext(
                true,
                3,
                List.of("minecraft:stone"),
                "0,0,0,0;1,1,0,0;1,1,1,0"
            )
        );

        assertEquals(AreaContextHasher.sha256(summaryOnly), AreaContextHasher.sha256(exact));
        assertEquals(exact, AreaContextValidator.validate(exact));
    }

    @Test
    void validatorRejectsDuplicateExactCoordinates() {
        AreaContext valid = context(defaultMaterials());
        AreaContext invalid = new AreaContext(
            valid.schemaVersion(), valid.selectionId(), valid.dimension(), valid.min(), valid.max(),
            valid.width(), valid.height(), valid.depth(), valid.volume(), valid.sampledBlocks(),
            valid.nonAirBlocks(), valid.unloadedBlocks(), valid.occupiedColumns(), valid.minimumSurfaceY(),
            valid.maximumSurfaceY(), valid.materials(), valid.worldStateHash(), valid.layers(),
            valid.surfaceSamples(),
            new ExactBlockContext(
                true,
                3,
                List.of("minecraft:stone"),
                "0,0,0,0;0,0,0,0;1,1,1,0"
            )
        );

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> AreaContextValidator.validate(invalid)
        );
        assertTrue(exception.getMessage().contains("duplicates"));
    }

    @Test
    void validatorAcceptsConsistentContext() {
        assertEquals(context(defaultMaterials()), AreaContextValidator.validate(context(defaultMaterials())));
    }

    @Test
    void olderJsonWithoutExactBlocksRemainsReadable() {
        AreaContext original = context(defaultMaterials());
        JsonObject oldJson = JsonParser.parseString(AreaContextJson.encode(original)).getAsJsonObject();
        oldJson.remove("exactBlocks");

        AreaContext decoded = AreaContextJson.decode(oldJson.toString());

        assertEquals(original.selectionId(), decoded.selectionId());
        assertTrue(!decoded.exactBlocks().complete());
        assertEquals(AreaContextHasher.sha256(original), AreaContextHasher.sha256(decoded));
        AreaContextValidator.validate(decoded);
    }

    @Test
    void validatorRejectsInvalidWorldHash() {
        AreaContext valid = context(defaultMaterials());
        AreaContext invalid = new AreaContext(
            valid.schemaVersion(), valid.selectionId(), valid.dimension(), valid.min(), valid.max(),
            valid.width(), valid.height(), valid.depth(), valid.volume(), valid.sampledBlocks(),
            valid.nonAirBlocks(), valid.unloadedBlocks(), valid.occupiedColumns(), valid.minimumSurfaceY(),
            valid.maximumSurfaceY(), valid.materials(), "not-a-hash", valid.layers(), valid.surfaceSamples()
        );

        assertThrows(IllegalArgumentException.class, () -> AreaContextValidator.validate(invalid));
    }

    @Test
    void validatorRejectsLayerTotalsThatDisagreeWithContext() {
        AreaContext valid = context(defaultMaterials());
        AreaContext invalid = new AreaContext(
            valid.schemaVersion(), valid.selectionId(), valid.dimension(), valid.min(), valid.max(),
            valid.width(), valid.height(), valid.depth(), valid.volume(), valid.sampledBlocks(),
            valid.nonAirBlocks(), valid.unloadedBlocks(), valid.occupiedColumns(), valid.minimumSurfaceY(),
            valid.maximumSurfaceY(), valid.materials(), valid.worldStateHash(),
            List.of(
                new LayerSummary(0, 0, "minecraft:air"),
                new LayerSummary(1, 1, "minecraft:stone")
            ),
            valid.surfaceSamples()
        );

        assertThrows(IllegalArgumentException.class, () -> AreaContextValidator.validate(invalid));
    }

    @Test
    void validatorRejectsDuplicateSurfaceColumns() {
        AreaContext valid = context(defaultMaterials());
        AreaContext invalid = new AreaContext(
            valid.schemaVersion(), valid.selectionId(), valid.dimension(), valid.min(), valid.max(),
            valid.width(), valid.height(), valid.depth(), valid.volume(), valid.sampledBlocks(),
            valid.nonAirBlocks(), valid.unloadedBlocks(), valid.occupiedColumns(), valid.minimumSurfaceY(),
            valid.maximumSurfaceY(), valid.materials(), valid.worldStateHash(), valid.layers(),
            List.of(
                new SurfaceSample(0, 0, 0, "minecraft:stone"),
                new SurfaceSample(0, 0, 1, "minecraft:stone")
            )
        );

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> AreaContextValidator.validate(invalid)
        );
        assertTrue(exception.getMessage().contains("duplicate"));
    }

    private AreaContext context(Map<String, Integer> materials) {
        return context("00000000-0000-0000-0000-000000000001", materials, "a".repeat(64));
    }

    private AreaContext context(Map<String, Integer> materials, String worldStateHash) {
        return context("00000000-0000-0000-0000-000000000001", materials, worldStateHash);
    }

    private AreaContext context(String selectionId, Map<String, Integer> materials, String worldStateHash) {
        return new AreaContext(
            1,
            selectionId,
            "minecraft:overworld",
            new AreaPoint(100, 64, 200),
            new AreaPoint(101, 65, 201),
            2,
            2,
            2,
            8,
            8,
            3,
            0,
            2,
            64,
            65,
            materials,
            worldStateHash,
            List.of(
                new LayerSummary(0, 1, "minecraft:air"),
                new LayerSummary(1, 2, "minecraft:air")
            ),
            List.of(
                new SurfaceSample(0, 0, 0, "minecraft:stone"),
                new SurfaceSample(1, 1, 1, "minecraft:stone")
            )
        );
    }

    private Map<String, Integer> defaultMaterials() {
        Map<String, Integer> materials = new LinkedHashMap<>();
        materials.put("minecraft:stone", 3);
        materials.put("minecraft:air", 5);
        return materials;
    }
}
