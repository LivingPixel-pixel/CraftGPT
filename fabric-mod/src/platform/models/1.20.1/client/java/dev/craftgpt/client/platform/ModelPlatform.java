package dev.craftgpt.client.platform;

import com.mojang.blaze3d.vertex.PoseStack;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.craftgpt.build.server.BuildPreviewValidator;
import dev.craftgpt.client.build.preview.MinecraftModelSnapshot.Vertex;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;

/** Native baked-model access for Minecraft 1.20.1. */
public final class ModelPlatform {
    private ModelPlatform() {}

    public static Object modelSet(Minecraft mc) { return mc.getBlockRenderer().getBlockModel(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()); }

    public static List<BakedQuad> baked(Minecraft mc, String serialized) {
        try {
            var state = BuildPreviewValidator.parseCanonicalState(BuiltInRegistries.BLOCK, serialized);
            var model = mc.getBlockRenderer().getBlockModel(state);
            List<BakedQuad> quads = new ArrayList<>();
            quads.addAll(model.getQuads(state, null, RandomSource.create(42)));
            for (Direction direction : Direction.values()) quads.addAll(model.getQuads(state, direction, RandomSource.create(42)));
            return List.copyOf(quads);
        } catch (Exception e) { return List.of(); }
    }

    public static TextureAtlasSprite sprite(BakedQuad quad) { return quad.getSprite(); }

    public static Vertex vertex(BakedQuad quad, int index) {
        var sprite = sprite(quad);
        int[] data = quad.getVertices();
        int offset = index * 8;
        return new Vertex(Float.intBitsToFloat(data[offset]), Float.intBitsToFloat(data[offset+1]), Float.intBitsToFloat(data[offset+2]),
            (Float.intBitsToFloat(data[offset+4]) - sprite.getU0()) / (sprite.getU1() - sprite.getU0()),
            (Float.intBitsToFloat(data[offset+5]) - sprite.getV0()) / (sprite.getV1() - sprite.getV0()));
    }

    public static int tint(Minecraft mc, String serialized, BakedQuad quad) {
        if (quad.isTinted()) try {
            var state = BuildPreviewValidator.parseCanonicalState(BuiltInRegistries.BLOCK, serialized);
            return mc.getBlockColors().getColor(state, null, null, quad.getTintIndex());
        } catch (Exception ignored) { }
        return 0xFFFFFF;
    }

    public static float shade(BakedQuad quad) {
        return switch (quad.getDirection()) { case UP -> 1f; case DOWN -> .55f; case NORTH, SOUTH -> .8f; default -> .9f; };
    }

    public static RenderType renderType(BakedQuad quad) {
        return RenderTypes.entityTranslucent(sprite(quad).atlasLocation());
    }

    public static void emit(VertexConsumer vertices, PoseStack.Pose pose, BakedQuad quad, int tint) {
        float shade = shade(quad);
        vertices.putBulkData(pose, quad, ((tint >> 16) & 255) / 255f * shade,
            ((tint >> 8) & 255) / 255f * shade, (tint & 255) / 255f * shade,
            0xF000F0, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
    }
}
