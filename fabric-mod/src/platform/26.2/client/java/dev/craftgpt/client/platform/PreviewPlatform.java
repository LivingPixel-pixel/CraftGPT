package dev.craftgpt.client.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.rendertype.RenderType;

/** Submit geometry before the 26.2 renderer consumes its feature queue. */
public final class PreviewPlatform {
    private PreviewPlatform() {}
    public static void lineVertex(VertexConsumer vertices, PoseStack.Pose pose, float x, float y, float z,
            int color, float nx, float ny, float nz, float width) {
        lineWidth(vertices.addVertex(pose, x, y, z).setColor(color).setNormal(pose, nx, ny, nz), width);
    }
    public static RenderType lineType() { return net.minecraft.client.renderer.rendertype.RenderTypes.linesTranslucent(); }
    public static void lineWidth(VertexConsumer vertices, float width) { vertices.setLineWidth(width); }

    public static net.minecraft.world.phys.Vec3 cameraPosition(LevelRenderContext context) {
        return context.levelState().cameraRenderState.pos;
    }
    public static net.minecraft.client.renderer.culling.Frustum frustum(LevelRenderContext context) {
        return context.levelState().cameraRenderState.cullFrustum;
    }
    public static PoseStack poses(LevelRenderContext context) { return context.poseStack(); }

    public static void register(Consumer<LevelRenderContext> render) {
        LevelRenderEvents.COLLECT_SUBMITS.register(render::accept);
    }

    public static void draw(LevelRenderContext context, PoseStack poses, RenderType type,
                            BiConsumer<PoseStack.Pose, VertexConsumer> geometry) {
        context.submitNodeCollector().submitCustomGeometry(poses, type, geometry::accept);
    }

    public static void finish(LevelRenderContext context, Set<RenderType> types) {
        // Vanilla owns and flushes the submitted geometry buffers.
    }
}
