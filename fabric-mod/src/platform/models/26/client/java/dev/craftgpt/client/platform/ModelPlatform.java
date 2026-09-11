package dev.craftgpt.client.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.craftgpt.build.server.BuildPreviewValidator;
import dev.craftgpt.client.build.preview.MinecraftModelSnapshot.Vertex;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;

/** Native model access shared by the 26.x builds. */
public final class ModelPlatform {
    private ModelPlatform() {}

    public static Object modelSet(Minecraft mc) { return mc.getModelManager().getBlockStateModelSet(); }

    public static List<BakedQuad> baked(Minecraft mc, String serialized) {
        try {
            var state = BuildPreviewValidator.parseCanonicalState(BuiltInRegistries.BLOCK, serialized);
            var model = mc.getModelManager().getBlockStateModelSet().get(state);
            List<BlockStateModelPart> parts = new ArrayList<>();
            model.collectParts(RandomSource.create(42), parts);
            List<BakedQuad> quads = new ArrayList<>();
            for (var part : parts) {
                quads.addAll(part.getQuads(null));
                for (Direction direction : Direction.values()) quads.addAll(part.getQuads(direction));
            }
            return List.copyOf(quads);
        } catch (Exception e) { return List.of(); }
    }

    public static TextureAtlasSprite sprite(BakedQuad quad) { return quad.materialInfo().sprite(); }

    public static Vertex vertex(BakedQuad quad, int index) {
        var sprite = sprite(quad);
        var position = quad.position(index);
        long uv = quad.packedUV(index);
        return new Vertex(position.x(), position.y(), position.z(),
            (UVPair.unpackU(uv) - sprite.getU0()) / (sprite.getU1() - sprite.getU0()),
            (UVPair.unpackV(uv) - sprite.getV0()) / (sprite.getV1() - sprite.getV0()));
    }

    public static int tint(Minecraft mc, String serialized, BakedQuad quad) {
        if (quad.materialInfo().isTinted()) try {
            var state = BuildPreviewValidator.parseCanonicalState(BuiltInRegistries.BLOCK, serialized);
            return mc.getBlockColors().getTintSource(state, quad.materialInfo().tintIndex()).color(state);
        } catch (Exception ignored) { }
        return 0xFFFFFF;
    }

    public static float shade(BakedQuad quad) {
        return switch (quad.direction()) { case UP -> 1f; case DOWN -> .55f; case NORTH, SOUTH -> .8f; default -> .9f; };
    }

    public static RenderType renderType(BakedQuad quad) {
        return quad.materialInfo().layer().translucent()
            ? RenderTypes.entityTranslucent(sprite(quad).atlasLocation())
            : RenderTypes.entityCutout(sprite(quad).atlasLocation());
    }

    public static void emit(VertexConsumer vertices, PoseStack.Pose pose, BakedQuad quad, int tint) {
        var instance = new QuadInstance();
        instance.setLightCoords(0xF000F0);
        instance.setOverlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        instance.setColor(tint | 0xFF000000);
        instance.scaleColor(shade(quad));
        vertices.putBakedQuad(pose, quad, instance);
    }
}
