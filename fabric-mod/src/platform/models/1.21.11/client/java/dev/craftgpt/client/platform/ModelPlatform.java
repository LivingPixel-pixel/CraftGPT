package dev.craftgpt.client.platform;

import com.mojang.blaze3d.vertex.PoseStack;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.craftgpt.build.server.BuildPreviewValidator;
import dev.craftgpt.client.build.preview.MinecraftModelSnapshot.Vertex;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;

/** Native baked-model access for Minecraft 1.21.11. */
public final class ModelPlatform {
    private ModelPlatform() {}

    public static Object modelSet(Minecraft mc) { return mc.getBlockRenderer().getBlockModel(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()); }

    public static List<BakedQuad> baked(Minecraft mc, String serialized) {
        try {
            var state = BuildPreviewValidator.parseCanonicalState(BuiltInRegistries.BLOCK, serialized);
            var model = mc.getBlockRenderer().getBlockModel(state);
            List<BlockModelPart> parts = new ArrayList<>();
            model.collectParts(RandomSource.create(42), parts);
            List<BakedQuad> quads = new ArrayList<>();
            for (var part : parts) {
                quads.addAll(part.getQuads(null));
                for (Direction direction : Direction.values()) quads.addAll(part.getQuads(direction));
            }
            return List.copyOf(quads);
        } catch (Exception e) { return List.of(); }
    }

    public static TextureAtlasSprite sprite(BakedQuad quad) { return quad.sprite(); }

    public static Vertex vertex(BakedQuad quad, int index) {
        var sprite = sprite(quad);
        var position = quad.position(index);
        long uv = quad.packedUV(index);
        return new Vertex(position.x(), position.y(), position.z(),
            (UVPair.unpackU(uv) - sprite.getU0()) / (sprite.getU1() - sprite.getU0()),
            (UVPair.unpackV(uv) - sprite.getV0()) / (sprite.getV1() - sprite.getV0()));
    }

    public static int tint(Minecraft mc, String serialized, BakedQuad quad) {
        if (quad.isTinted()) try {
            var state = BuildPreviewValidator.parseCanonicalState(BuiltInRegistries.BLOCK, serialized);
            return mc.getBlockColors().getColor(state, null, null, quad.tintIndex());
        } catch (Exception ignored) { }
        return 0xFFFFFF;
    }

    public static float shade(BakedQuad quad) {
        return switch (quad.direction()) { case UP -> 1f; case DOWN -> .55f; case NORTH, SOUTH -> .8f; default -> .9f; };
    }

    public static RenderType renderType(BakedQuad quad) {
        return RenderTypes.entityTranslucent(sprite(quad).atlasLocation());
    }

    public static void emit(VertexConsumer vertices, PoseStack.Pose pose, BakedQuad quad, int tint) {
        float shade = shade(quad);
        vertices.putBulkData(pose, quad, ((tint >> 16) & 255) / 255f * shade,
            ((tint >> 8) & 255) / 255f * shade, (tint & 255) / 255f * shade, 1f,
            0xF000F0, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
    }
}
