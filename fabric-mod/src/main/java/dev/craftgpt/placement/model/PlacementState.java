package dev.craftgpt.placement.model;

/** Mutable progress record paired with an immutable placement snapshot. */
public record PlacementState(
    int schemaVersion,
    String placementId,
    String snapshotHash,
    String status,
    int processedChanges,
    int revertedChanges,
    int conflicts,
    String updatedAt
) {
}
