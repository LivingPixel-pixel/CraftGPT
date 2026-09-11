package dev.craftgpt.client.build.preview;

import dev.craftgpt.client.platform.ModelPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.*;

/** Capture model data on the client thread, rasterize and load texture images on a worker. */
public final class MinecraftModelSnapshot implements BuildVisualSheetRenderer.TextureProvider {
    public record Vertex(float x,float y,float z,float u,float v) { }
    public record Quad(List<Vertex> vertices, Identifier texture, int tint, float shade) { }
    private final Map<String,List<Quad>> models;
    private final ResourceManager resources;
    private final Map<Identifier,BufferedImage> textures=new HashMap<>();
    private MinecraftModelSnapshot(Map<String,List<Quad>> models,ResourceManager resources) {
        this.models=Map.copyOf(models); this.resources=resources;
    }
    /** Immutable geometry fixture for offline renderer regression tests. */
    static MinecraftModelSnapshot fixture(Map<String,List<Quad>> models, Map<Identifier,BufferedImage> textures) {
        var snapshot=new MinecraftModelSnapshot(models,null);
        snapshot.textures.putAll(textures);
        return snapshot;
    }
    public static List<BakedQuad> baked(Minecraft mc,String serialized) {
        return ModelPlatform.baked(mc, serialized);
    }
    public static MinecraftModelSnapshot capture(Minecraft mc,Collection<String> states) {
        Map<String,List<Quad>> models=new HashMap<>();
        for(String serialized:new LinkedHashSet<>(states)) {
            List<Quad> quads=new ArrayList<>();
            for(BakedQuad q:baked(mc,serialized)) {
                var sprite=ModelPlatform.sprite(q);
                List<Vertex> vertices=new ArrayList<>();
                for(int i=0;i<4;i++) {
                    vertices.add(ModelPlatform.vertex(q, i));
                }
                int tint=ModelPlatform.tint(mc, serialized, q);
                float shade=ModelPlatform.shade(q);
                quads.add(new Quad(List.copyOf(vertices),sprite.contents().name(),tint,shade));
            }
            models.put(serialized,List.copyOf(quads));
        }
        return new MinecraftModelSnapshot(models,mc.getResourceManager());
    }
    public List<Quad> quads(String state) {
        var quads=models.getOrDefault(state,List.of());
        if(!quads.isEmpty() || state.equals("minecraft:air") || state.equals("minecraft:cave_air") || state.equals("minecraft:void_air"))return quads;
        return missingGeometry();
    }
    private static List<Quad> missingGeometry() {
        Identifier missing=Identifier.fromNamespaceAndPath("craftgpt","missing_geometry");
        float[][][] faces={
            {{0,1,0},{1,1,0},{1,1,1},{0,1,1}},{{0,0,1},{1,0,1},{1,1,1},{0,1,1}},
            {{1,0,0},{0,0,0},{0,1,0},{1,1,0}},{{1,0,1},{1,0,0},{1,1,0},{1,1,1}},
            {{0,0,0},{0,0,1},{0,1,1},{0,1,0}},{{0,0,0},{1,0,0},{1,0,1},{0,0,1}}
        };
        List<Quad> result=new ArrayList<>();
        for(var face:faces){List<Vertex> vs=new ArrayList<>();for(var p:face)vs.add(new Vertex(p[0],p[1],p[2],0,0));result.add(new Quad(vs,missing,0xffffff,.8f));}
        return result;
    }
    public BufferedImage texture(Identifier name) {
        if(textures.containsKey(name)) return textures.get(name);
        if(resources==null) return null;
        BufferedImage image=null;
        try {
            var resource=resources.getResource(Identifier.fromNamespaceAndPath(name.getNamespace(),"textures/"+name.getPath()+".png"));
            if(resource.isPresent()) try(var stream=resource.get().open()) {
                BufferedImage source=ImageIO.read(stream);
                if(source!=null) image=source.getSubimage(0,0,source.getWidth(),Math.min(source.getWidth(),source.getHeight()));
            }
        } catch(Exception ignored) { }
        textures.put(name,image); return image;
    }
    @Override public BufferedImage texture(String state,BuildVisualSheetRenderer.Face face) { return null; }
}
