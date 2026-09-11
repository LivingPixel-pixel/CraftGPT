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

/** Native Fabric world-render hooks for Minecraft 1.21.11. */
public final class PreviewPlatform {
    private PreviewPlatform() {}
    public static void lineVertex(VertexConsumer vertices, PoseStack.Pose pose, float x, float y, float z,
            int color, float nx, float ny, float nz, float width) {
        lineWidth(vertices.addVertex(pose, x, y, z).setColor(color).setNormal(pose, nx, ny, nz), width);
    }
    public static RenderType lineType() { return net.minecraft.client.renderer.rendertype.RenderTypes.linesTranslucent(); }
    public static void lineWidth(VertexConsumer vertices, float width) { vertices.setLineWidth(width); }

    public static net.minecraft.world.phys.Vec3 cameraPosition(WorldRenderContext context) {
        return context.worldState().cameraRenderState.pos;
    }
    public static net.minecraft.client.renderer.culling.Frustum frustum(WorldRenderContext context) {
        // This API does not expose the active frustum. Distance and frame caps still apply.
        return null;
    }
    public static PoseStack poses(WorldRenderContext context) { return context.matrices(); }

    public static void register(Consumer<WorldRenderContext> render) {
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(render::accept);
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
