package dev.craftgpt.compat.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Scope the legacy callback suppression to one exact server/world position and always restore it. */
public final class PlacementScope implements AutoCloseable {
    private record Target(Object level, BlockPos position) {}
    private static final ThreadLocal<Target> CURRENT = new ThreadLocal<>();
    private final Target previous;
    private PlacementScope(Object level, BlockPos position) {
        previous = CURRENT.get();
        CURRENT.set(new Target(level, position.immutable()));
    }
    static PlacementScope enter(Object level, BlockPos position) { return new PlacementScope(level, position); }
    public static boolean active(Object level, BlockPos position) {
        Target target = CURRENT.get();
        return target != null && target.level == level && target.position.equals(position);
    }
    @Override public void close() { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    public static boolean setBlock(ServerLevel level, BlockPos position, BlockState replacement, int flags) {
        try (var scope = enter(level, position)) { return level.setBlock(position, replacement, flags); }
    }
}
