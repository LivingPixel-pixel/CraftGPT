package dev.craftgpt.compat.placement;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlacementScopeTest {
    @Test void affectsOnlyExactWorldAndPositionAndRestoresAfterFailure() {
        Object world = new Object();
        assertThrows(IllegalStateException.class, () -> {
            try (var scope = PlacementScope.enter(world, BlockPos.ZERO)) {
                assertTrue(PlacementScope.active(world, BlockPos.ZERO));
                assertFalse(PlacementScope.active(new Object(), BlockPos.ZERO));
                assertFalse(PlacementScope.active(world, BlockPos.ZERO.above()));
                throw new IllegalStateException("placement failed");
            }
        });
        assertFalse(PlacementScope.active(world, BlockPos.ZERO));
    }
    @Test void nestedScopesRestoreTheirParentAndDoNotCrossThreads() throws Exception {
        Object world = new Object();
        try (var scope = PlacementScope.enter(world, BlockPos.ZERO)) {
            try (var nested = PlacementScope.enter(world, BlockPos.ZERO.above())) {
                assertFalse(PlacementScope.active(world, BlockPos.ZERO));
                assertTrue(PlacementScope.active(world, BlockPos.ZERO.above()));
            }
            assertTrue(PlacementScope.active(world, BlockPos.ZERO));
            var result = new java.util.concurrent.atomic.AtomicBoolean(true);
            Thread thread = new Thread(() -> result.set(PlacementScope.active(world, BlockPos.ZERO)));
            thread.start(); thread.join();
            assertFalse(result.get());
        }
    }
}
