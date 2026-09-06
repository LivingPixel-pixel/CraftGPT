package dev.craftgpt.placement.model;

import java.util.List;

/** Immutable before-placement snapshot persisted before any world mutation. */
public record PlacementSnapshot(
    int schemaVersion,
    String placementId,
    String buildId,
    String ownerId,
    String selectionId,
    String dimension,
    String createdAt,
    List<PlacementChange> changes
) {
    public PlacementSnapshot {
        changes = List.copyOf(changes);
    }
}
