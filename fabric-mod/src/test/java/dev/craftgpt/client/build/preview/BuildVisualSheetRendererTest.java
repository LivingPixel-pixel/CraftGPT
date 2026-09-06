package dev.craftgpt.client.build.preview;

import dev.craftgpt.build.BuildLimits;
import dev.craftgpt.build.model.BuildOperation;
import dev.craftgpt.build.model.CompiledBuildArtifact;
import dev.craftgpt.context.AreaContext;
import dev.craftgpt.context.AreaPoint;
import dev.craftgpt.context.ExactBlockContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BuildVisualSheetRendererTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void rendersTwelveMinecraftTexturedViewsAcrossThreeContactSheets() throws Exception {
        Path output = temporaryDirectory.resolve("six-view.png");

        List<Path> outputs = BuildVisualSheetRenderer.renderInspectionSet(
            output,
            artifact(),
            context(),
            this::loadVanillaTexture
        );

        assertEquals(3, outputs.size());
        Path reportDirectory = Path.of("build", "reports");
        Files.createDirectories(reportDirectory);
        for (Path page : outputs) {
            assertTrue(Files.size(page) > 25_000L);
            BufferedImage image = ImageIO.read(page.toFile());
            assertEquals(BuildVisualSheetRenderer.IMAGE_WIDTH, image.getWidth());
            assertEquals(BuildVisualSheetRenderer.IMAGE_HEIGHT, image.getHeight());
            long distinctSampledColors = java.util.stream.IntStream.range(0, image.getWidth())
                .filter(x -> x % 20 == 0)
                .boxed()
                .flatMap(x -> java.util.stream.IntStream.range(0, image.getHeight())
                    .filter(y -> y % 20 == 0)
                    .mapToObj(y -> image.getRGB(x, y)))
                .distinct()
                .count();
            assertTrue(distinctSampledColors > 30);
            Files.copy(page, reportDirectory.resolve(page.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private CompiledBuildArtifact artifact() {
        List<String> palette = List.of(
            "minecraft:spruce_planks",
            "minecraft:white_wool",
            "minecraft:glass",
            "minecraft:stone_bricks",
            "minecraft:oak_planks"
        );
        List<BuildOperation> operations = new java.util.ArrayList<>();
        for (int x = 1; x <= 6; x++) {
            for (int z = 1; z <= 5; z++) operations.add(new BuildOperation(x, 1, z, 4));
        }
        for (int y = 2; y <= 4; y++) {
            for (int x = 1; x <= 6; x++) {
                operations.add(new BuildOperation(x, y, 1, wallPalette(x, y)));
                operations.add(new BuildOperation(x, y, 5, wallPalette(x + 1, y)));
            }
            for (int z = 2; z <= 4; z++) {
                operations.add(new BuildOperation(1, y, z, wallPalette(z, y)));
                operations.add(new BuildOperation(6, y, z, wallPalette(z + 1, y)));
            }
        }
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 6; z++) operations.add(new BuildOperation(x, 5, z, 3));
        }
        operations.add(new BuildOperation(3, 2, 1, 1));
        operations.add(new BuildOperation(3, 3, 1, 1));
        return new CompiledBuildArtifact(
            BuildLimits.SCHEMA_VERSION,
            "33333333-3333-3333-3333-333333333333",
            "11111111-1111-1111-1111-111111111111",
            "44444444-4444-4444-4444-444444444444",
            "v1",
            "b".repeat(64),
            "a".repeat(64),
            "2026-08-30T18:00:00Z",
            "gpt-test",
            "high",
            "Minecraft-textured visual test cottage",
            palette,
            List.copyOf(operations)
        );
    }

    private int wallPalette(int position, int y) {
        return y == 3 && position % 3 == 0 ? 2 : 0;
    }

    private AreaContext context() {
        return new AreaContext(
            1,
            "11111111-1111-1111-1111-111111111111",
            "minecraft:overworld",
            new AreaPoint(0, 0, 0),
            new AreaPoint(7, 5, 6),
            8, 6, 7, 336,
            56, 56, 0, 56, 0, 0,
            Map.of("minecraft:dirt", 56),
            "a".repeat(64),
            List.of(),
            List.of(),
            new ExactBlockContext(
                true,
                56,
                List.of("minecraft:dirt"),
                groundBlocks()
            )
        );
    }

    private String groundBlocks() {
        StringBuilder compact = new StringBuilder();
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 7; z++) {
                if (!compact.isEmpty()) compact.append(';');
                compact.append(x).append(",0,").append(z).append(",0");
            }
        }
        return compact.toString();
    }

    private BufferedImage loadVanillaTexture(String state, BuildVisualSheetRenderer.Face face) {
        String identifier = state.substring(0, state.indexOf('[') < 0 ? state.length() : state.indexOf('['));
        String path = identifier.substring(identifier.indexOf(':') + 1);
        String resource = "/assets/minecraft/textures/block/" + path + ".png";
        try (InputStream input = BuildVisualSheetRendererTest.class.getResourceAsStream(resource)) {
            return input == null ? null : ImageIO.read(input);
        } catch (Exception ignored) {
            return null;
        }
    }
}
