package dev.craftgpt.client.build.preview;

import dev.craftgpt.build.model.BuildOperation;
import dev.craftgpt.build.model.CompiledBuildArtifact;
import dev.craftgpt.context.AreaContext;
import dev.craftgpt.context.ExactBlockContext;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Creates deterministic multi-view sheets without moving the Minecraft camera or player. */
public final class BuildVisualSheetRenderer {
    static final int IMAGE_WIDTH = 1_680;
    static final int IMAGE_HEIGHT = 1_100;
    private static final int HEADER_HEIGHT = 74;
    private static final int PANEL_GAP = 12;
    private static final Color BACKGROUND = new Color(12, 16, 24);
    private static final Color PANEL_BACKGROUND = new Color(24, 31, 43);
    private static final Color PANEL_BORDER = new Color(69, 84, 105);
    private static final Color TEXT = new Color(235, 241, 250);
    private static final Color SUBTEXT = new Color(158, 174, 196);

    private BuildVisualSheetRenderer() {
    }

    public enum Face {
        TOP,
        SIDE,
        BOTTOM
    }

    @FunctionalInterface
    public interface TextureProvider {
        TextureProvider NONE = (state, face) -> null;

        BufferedImage texture(String state, Face face);
    }

    public static void render(
        Path output,
        CompiledBuildArtifact artifact,
        AreaContext context
    ) throws IOException {
        render(output, artifact, context, TextureProvider.NONE);
    }

    public static void render(
        Path output,
        CompiledBuildArtifact artifact,
        AreaContext context,
        TextureProvider textures
    ) throws IOException {
        validateInput(output, artifact, context, textures);
        VisualScene scene = createScene(artifact, context);
        renderSheet(
            output,
            artifact,
            scene,
            textures,
            "Six-view overview",
            List.of(
                new View("SOUTH-EAST EXTERIOR", 0, Cutaway.NONE),
                new View("SOUTH-WEST EXTERIOR", 1, Cutaway.NONE),
                new View("NORTH-WEST EXTERIOR", 2, Cutaway.NONE),
                new View("NORTH-EAST EXTERIOR", 3, Cutaway.NONE),
                new View("CENTER CUTAWAY FROM SOUTH", 0, Cutaway.SOUTH),
                new View("CENTER CUTAWAY FROM EAST", 3, Cutaway.EAST)
            ),
            3
        );
    }

    /** Renders three large contact sheets with twelve complementary views. */
    public static List<Path> renderInspectionSet(
        Path output,
        CompiledBuildArtifact artifact,
        AreaContext context,
        TextureProvider textures
    ) throws IOException {
        validateInput(output, artifact, context, textures);
        VisualScene scene = createScene(artifact, context);
        String fileName = output.getFileName().toString();
        String stem = fileName.toLowerCase(Locale.ROOT).endsWith(".png")
            ? fileName.substring(0, fileName.length() - 4)
            : fileName;
        List<Sheet> sheets = List.of(
            new Sheet("Exterior ring", "exterior", List.of(
                new View("SOUTH-EAST EXTERIOR", 0, Cutaway.NONE),
                new View("SOUTH-WEST EXTERIOR", 1, Cutaway.NONE),
                new View("NORTH-WEST EXTERIOR", 2, Cutaway.NONE),
                new View("NORTH-EAST EXTERIOR", 3, Cutaway.NONE)
            )),
            new Sheet("Interior cutaways", "cutaways", List.of(
                new View("CENTER CUTAWAY FROM SOUTH", 0, Cutaway.SOUTH),
                new View("CENTER CUTAWAY FROM EAST", 3, Cutaway.EAST),
                new View("CENTER CUTAWAY FROM NORTH", 2, Cutaway.NORTH),
                new View("CENTER CUTAWAY FROM WEST", 1, Cutaway.WEST)
            )),
            new Sheet("Structural sections", "sections", List.of(
                new View("ROOFLESS UPPER OVERVIEW", 0, Cutaway.ROOFLESS),
                new View("LOWER LEVEL FROM SOUTH", 0, Cutaway.LOWER_SOUTH),
                new View("UPPER LEVEL FROM SOUTH", 0, Cutaway.UPPER_SOUTH),
                new View("VERTICAL CENTER CORE", 0, Cutaway.CENTER_CORE)
            ))
        );
        List<Path> outputs = new ArrayList<>();
        for (Sheet sheet : sheets) {
            Path page = output.resolveSibling(stem + "-" + sheet.fileSuffix() + ".png");
            renderSheet(page, artifact, scene, textures, sheet.title(), sheet.views(), 2);
            outputs.add(page);
        }
        return List.copyOf(outputs);
    }

