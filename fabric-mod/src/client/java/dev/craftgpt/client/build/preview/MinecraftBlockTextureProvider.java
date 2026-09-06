package dev.craftgpt.client.build.preview;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Loads block textures from the active Minecraft resource-pack stack. */
public final class MinecraftBlockTextureProvider implements BuildVisualSheetRenderer.TextureProvider {
    private static final int NORMALIZED_SIZE = 16;

    private final ResourceManager resources;
    private final Map<TextureKey, Optional<BufferedImage>> cache = new HashMap<>();

    public MinecraftBlockTextureProvider(ResourceManager resources) {
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    @Override
    public BufferedImage texture(String state, BuildVisualSheetRenderer.Face face) {
        return cache.computeIfAbsent(
            new TextureKey(state, face),
            key -> resolve(key.state(), key.face())
        ).orElse(null);
    }

    private Optional<BufferedImage> resolve(String state, BuildVisualSheetRenderer.Face face) {
        ParsedBlock parsed = ParsedBlock.parse(state);
        for (String candidate : candidates(parsed.path(), parsed.properties(), face)) {
            Identifier identifier = Identifier.fromNamespaceAndPath(
                parsed.namespace(),
                "textures/block/" + candidate + ".png"
            );
            Optional<Resource> resource = resources.getResource(identifier);
            if (resource.isEmpty()) continue;
            try (InputStream input = resource.get().open()) {
                BufferedImage image = ImageIO.read(input);
                if (image != null) return Optional.of(normalizeFirstFrame(image));
            } catch (IOException ignored) {
                // A missing or unreadable resource-pack entry falls back to a material color.
            }
        }
        return Optional.empty();
    }

    private static List<String> candidates(String path, Map<String, String> properties, BuildVisualSheetRenderer.Face face) {
        List<String> result = new ArrayList<>();
        if (path.endsWith("_door")) {
            result.add(path + ("upper".equals(properties.get("half")) ? "_top" : "_bottom"));
        }
        if (path.equals("grass_block")) {
            result.add(face == BuildVisualSheetRenderer.Face.TOP ? "grass_block_top"
                : face == BuildVisualSheetRenderer.Face.BOTTOM ? "dirt" : "grass_block_side");
        }
        if (path.equals("dirt_path")) {
            result.add(face == BuildVisualSheetRenderer.Face.TOP ? "dirt_path_top"
                : face == BuildVisualSheetRenderer.Face.BOTTOM ? "dirt" : "dirt_path_side");
        }
        if (path.endsWith("_log") || path.endsWith("_stem")) {
            result.add(face == BuildVisualSheetRenderer.Face.SIDE ? path : path + "_top");
        }
        if (path.endsWith("_wood") || path.endsWith("_hyphae")) result.add(path);
        if (path.endsWith("_glass_pane")) result.add(path.substring(0, path.length() - "_pane".length()));
        if (path.equals("glass_pane")) result.add("glass");

        String base = shapeBase(path);
        result.add(base);
        if (!base.equals(path)) result.add(path);
        if (base.equals("stone_brick")) result.add("stone_bricks");
        if (base.equals("brick")) result.add("bricks");
        if (base.equals("nether_brick")) result.add("nether_bricks");
        if (base.equals("quartz")) {
            result.add(face == BuildVisualSheetRenderer.Face.SIDE ? "quartz_block_side" : "quartz_block_top");
        }
        if (base.equals("smooth_quartz")) {
            result.add(face == BuildVisualSheetRenderer.Face.BOTTOM ? "quartz_block_bottom" : "quartz_block_top");
        }
        return result.stream().distinct().toList();
    }

    private static String shapeBase(String path) {
        for (String suffix : List.of("_stairs", "_slab", "_wall", "_fence", "_fence_gate")) {
            if (path.endsWith(suffix)) return path.substring(0, path.length() - suffix.length());
        }
        return path;
    }

    private static BufferedImage normalizeFirstFrame(BufferedImage source) {
        int frameSize = Math.min(source.getWidth(), source.getHeight());
        BufferedImage normalized = new BufferedImage(NORMALIZED_SIZE, NORMALIZED_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = normalized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.drawImage(source, 0, 0, NORMALIZED_SIZE, NORMALIZED_SIZE, 0, 0, frameSize, frameSize, null);
        } finally {
            graphics.dispose();
        }
        return normalized;
    }

    private record TextureKey(String state, BuildVisualSheetRenderer.Face face) {
    }

    private record ParsedBlock(String namespace, String path, Map<String, String> properties) {
        static ParsedBlock parse(String state) {
            String safe = state == null ? "minecraft:missing" : state.trim().toLowerCase(Locale.ROOT);
            int propertyStart = safe.indexOf('[');
            String identifier = propertyStart < 0 ? safe : safe.substring(0, propertyStart);
            int separator = identifier.indexOf(':');
            String namespace = separator < 0 ? "minecraft" : identifier.substring(0, separator);
            String path = separator < 0 ? identifier : identifier.substring(separator + 1);
            Map<String, String> properties = new HashMap<>();
            if (propertyStart >= 0 && safe.endsWith("]")) {
                String raw = safe.substring(propertyStart + 1, safe.length() - 1);
                for (String entry : raw.split(",")) {
                    int equals = entry.indexOf('=');
                    if (equals > 0) properties.put(entry.substring(0, equals), entry.substring(equals + 1));
                }
            }
            return new ParsedBlock(namespace, path, Map.copyOf(properties));
        }
    }
}
