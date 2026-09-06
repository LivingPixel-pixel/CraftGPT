package dev.craftgpt.client.build.preview;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Draws immutable block models, with optional wireframes and removal markers. */
final class GhostPreviewRenderer {
    static final int MAX_RENDERED_OPERATIONS = 4_096;
    static final double MAX_RENDER_DISTANCE = 256.0;

    private static final int PLACEMENT_COLOR = 0x9955E6D4;
    private static final int REMOVAL_COLOR = 0xBBFF5555;
    private static final float LINE_WIDTH = 1.15F;
    private static final float REMOVAL_LINE_WIDTH = 1.65F;
    private static final java.util.Set<RenderType> SOLID_TYPES = new java.util.HashSet<>();
    private static Object modelSet;
    private static final java.util.Map<String, java.util.List<net.minecraft.client.resources.model.geometry.BakedQuad>> MODEL_CACHE = new java.util.HashMap<>();
    private GhostPreviewRenderer() {
    }

    static void render(LevelRenderContext context) {
        GhostPreviewManager manager = GhostPreviewManager.INSTANCE;
        PreviewSnapshot snapshot = manager.snapshot();
        Minecraft minecraft = Minecraft.getInstance();

        if (!manager.isVisible() || snapshot.validOperations() == 0 || minecraft.level == null) {
            manager.updateFrameStats(snapshot, false, 0, 0, 0, 0);
            return;
        }

        boolean dimensionMatches = minecraft.level.dimension().identifier().toString()
            .equals(snapshot.dimension());
        if (!dimensionMatches) {
            manager.updateFrameStats(snapshot, false, 0, 0, 0, 0);
            return;
        }

        Vec3 cameraPosition = context.levelState().cameraRenderState.pos;
        if (cameraPosition == null) {
            manager.updateFrameStats(snapshot, true, 0, 0, 0, 0);
            return;
        }

        double effectiveDistance = effectiveRenderDistance(minecraft);
        double maxDistanceSquared = effectiveDistance * effectiveDistance;
        Frustum frustum = context.levelState().cameraRenderState.cullFrustum;
        List<VisibleBatch> visibleBatches = new ArrayList<>(snapshot.batches().size());
        int distanceCulled = 0;
        int frustumCulled = 0;

        for (PreviewBatch batch : snapshot.batches()) {
            double distanceSquared = distanceSquared(batch.bounds(), cameraPosition);
            if (distanceSquared > maxDistanceSquared) {
                distanceCulled += batch.blocks().size();
            } else if (frustum != null && !frustum.isVisible(batch.bounds())) {
                frustumCulled += batch.blocks().size();
            } else {
                visibleBatches.add(new VisibleBatch(batch, distanceSquared));
            }
        }

        // When the frame cap is reached, nearby sections are always preferred.
        visibleBatches.sort(Comparator.comparingDouble(VisibleBatch::distanceSquared));

        PoseStack poseStack = context.poseStack();
        RenderType renderType = RenderTypes.linesTranslucent();
        VertexConsumer vertices = context.bufferSource().getBuffer(renderType);
        int rendered = 0;
        int capOmitted = 0;

        poseStack.pushPose();
        try {
            poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
            outer:
            for (VisibleBatch visibleBatch : visibleBatches) {
                for (PreviewBlock block : visibleBatch.batch().blocks()) {
                    if (rendered >= MAX_RENDERED_OPERATIONS) {
                        break outer;
                    }
                    if(!manager.solid() || block.removal() || !renderSolid(context,poseStack,block,minecraft))
                        renderBlock(poseStack, context.bufferSource().getBuffer(renderType), block);
                    rendered++;
                }
            }
        } finally {
            poseStack.popPose();
            // This buffer is added after vanilla's translucent features; flush it here.
            context.bufferSource().endBatch(renderType);
            for(var type:SOLID_TYPES) context.bufferSource().endBatch(type);
            SOLID_TYPES.clear();
        }

        int visibleOperations = visibleBatches.stream()
            .mapToInt(batch -> batch.batch().blocks().size())
            .sum();
        capOmitted = Math.max(0, visibleOperations - rendered);
        manager.updateFrameStats(
            snapshot,
            true,
            rendered,
            distanceCulled,
            frustumCulled,
            capOmitted
        );
    }

    private static boolean renderSolid(LevelRenderContext context,PoseStack poses,PreviewBlock block,Minecraft mc) {
        Object current=mc.getModelManager().getBlockStateModelSet();
        if(current!=modelSet) {MODEL_CACHE.clear();modelSet=current;}
        var quads=MODEL_CACHE.computeIfAbsent(block.state(),s->MinecraftModelSnapshot.baked(mc,s));
        if(quads.isEmpty())return false;
        poses.pushPose(); poses.translate(block.x(),block.y(),block.z());
        com.mojang.blaze3d.vertex.QuadInstance instance=new com.mojang.blaze3d.vertex.QuadInstance();
        instance.setLightCoords(0xF000F0);
        instance.setOverlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        for(var quad:quads) {
            int tint=0xFFFFFFFF;
            if(quad.materialInfo().isTinted()) try {
                var state=dev.craftgpt.build.server.BuildPreviewValidator.parseCanonicalState(net.minecraft.core.registries.BuiltInRegistries.BLOCK,block.state());
                tint=mc.getBlockColors().getTintSource(state,quad.materialInfo().tintIndex()).color(state)|0xFF000000;
            } catch(Exception ignored) { }
            instance.setColor(tint);
            instance.scaleColor(switch(quad.direction()){case UP->1f;case DOWN->.55f;case NORTH,SOUTH->.8f;default->.9f;});
            var type=quad.materialInfo().layer().translucent()
                ? RenderTypes.entityTranslucent(quad.materialInfo().sprite().atlasLocation())
                : RenderTypes.entityCutout(quad.materialInfo().sprite().atlasLocation());
            SOLID_TYPES.add(type);
            context.bufferSource().getBuffer(type).putBakedQuad(poses.last(),quad,instance);
        }
        poses.popPose();
        return true;
    }