    /** Render cropped, bounded camera requests. No player movement is needed. */
    public static List<Path> renderRequested(Path output, CompiledBuildArtifact artifact, AreaContext context,
        MinecraftModelSnapshot models, List<dev.craftgpt.client.portable.ReviewDecision.Camera> cameras) throws IOException {
        validateInput(output,artifact,context,models);
        new dev.craftgpt.client.portable.ReviewDecision("inspect","views",List.of(),cameras)
            .validate("views",context.width(),context.height(),context.depth());
        VisualScene scene=createScene(artifact,context);
        List<Path> paths=new ArrayList<>();
        if(cameras.size()>4) throw new IllegalArgumentException("too_many_views");
        for(int i=0;i<cameras.size();i++) {
            var c=cameras.get(i);
            List<SceneBlock> blocks=scene.blocks().stream().filter(block->{
                var p=block.coordinate();
                return p.x()>=c.from().get(0)&&p.y()>=c.from().get(1)&&p.z()>=c.from().get(2)
                    &&p.x()<=c.to().get(0)&&p.y()<=c.to().get(1)&&p.z()<=c.to().get(2);
            }).toList();
            Path path=output.resolveSibling("requested-"+i+".png");
            renderSheet(path,artifact,new VisualScene(scene.bounds(),blocks,scene.operationCount()),models,
                "Artificial section "+c.from()+" to "+c.to(),List.of(new View(c.label(),c.rotation(),Cutaway.NONE)),1);
            paths.add(path);
        }
        return List.copyOf(paths);
    }

    private static void validateInput(
        Path output,
        CompiledBuildArtifact artifact,
        AreaContext context,
        TextureProvider textures
    ) {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(artifact, "artifact");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(textures, "textures");
        if (!context.exactBlocks().complete() || artifact.operations().isEmpty()) {
            throw new IllegalArgumentException("visual_sheet_context_unavailable");
        }
    }

