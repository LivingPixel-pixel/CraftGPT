package dev.craftgpt.area;

import net.minecraft.core.BlockPos;

public record AreaBounds(BlockPos min, BlockPos max) {
    public static AreaBounds between(BlockPos first, BlockPos second) {
        BlockPos min = new BlockPos(
            Math.min(first.getX(), second.getX()),
            Math.min(first.getY(), second.getY()),
            Math.min(first.getZ(), second.getZ())
        );
        BlockPos max = new BlockPos(
            Math.max(first.getX(), second.getX()),
            Math.max(first.getY(), second.getY()),
            Math.max(first.getZ(), second.getZ())
        );
        return new AreaBounds(min, max);
    }

    public int width() {
        return max.getX() - min.getX() + 1;
    }

    public int height() {
        return max.getY() - min.getY() + 1;
    }

    public int depth() {
        return max.getZ() - min.getZ() + 1;
    }

    public long volume() {
        return (long) width() * height() * depth();
    }
}
