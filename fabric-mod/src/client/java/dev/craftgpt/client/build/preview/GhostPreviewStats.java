package dev.craftgpt.client.build.preview;

/**
 * Lightweight snapshot of the ghost preview state. Rendering counters describe
 * the most recently rendered frame.
 */
public record GhostPreviewStats(
    int totalOperations,
    int validOperations,
    int placementOperations,
    int removalOperations,
    int invalidOperations,
    int renderedOperations,
    int distanceCulledOperations,
    int frustumCulledOperations,
    int renderCapOmittedOperations,
    boolean hasPreview,
    boolean visible,
    boolean dimensionMatches
) {
    public static GhostPreviewStats empty(boolean visible) {
        return new GhostPreviewStats(
            0, 0, 0, 0, 0,
            0, 0, 0, 0,
            false, visible, false
        );
    }
}