    private static void renderSheet(
        Path output,
        CompiledBuildArtifact artifact,
        VisualScene scene,
        TextureProvider textures,
        String sheetTitle,
        List<View> views,
        int columns
    ) throws IOException {
        BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(BACKGROUND);
            graphics.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
            drawHeader(graphics, artifact, scene, sheetTitle);
            if(textures instanceof MinecraftModelSnapshot) {
                graphics.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,12));
                graphics.setColor(SUBTEXT);
                graphics.drawString("Neutral light | first texture frame | magenta means unavailable model/texture, not an intended block",20,71);
            }

            int rows = (int) Math.ceil(views.size() / (double) columns);
            int panelWidth = (IMAGE_WIDTH - PANEL_GAP * (columns + 1)) / columns;
            int panelHeight = (IMAGE_HEIGHT - HEADER_HEIGHT - PANEL_GAP * (rows + 1)) / rows;
            for (int index = 0; index < views.size(); index++) {
                int column = index % columns;
                int row = index / columns;
                int x = PANEL_GAP + column * (panelWidth + PANEL_GAP);
                int y = HEADER_HEIGHT + PANEL_GAP + row * (panelHeight + PANEL_GAP);
                drawPanel(graphics, scene, views.get(index), x, y, panelWidth, panelHeight, textures);
            }
        } finally {
            graphics.dispose();
        }

        Files.createDirectories(output.toAbsolutePath().normalize().getParent());
        if (!ImageIO.write(image, "png", output.toFile())) {
            throw new IOException("PNG writer unavailable");
        }
    }

    private static VisualScene createScene(CompiledBuildArtifact artifact, AreaContext context) {
        Map<Coordinate, VisualBlock> blocks = new HashMap<>();
        ExactBlockContext exact = context.exactBlocks();
        if (!exact.blocks().isBlank()) {
            for (String compact : exact.blocks().split(";")) {
                String[] values = compact.split(",", -1);
                if (values.length != 4) continue;
                try {
                    int x = Integer.parseInt(values[0]);
                    int y = Integer.parseInt(values[1]);
                    int z = Integer.parseInt(values[2]);
                    int paletteIndex = Integer.parseInt(values[3]);
                    if (paletteIndex >= 0 && paletteIndex < exact.palette().size()) {
                        blocks.put(
                            new Coordinate(x, y, z),
                            new VisualBlock(exact.palette().get(paletteIndex), false)
                        );
                    }
                } catch (NumberFormatException ignored) {
                    // The authoritative context validator handles malformed compact values.
                }
            }
        }

        Bounds focus = Bounds.empty();
        for (BuildOperation operation : artifact.operations()) {
            Coordinate coordinate = new Coordinate(
                operation.relativeX(), operation.relativeY(), operation.relativeZ()
            );
            focus = focus.include(coordinate);
            String state = artifact.palette().get(operation.paletteIndex());
            if ("minecraft:air".equals(state)) {
                blocks.remove(coordinate);
            } else {
                blocks.put(coordinate, new VisualBlock(state, true));
            }
        }
        if (!focus.valid()) throw new IllegalArgumentException("visual_sheet_build_empty");

        Bounds expanded = focus.expand(context.width(), context.height(), context.depth());
        List<SceneBlock> visible = blocks.entrySet().stream()
            .filter(entry -> expanded.contains(entry.getKey()))
            .map(entry -> new SceneBlock(entry.getKey(), entry.getValue()))
            .toList();
        return new VisualScene(expanded, visible, artifact.operations().size());
    }

    private static void drawHeader(
        Graphics2D graphics,
        CompiledBuildArtifact artifact,
        VisualScene scene,
        String sheetTitle
    ) {
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 25));
        graphics.setColor(TEXT);
        graphics.drawString("CraftGPT visual inspection: " + sheetTitle, 20, 31);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        graphics.setColor(SUBTEXT);
        String summary = artifact.summary().replace('\n', ' ').replace('\r', ' ');
        if (summary.length() > 110) summary = summary.substring(0, 110);
        graphics.drawString(
            summary + "  |  " + scene.operationCount() + " generated operations  |  active Minecraft textures",
            20,
            56
        );
    }

    private static void drawPanel(
        Graphics2D graphics,
        VisualScene scene,
        View view,
        int panelX,
        int panelY,
        int panelWidth,
        int panelHeight,
        TextureProvider textures
    ) {
        graphics.setColor(PANEL_BACKGROUND);
        graphics.fillRoundRect(panelX, panelY, panelWidth, panelHeight, 12, 12);
        graphics.setColor(PANEL_BORDER);
        graphics.setStroke(new BasicStroke(1.3F));
        graphics.drawRoundRect(panelX, panelY, panelWidth, panelHeight, 12, 12);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        graphics.setColor(TEXT);
        graphics.drawString(view.label(), panelX + 14, panelY + 23);

        Bounds bounds = scene.bounds();
        int width = bounds.width();
        int height = bounds.height();
        int depth = bounds.depth();
        double horizontalSpan = width + depth + 1.0;
        double verticalSpan = height + horizontalSpan / 2.0 + 1.0;
        int tileHeight = (int) Math.floor(Math.min(
            (panelWidth - 30.0) / horizontalSpan,
            (panelHeight - 58.0) / verticalSpan
        ));
        tileHeight = Math.max(2, Math.min(34, tileHeight));
        int tileWidth = tileHeight * 2;
        int originX = panelX + panelWidth / 2;
        int originY = panelY + 37 + (height + 1) * tileHeight;

        List<ProjectedBlock> projected = new ArrayList<>();
        for (SceneBlock block : scene.blocks()) {
            Coordinate coordinate = block.coordinate();
            if (!view.cutaway().includes(coordinate, bounds)) continue;
            Rotated rotated = rotate(coordinate, bounds, view.rotation());
            projected.add(new ProjectedBlock(rotated, block.visual()));
        }
        projected.sort(Comparator
            .comparingInt((ProjectedBlock block) -> block.rotated().x() + block.rotated().z())
            .thenComparingInt(block -> block.rotated().y()));

        for (ProjectedBlock block : projected) {
            if (!(textures instanceof MinecraftModelSnapshot))
                drawCube(graphics, block, originX, originY, tileWidth, tileHeight, textures);
        }

        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        graphics.setColor(SUBTEXT);
        if (textures instanceof MinecraftModelSnapshot models) {
            List<ModelRasterizer.Block> modelBlocks=scene.blocks().stream()
                .filter(block->view.cutaway().includes(block.coordinate(),bounds))
                .map(block->new ModelRasterizer.Block(block.coordinate().x(),block.coordinate().y(),block.coordinate().z(),block.visual().state())).toList();
            BufferedImage rendered=ModelRasterizer.render(panelWidth-20,panelHeight-62,modelBlocks,models,view.rotation());
            graphics.drawImage(rendered,panelX+10,panelY+32,null);
        }
        String caption = view.cutaway().caption();
        graphics.drawString(caption, panelX + 14, panelY + panelHeight - 12);
    }

    private static void drawCube(
        Graphics2D graphics,
        ProjectedBlock block,
        int originX,
        int originY,
        int tileWidth,
        int tileHeight,
        TextureProvider textures
    ) {
        Rotated position = block.rotated();
        int centerX = originX + (position.x() - position.z()) * tileWidth / 2;
        int topY = originY + (position.x() + position.z()) * tileHeight / 2
            - (position.y() + 1) * tileHeight;
        int halfWidth = tileWidth / 2;
        int halfHeight = Math.max(1, tileHeight / 2);
        int vertical = tileHeight;

        Polygon top = polygon(
            centerX, topY,
            centerX + halfWidth, topY + halfHeight,
            centerX, topY + tileHeight,
            centerX - halfWidth, topY + halfHeight
        );
        Polygon left = polygon(
            centerX - halfWidth, topY + halfHeight,
            centerX, topY + tileHeight,
            centerX, topY + tileHeight + vertical,
            centerX - halfWidth, topY + halfHeight + vertical
        );
        Polygon right = polygon(
            centerX + halfWidth, topY + halfHeight,
            centerX, topY + tileHeight,
            centerX, topY + tileHeight + vertical,
            centerX + halfWidth, topY + halfHeight + vertical
        );

        Color base = blockColor(block.visual().state());
        if (!block.visual().generated()) base = shade(base, 0.54F);
        BufferedImage topTexture = textures.texture(block.visual().state(), Face.TOP);
        BufferedImage sideTexture = textures.texture(block.visual().state(), Face.SIDE);
        if (topTexture == null) {
            graphics.setColor(shade(base, 1.15F));
            graphics.fillPolygon(top);
        } else {
            drawTexture(graphics, topTexture, top, 1.05F, !block.visual().generated());
        }
        if (sideTexture == null) {
            graphics.setColor(shade(base, 0.78F));
            graphics.fillPolygon(left);
            graphics.setColor(shade(base, 0.92F));
            graphics.fillPolygon(right);
        } else {
            drawTexture(graphics, sideTexture, left, 0.70F, !block.visual().generated());
            drawTexture(graphics, sideTexture, right, 0.86F, !block.visual().generated());
        }
        graphics.setColor(block.visual().generated()
            ? new Color(8, 11, 16, 190)
            : new Color(82, 94, 112, 100));
        graphics.setStroke(new BasicStroke(block.visual().generated() ? 0.8F : 0.45F));
        graphics.drawPolygon(top);
        graphics.drawPolygon(left);
        graphics.drawPolygon(right);
    }

    private static void drawTexture(
        Graphics2D graphics,
        BufferedImage texture,
        Polygon target,
        float brightness,
        boolean contextBlock
    ) {
        if (texture.getWidth() <= 0 || texture.getHeight() <= 0 || target.npoints < 4) return;
        Shape previousClip = graphics.getClip();
        Object interpolation = graphics.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        try {
            graphics.clip(target);
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
            );
            double sourceWidth = texture.getWidth();
            double sourceHeight = texture.getHeight();
            AffineTransform transform = new AffineTransform(
                (target.xpoints[1] - target.xpoints[0]) / sourceWidth,
                (target.ypoints[1] - target.ypoints[0]) / sourceWidth,
                (target.xpoints[3] - target.xpoints[0]) / sourceHeight,
                (target.ypoints[3] - target.ypoints[0]) / sourceHeight,
                target.xpoints[0],
                target.ypoints[0]
            );
            graphics.drawImage(texture, transform, null);
            int shadeAlpha = Math.min(215, Math.max(0, Math.round((1.0F - brightness) * 210.0F)));
            if (shadeAlpha > 0) {
                graphics.setColor(new Color(0, 0, 0, shadeAlpha));
                graphics.fillPolygon(target);
            } else if (brightness > 1.0F) {
                int highlightAlpha = Math.min(45, Math.round((brightness - 1.0F) * 255.0F));
                graphics.setColor(new Color(255, 255, 255, highlightAlpha));
                graphics.fillPolygon(target);
            }
            if (contextBlock) {
                graphics.setColor(new Color(12, 18, 28, 105));
                graphics.fillPolygon(target);
            }
        } finally {
            graphics.setClip(previousClip);
            if (interpolation != null) graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
        }
    }

    private static Polygon polygon(int... values) {
        int points = values.length / 2;
        int[] x = new int[points];
        int[] y = new int[points];
        for (int index = 0; index < points; index++) {
            x[index] = values[index * 2];
            y[index] = values[index * 2 + 1];
        }
        return new Polygon(x, y, points);
    }

    private static Rotated rotate(Coordinate coordinate, Bounds bounds, int rotation) {
        int x = coordinate.x() - bounds.minX();
        int y = coordinate.y() - bounds.minY();
        int z = coordinate.z() - bounds.minZ();
        return switch (rotation) {
            case 1 -> new Rotated(z, y, bounds.width() - 1 - x);
            case 2 -> new Rotated(bounds.width() - 1 - x, y, bounds.depth() - 1 - z);
            case 3 -> new Rotated(bounds.depth() - 1 - z, y, x);
            default -> new Rotated(x, y, z);
        };
    }

    private static Color blockColor(String state) {
        String id = state == null ? "" : state.toLowerCase(Locale.ROOT);
        int properties = id.indexOf('[');
        if (properties >= 0) id = id.substring(0, properties);
        if (id.contains("black")) return new Color(35, 37, 43);
        if (id.contains("light_gray")) return new Color(151, 151, 143);
        if (id.contains("gray")) return new Color(80, 84, 88);
        if (id.contains("white") || id.contains("snow") || id.contains("quartz")) return new Color(224, 229, 226);
        if (id.contains("light_blue") || id.contains("ice")) return new Color(116, 178, 221);
        if (id.contains("blue")) return new Color(55, 82, 166);
        if (id.contains("cyan")) return new Color(44, 139, 145);
        if (id.contains("purple")) return new Color(118, 62, 162);
        if (id.contains("magenta")) return new Color(183, 76, 171);
        if (id.contains("pink") || id.contains("cherry")) return new Color(219, 141, 159);
        if (id.contains("red") || id.contains("nether_brick")) return new Color(157, 55, 51);
        if (id.contains("orange") || id.contains("copper")) return new Color(193, 105, 59);
        if (id.contains("yellow") || id.contains("gold")) return new Color(220, 181, 58);
        if (id.contains("lime")) return new Color(112, 181, 56);
        if (id.contains("green") || id.contains("leaves") || id.contains("moss")) return new Color(65, 126, 69);
        if (id.contains("glass")) return new Color(139, 198, 210, 175);
        if (id.contains("spruce") || id.contains("dark_oak")) return new Color(82, 57, 37);
        if (id.contains("oak") || id.contains("wood") || id.contains("planks") || id.contains("log")) {
            return new Color(151, 108, 63);
        }
        if (id.contains("birch") || id.contains("sand") || id.contains("end_stone")) return new Color(211, 196, 143);
        if (id.contains("brick")) return new Color(156, 86, 72);
        if (id.contains("deepslate") || id.contains("blackstone")) return new Color(55, 57, 63);
        if (id.contains("stone") || id.contains("andesite") || id.contains("cobble")) return new Color(119, 120, 119);
        if (id.contains("dirt") || id.contains("mud")) return new Color(116, 82, 56);
        int hash = id.hashCode();
        float hue = (hash & 0xFFFF) / 65535.0F;
        return Color.getHSBColor(hue, 0.38F, 0.72F);
    }

    private static Color shade(Color color, float factor) {
        return new Color(
            Math.min(255, Math.max(0, Math.round(color.getRed() * factor))),
            Math.min(255, Math.max(0, Math.round(color.getGreen() * factor))),
            Math.min(255, Math.max(0, Math.round(color.getBlue() * factor))),
            color.getAlpha()
        );
    }

    private enum Cutaway {
        NONE {
            @Override boolean includes(Coordinate coordinate, Bounds bounds) { return true; }
            @Override String caption() { return "Complete exterior from this side"; }
        },
        SOUTH {
            @Override boolean includes(Coordinate coordinate, Bounds bounds) {
                return coordinate.z() <= (bounds.minZ() + bounds.maxZ()) / 2;
            }
            @Override String caption() { return "South half removed to expose rooms and center structure"; }
        },
        EAST {
            @Override boolean includes(Coordinate coordinate, Bounds bounds) {
                return coordinate.x() <= (bounds.minX() + bounds.maxX()) / 2;
            }
            @Override String caption() { return "East half removed to expose rooms and center structure"; }
        },
        NORTH {
            @Override boolean includes(Coordinate coordinate, Bounds bounds) {
                return coordinate.z() >= (bounds.minZ() + bounds.maxZ() + 1) / 2;
            }
            @Override String caption() { return "North half removed to expose rooms and center structure"; }
        },
        WEST {
            @Override boolean includes(Coordinate coordinate, Bounds bounds) {
                return coordinate.x() >= (bounds.minX() + bounds.maxX() + 1) / 2;
            }
            @Override String caption() { return "West half removed to expose rooms and center structure"; }
        },
        ROOFLESS {
            @Override boolean includes(Coordinate coordinate, Bounds bounds) {
                int removedLayers = Math.max(1, (int) Math.ceil(bounds.height() / 4.0));
                return coordinate.y() <= bounds.maxY() - removedLayers;
            }
            @Override String caption() { return "Top quarter removed to inspect the upper interior"; }
        },
        LOWER_SOUTH {
            @Override boolean includes(Coordinate coordinate, Bounds bounds) {
                return SOUTH.includes(coordinate, bounds)
                    && coordinate.y() <= (bounds.minY() + bounds.maxY()) / 2;
            }
            @Override String caption() { return "South cutaway restricted to the lower vertical half"; }
        },
        UPPER_SOUTH {
            @Override boolean includes(Coordinate coordinate, Bounds bounds) {
                return SOUTH.includes(coordinate, bounds)
                    && coordinate.y() >= (bounds.minY() + bounds.maxY()) / 2;
            }
            @Override String caption() { return "South cutaway restricted to the upper vertical half"; }
        },
        CENTER_CORE {
            @Override boolean includes(Coordinate coordinate, Bounds bounds) {
                int xMargin = Math.max(1, bounds.width() / 4);
                int zMargin = Math.max(1, bounds.depth() / 4);
                return coordinate.x() >= bounds.minX() + xMargin
                    && coordinate.x() <= bounds.maxX() - xMargin
                    && coordinate.z() >= bounds.minZ() + zMargin
                    && coordinate.z() <= bounds.maxZ() - zMargin;
            }
            @Override String caption() { return "Outer quarter removed on every side to expose the center core"; }
        };

        abstract boolean includes(Coordinate coordinate, Bounds bounds);
        abstract String caption();
    }

    private record Coordinate(int x, int y, int z) {
    }

    private record VisualBlock(String state, boolean generated) {
    }

    private record SceneBlock(Coordinate coordinate, VisualBlock visual) {
    }

    private record Rotated(int x, int y, int z) {
    }

    private record ProjectedBlock(Rotated rotated, VisualBlock visual) {
    }

    private record View(String label, int rotation, Cutaway cutaway) {
    }

    private record Sheet(String title, String fileSuffix, List<View> views) {
    }

    private record VisualScene(Bounds bounds, List<SceneBlock> blocks, int operationCount) {
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        static Bounds empty() {
            return new Bounds(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
        }

        boolean valid() {
            return minX <= maxX && minY <= maxY && minZ <= maxZ;
        }

        Bounds include(Coordinate coordinate) {
            return new Bounds(
                Math.min(minX, coordinate.x()), Math.min(minY, coordinate.y()), Math.min(minZ, coordinate.z()),
                Math.max(maxX, coordinate.x()), Math.max(maxY, coordinate.y()), Math.max(maxZ, coordinate.z())
            );
        }

        Bounds expand(int areaWidth, int areaHeight, int areaDepth) {
            return new Bounds(
                Math.max(0, minX - 1), Math.max(0, minY - 1), Math.max(0, minZ - 1),
                Math.min(areaWidth - 1, maxX + 1),
                Math.min(areaHeight - 1, maxY + 1),
                Math.min(areaDepth - 1, maxZ + 1)
            );
        }

        boolean contains(Coordinate coordinate) {
            return coordinate.x() >= minX && coordinate.x() <= maxX
                && coordinate.y() >= minY && coordinate.y() <= maxY
                && coordinate.z() >= minZ && coordinate.z() <= maxZ;
        }

        int width() { return maxX - minX + 1; }
        int height() { return maxY - minY + 1; }
        int depth() { return maxZ - minZ + 1; }
    }
}
