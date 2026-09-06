package dev.craftgpt.build.model;

/**
 * A single block-state change relative to the minimum corner of the selected area.
 */
public record BuildOperation(
    int relativeX,
    int relativeY,
    int relativeZ,
    int paletteIndex
) {
}
