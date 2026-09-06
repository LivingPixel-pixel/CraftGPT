package dev.craftgpt.context;

import net.minecraft.core.BlockPos;

public record AreaPoint(int x, int y, int z) {
    public static AreaPoint from(BlockPos position) {
        return new AreaPoint(position.getX(), position.getY(), position.getZ());
    }
}
