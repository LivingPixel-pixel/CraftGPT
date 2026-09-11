package dev.craftgpt.client.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;

/** Native Fabric world-render hooks for Minecraft 1.20.1. */
public final class PreviewPlatform {
    private PreviewPlatform() {}
    public static void lineVertex(VertexConsumer vertices, PoseStack.Pose pose, float x, float y, float z,
            int color, float nx, float ny, float nz, float width) {
        vertices.vertex(pose.pose(), x, y, z).color(color).normal(pose.normal(), nx, ny, nz).endVertex();
    }
    public static RenderType lineType() { return RenderType.lines(); }
    public static void lineWidth(VertexConsumer vertices, float width) { /* This renderer uses pipeline line width. */ }

    public static net.minecraft.world.phys.Vec3 cameraPosition(WorldRenderContext context) {
        return context.camera().getPosition();
    }
    public static net.minecraft.client.renderer.culling.Frustum frustum(WorldRenderContext context) {
        return context.frustum();
    }
    public static PoseStack poses(WorldRenderContext context) { return context.matrixStack(); }

    public static void register(Consumer<WorldRenderContext> render) {
        // Fabric only guarantees consumer buffers through BEFORE_DEBUG_RENDER on this version.
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(render::accept);
    }

    public static void draw(WorldRenderContext context, PoseStack poses, RenderType type,
                            BiConsumer<PoseStack.Pose, VertexConsumer> geometry) {
        geometry.accept(poses.last(), context.consumers().getBuffer(type));
    }

    public static void finish(WorldRenderContext context, Set<RenderType> types) {
        if (context.consumers() instanceof MultiBufferSource.BufferSource buffers) {
            types.forEach(buffers::endBatch);
        }
    }
}
