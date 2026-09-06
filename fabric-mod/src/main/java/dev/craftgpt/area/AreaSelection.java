package dev.craftgpt.area;

import net.minecraft.core.BlockPos;

import java.util.Optional;
import java.util.UUID;

public record AreaSelection(String selectionId, String dimension, BlockPos start, Optional<BlockPos> end) {
    public static AreaSelection started(String dimension, BlockPos start) {
        return new AreaSelection(UUID.randomUUID().toString(), dimension, start.immutable(), Optional.empty());
    }

    public AreaSelection complete(BlockPos stop) {
        return new AreaSelection(selectionId, dimension, start, Optional.of(stop.immutable()));
    }

    public boolean isComplete() {
        return end.isPresent();
    }

    public Optional<AreaBounds> bounds() {
        return end.map(stop -> AreaBounds.between(start, stop));
    }
}
