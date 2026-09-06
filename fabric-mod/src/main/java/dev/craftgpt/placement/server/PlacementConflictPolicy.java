package dev.craftgpt.placement.server;

import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

/** Pure comparison policy that prevents placement and undo from overwriting later changes. */
final class PlacementConflictPolicy {
    private PlacementConflictPolicy() {
    }

    static Decision decide(BlockState current, BlockState expected, BlockState replacement) {
        return decide(current, null, expected, null, replacement, null);
    }

    static Decision decide(
        BlockState current,
        String currentBlockEntityNbt,
        BlockState expected,
        String expectedBlockEntityNbt,
        BlockState replacement,
        String replacementBlockEntityNbt
    ) {
        if (current == null || expected == null || replacement == null) {
            return Decision.CONFLICT;
        }
        if (current.equals(expected)
            && Objects.equals(currentBlockEntityNbt, expectedBlockEntityNbt)) {
            return Decision.APPLY;
        }
        if (current.equals(replacement)
            && Objects.equals(currentBlockEntityNbt, replacementBlockEntityNbt)) {
            return Decision.ALREADY_APPLIED;
        }
        return Decision.CONFLICT;
    }

    enum Decision {
        APPLY,
        ALREADY_APPLIED,
        CONFLICT
    }
}
