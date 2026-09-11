package dev.craftgpt.compat.mixin;

import dev.craftgpt.compat.placement.PlacementScope;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Backport the missing skip-placement and skip-removal-side-effect flags without global suppression. */
@Mixin(LevelChunk.class)
public abstract class LevelChunkPlacementMixin {
    @Redirect(method = "setBlockState", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/state/BlockState;onPlace(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)V"))
    private void craftgpt$onPlace(BlockState state, Level level, BlockPos position, BlockState previous, boolean moving) {
        if (!PlacementScope.active(level, position)) state.onPlace(level, position, previous, moving);
    }

    @Redirect(method = "setBlockState", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/state/BlockState;onRemove(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)V"))
    private void craftgpt$onRemove(BlockState state, Level level, BlockPos position, BlockState replacement, boolean moving) {
        if (!PlacementScope.active(level, position)) {
            state.onRemove(level, position, replacement, moving);
        } else if (state.hasBlockEntity() && !state.is(replacement.getBlock())) {
            // Preserve chunk bookkeeping without dropping container contents or notifying neighbors.
            level.removeBlockEntity(position);
        }
    }
}
