package dev.craftgpt.placement.model;

/** One absolute, reversible block-state change with optional server-captured before data. */
public record PlacementChange(
    int x,
    int y,
    int z,
    String beforeState,
    String afterState,
    String beforeBlockEntityNbt
) {
    public PlacementChange(int x, int y, int z, String beforeState, String afterState) {
        this(x, y, z, beforeState, afterState, null);
    }

    public boolean hasBlockEntityData() {
        return beforeBlockEntityNbt != null && !beforeBlockEntityNbt.isBlank();
    }
}
