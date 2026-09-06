package dev.craftgpt.placement.model;

/** Bounded, coordinate-free recovery metadata sent to the owning client. */
public record PlacementHistoryEntry(
    String placementId,
    String buildId,
    String dimension,
    String createdAt,
    String status,
    int processed,
    int total,
    int conflicts,
    boolean hasBlockEntityData,
    boolean live,
    boolean archived
) {
}
