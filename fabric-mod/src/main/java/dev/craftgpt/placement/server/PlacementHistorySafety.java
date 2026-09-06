package dev.craftgpt.placement.server;

import dev.craftgpt.placement.PlacementStatusCodes;
import dev.craftgpt.placement.model.PlacementChange;
import dev.craftgpt.placement.storage.PlacementJournalRepository.LoadedPlacement;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure ordering/overlap gate for explicit recovery of an older journal entry. */
final class PlacementHistorySafety {
    private PlacementHistorySafety() {
    }

    static boolean hasBlockingNewerOverlap(
        List<LoadedPlacement> newestFirst,
        LoadedPlacement target
    ) {
        Set<Coordinate> targetPositions = coordinates(target);
        boolean foundTarget = false;
        for (LoadedPlacement candidate : newestFirst) {
            if (candidate.snapshot().placementId().equals(target.snapshot().placementId())) {
                foundTarget = true;
                break;
            }
            if (PlacementStatusCodes.UNDONE.equals(candidate.state().status())
                || !candidate.snapshot().dimension().equals(target.snapshot().dimension())) {
                continue;
            }
            for (Coordinate coordinate : coordinates(candidate)) {
                if (targetPositions.contains(coordinate)) {
                    return true;
                }
            }
        }
        // A target outside the bounded, validated history window is never mutated by ID alone.
        return !foundTarget;
    }

    private static Set<Coordinate> coordinates(LoadedPlacement loaded) {
        Set<Coordinate> coordinates = new HashSet<>();
        for (PlacementChange change : loaded.snapshot().changes()) {
            coordinates.add(new Coordinate(
                loaded.snapshot().dimension(),
                change.x(),
                change.y(),
                change.z()
            ));
        }
        return coordinates;
    }

    private record Coordinate(String dimension, int x, int y, int z) {
    }
}
