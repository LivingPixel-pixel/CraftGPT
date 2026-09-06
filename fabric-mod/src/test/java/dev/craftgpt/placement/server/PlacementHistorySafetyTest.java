package dev.craftgpt.placement.server;

import dev.craftgpt.placement.PlacementLimits;
import dev.craftgpt.placement.PlacementStatusCodes;
import dev.craftgpt.placement.model.PlacementChange;
import dev.craftgpt.placement.model.PlacementSnapshot;
import dev.craftgpt.placement.model.PlacementState;
import dev.craftgpt.placement.storage.PlacementJournalRepository.LoadedPlacement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlacementHistorySafetyTest {
    @Test
    void blocksOverlappingNewerActivePlacementButAllowsNonOverlappingOrUndoneEntries() {
        LoadedPlacement target = placement("11111111-1111-1111-1111-111111111111", 1,
            PlacementStatusCodes.PLACED);
        LoadedPlacement overlapping = placement("22222222-2222-2222-2222-222222222222", 1,
            PlacementStatusCodes.PLACED);
        LoadedPlacement separate = placement("33333333-3333-3333-3333-333333333333", 9,
            PlacementStatusCodes.PLACED);
        LoadedPlacement undoneOverlap = placement("44444444-4444-4444-4444-444444444444", 1,
            PlacementStatusCodes.UNDONE);

        assertTrue(PlacementHistorySafety.hasBlockingNewerOverlap(
            List.of(overlapping, target), target
        ));
        assertFalse(PlacementHistorySafety.hasBlockingNewerOverlap(
            List.of(separate, target), target
        ));
        assertFalse(PlacementHistorySafety.hasBlockingNewerOverlap(
            List.of(undoneOverlap, target), target
        ));
        assertTrue(PlacementHistorySafety.hasBlockingNewerOverlap(List.of(separate), target));
    }

    private LoadedPlacement placement(String placementId, int x, String status) {
        PlacementSnapshot snapshot = new PlacementSnapshot(
            PlacementLimits.SCHEMA_VERSION,
            placementId,
            "55555555-5555-5555-5555-555555555555",
            "66666666-6666-6666-6666-666666666666",
            "77777777-7777-7777-7777-777777777777",
            "minecraft:overworld",
            "2026-07-15T10:00:00Z",
            List.of(new PlacementChange(x, 2, 3, "minecraft:stone", "minecraft:dirt"))
        );
        PlacementState state = new PlacementState(
            PlacementLimits.SCHEMA_VERSION,
            placementId,
            "a".repeat(64),
            status,
            1,
            PlacementStatusCodes.UNDONE.equals(status) ? 1 : 0,
            0,
            "2026-07-15T10:01:00Z"
        );
        return new LoadedPlacement(snapshot, state);
    }
}
