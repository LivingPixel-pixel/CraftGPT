package dev.craftgpt.area;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AreaBoundsTest {
    @Test
    void createsInclusiveBoundsIndependentOfSelectionDirection() {
        AreaBounds bounds = AreaBounds.between(
            new BlockPos(10, 70, 20),
            new BlockPos(4, 64, 12)
        );

        assertEquals(new BlockPos(4, 64, 12), bounds.min());
        assertEquals(new BlockPos(10, 70, 20), bounds.max());
        assertEquals(7, bounds.width());
        assertEquals(7, bounds.height());
        assertEquals(9, bounds.depth());
        assertEquals(441L, bounds.volume());
    }

    @Test
    void singleBlockAreaHasVolumeOne() {
        AreaBounds bounds = AreaBounds.between(BlockPos.ZERO, BlockPos.ZERO);

        assertEquals(1, bounds.width());
        assertEquals(1, bounds.height());
        assertEquals(1, bounds.depth());
        assertEquals(1L, bounds.volume());
    }
}