    private static void renderBlock(PoseStack poseStack, VertexConsumer vertices, PreviewBlock block) {
        if (block.removal()) {
            renderBox(poseStack, vertices, block, 0.11F, REMOVAL_COLOR, REMOVAL_LINE_WIDTH);
            renderRemovalDiagonals(poseStack, vertices, block);
        } else {
            renderBox(poseStack, vertices, block, 0.035F, PLACEMENT_COLOR, LINE_WIDTH);
        }
    }

    /** Emits the twelve box edges directly, avoiding per-edge vector allocations every frame. */
    private static void renderBox(
        PoseStack poseStack,
        VertexConsumer vertices,
        PreviewBlock block,
        float inset,
        int color,
        float lineWidth
    ) {
        float x0 = block.x() + inset;
        float y0 = block.y() + inset;
        float z0 = block.z() + inset;
        float x1 = block.x() + 1.0F - inset;
        float y1 = block.y() + 1.0F - inset;
        float z1 = block.z() + 1.0F - inset;

        line(poseStack, vertices, x0, y0, z0, x1, y0, z0, color, lineWidth);
        line(poseStack, vertices, x0, y1, z0, x1, y1, z0, color, lineWidth);
        line(poseStack, vertices, x0, y0, z1, x1, y0, z1, color, lineWidth);
        line(poseStack, vertices, x0, y1, z1, x1, y1, z1, color, lineWidth);

        line(poseStack, vertices, x0, y0, z0, x0, y1, z0, color, lineWidth);
        line(poseStack, vertices, x1, y0, z0, x1, y1, z0, color, lineWidth);
        line(poseStack, vertices, x0, y0, z1, x0, y1, z1, color, lineWidth);
        line(poseStack, vertices, x1, y0, z1, x1, y1, z1, color, lineWidth);

        line(poseStack, vertices, x0, y0, z0, x0, y0, z1, color, lineWidth);
        line(poseStack, vertices, x1, y0, z0, x1, y0, z1, color, lineWidth);
        line(poseStack, vertices, x0, y1, z0, x0, y1, z1, color, lineWidth);
        line(poseStack, vertices, x1, y1, z0, x1, y1, z1, color, lineWidth);
    }

    private static void renderRemovalDiagonals(
        PoseStack poseStack,
        VertexConsumer vertices,
        PreviewBlock block
    ) {
        float lowX = block.x() + 0.12F;
        float lowY = block.y() + 0.12F;
        float lowZ = block.z() + 0.12F;
        float highX = block.x() + 0.88F;
        float highY = block.y() + 0.88F;
        float highZ = block.z() + 0.88F;

        line(poseStack, vertices, lowX, lowY, lowZ, highX, highY, highZ,
            REMOVAL_COLOR, REMOVAL_LINE_WIDTH);
        line(poseStack, vertices, highX, lowY, lowZ, lowX, highY, highZ,
            REMOVAL_COLOR, REMOVAL_LINE_WIDTH);
        line(poseStack, vertices, lowX, highY, lowZ, highX, lowY, highZ,
            REMOVAL_COLOR, REMOVAL_LINE_WIDTH);
        line(poseStack, vertices, highX, highY, lowZ, lowX, lowY, highZ,
            REMOVAL_COLOR, REMOVAL_LINE_WIDTH);
    }

    private static void line(
        PoseStack poseStack,
        VertexConsumer vertices,
        float fromX,
        float fromY,
        float fromZ,
        float toX,
        float toY,
        float toZ,
        int color,
        float lineWidth
    ) {
        float dx = toX - fromX;
        float dy = toY - fromY;
        float dz = toZ - fromZ;
        float inverseLength = 1.0F / (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float normalX = dx * inverseLength;
        float normalY = dy * inverseLength;
        float normalZ = dz * inverseLength;
        PoseStack.Pose pose = poseStack.last();

        vertices.addVertex(pose, fromX, fromY, fromZ)
            .setColor(color)
            .setNormal(pose, normalX, normalY, normalZ)
            .setLineWidth(lineWidth);
        vertices.addVertex(pose, toX, toY, toZ)
            .setColor(color)
            .setNormal(pose, normalX, normalY, normalZ)
            .setLineWidth(lineWidth);
    }

    private static double effectiveRenderDistance(Minecraft minecraft) {
        double configured = minecraft.options.getEffectiveRenderDistance() * 16.0 + 16.0;
        return Math.min(MAX_RENDER_DISTANCE, Math.max(48.0, configured));
    }

    static double distanceSquared(AABB bounds, Vec3 point) {
        double x = Math.max(bounds.minX, Math.min(point.x, bounds.maxX));
        double y = Math.max(bounds.minY, Math.min(point.y, bounds.maxY));
        double z = Math.max(bounds.minZ, Math.min(point.z, bounds.maxZ));
        double dx = point.x - x;
        double dy = point.y - y;
        double dz = point.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private record VisibleBatch(PreviewBatch batch, double distanceSquared) {
    }
}
