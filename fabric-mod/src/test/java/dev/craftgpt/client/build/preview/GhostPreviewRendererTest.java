package dev.craftgpt.client.build.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class GhostPreviewRendererTest {
    private static final AABB BOX = new AABB(10.0, 20.0, 30.0, 12.0, 22.0, 32.0);

    @Test
    void distanceIsZeroInsideBatchBounds() {
        assertEquals(0.0, GhostPreviewRenderer.distanceSquared(BOX, new Vec3(11.0, 21.0, 31.0)));
    }

    @Test
    void distanceUsesClosestPointOnBatchBounds() {
        assertEquals(29.0, GhostPreviewRenderer.distanceSquared(BOX, new Vec3(15.0, 24.0, 36.0)));
    }
}
