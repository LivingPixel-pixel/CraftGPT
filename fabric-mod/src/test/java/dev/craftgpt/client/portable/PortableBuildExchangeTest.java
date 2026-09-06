package dev.craftgpt.client.portable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.craftgpt.build.model.BuildDraft;
import dev.craftgpt.client.planning.model.IntentionSpec;
import dev.craftgpt.client.planning.model.MaterialRole;
import dev.craftgpt.client.planning.model.PlanDimensions;
import dev.craftgpt.context.AreaContext;
import dev.craftgpt.context.AreaPoint;
import dev.craftgpt.context.ExactBlockContext;
import dev.craftgpt.context.LayerSummary;
import dev.craftgpt.context.SurfaceSample;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PortableBuildExchangeTest {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @TempDir
    Path temporaryDirectory;

    @Test
    void exportsCoordinateFreePackageAndLoadsMatchingResult() throws Exception {
        PortableBuildExchange exchange = new PortableBuildExchange(temporaryDirectory.resolve("requests"));
        PortableBuildExchange.PortableExport exported = exchange.export(
            context(), "Build a small stone arch", 100, null
        );

        String requestJson = Files.readString(exported.requestFile());
        PortableBuildRequest request = GSON.fromJson(requestJson, PortableBuildRequest.class);
        assertFalse(requestJson.contains("123456"));
        assertFalse(requestJson.contains("-234567"));
        assertTrue(requestJson.contains("0,0,0,0"));
        assertTrue(Files.isRegularFile(exported.directory().resolve(PortableBuildExchange.PROMPT_FILE)));
        String workerPrompt = Files.readString(
            exported.directory().resolve(PortableBuildExchange.PROMPT_FILE)
        );
        assertTrue(workerPrompt.contains("COMPONENTS:"));
        assertTrue(workerPrompt.contains("You choose every material"));
        assertTrue(workerPrompt.contains("REVIEW PROTOCOL:"));
        assertTrue(workerPrompt.contains(dev.craftgpt.client.build.api.BuildWorkerContract.SURVIVAL_FUNCTION));
        assertEquals(1, workerPrompt.split("SURVIVAL FUNCTION:", -1).length - 1);
        assertTrue(Files.isRegularFile(exported.resultSchemaFile()));
        String resultSchema = Files.readString(exported.resultSchemaFile());
        assertTrue(resultSchema.contains(request.requestId()));
        assertTrue(Files.isRegularFile(exported.viewerFile()));

        PortableBuildResult result = new PortableBuildResult(
            1,
            request.requestId(),
            request.contextHash(),
            request.instructionHash(),
            plan(),
            new BuildDraft(1, "Stone arch", List.of("minecraft:stone_bricks"), List.of("1,0,1,0"))
        );
        Files.writeString(exported.directory().resolve(PortableBuildExchange.RESULT_FILE), GSON.toJson(result));

        PortableBuildExchange.PortableImport imported = exchange.loadLatestResult(context());
        assertEquals(request.requestId(), imported.result().requestId());
        assertEquals("Stone arch", imported.result().build().summary());
    }

    @Test
    void rejectsResultThatWasBoundToAnotherInstruction() throws Exception {
        PortableBuildExchange exchange = new PortableBuildExchange(temporaryDirectory.resolve("requests"));
        PortableBuildExchange.PortableExport exported = exchange.export(context(), "Build an arch", 100, null);
        PortableBuildRequest request = GSON.fromJson(
            Files.readString(exported.requestFile()), PortableBuildRequest.class
        );
        PortableBuildResult result = new PortableBuildResult(
            1,
            request.requestId(),
            request.contextHash(),
            "f".repeat(64),
            plan(),
            new BuildDraft(1, "Stone arch", List.of("minecraft:stone_bricks"), List.of("1,0,1,0"))
        );
        Files.writeString(exported.directory().resolve(PortableBuildExchange.RESULT_FILE), GSON.toJson(result));

        assertThrows(PortableExchangeException.class, () -> exchange.loadLatestResult(context()));
    }

    @Test
    void loadsTheExactResultDirectoryReturnedByCodex() throws Exception {
        PortableBuildExchange exchange = new PortableBuildExchange(temporaryDirectory.resolve("requests"));
        PortableBuildExchange.PortableExport exported = exchange.export(
            context(), "Build the exact requested arch", 100, null
        );
        PortableBuildRequest request = GSON.fromJson(
            Files.readString(exported.requestFile()), PortableBuildRequest.class
        );
        PortableBuildResult result = new PortableBuildResult(
            1,
            request.requestId(),
            request.contextHash(),
            request.instructionHash(),
            plan(),
            new BuildDraft(1, "Exact arch", List.of("minecraft:stone_bricks"), List.of("1,0,1,0"))
        );
        Files.writeString(exported.directory().resolve(PortableBuildExchange.RESULT_FILE), GSON.toJson(result));

        PortableBuildExchange.PortableImport imported = exchange.loadResult(exported.directory(), context());

        assertEquals(exported.directory().toAbsolutePath().normalize(), imported.directory());
        assertEquals("Exact arch", imported.result().build().summary());
    }

    private AreaContext context() {
        return new AreaContext(
            1,
            "00000000-0000-0000-0000-000000000001",
            "minecraft:overworld",
            new AreaPoint(123_456, 64, -234_567),
            new AreaPoint(123_459, 67, -234_564),
            4, 4, 4, 64, 64, 2, 0, 1, 64, 64,
            Map.of("minecraft:stone", 2, "minecraft:air", 62),
            "a".repeat(64),
            List.of(new LayerSummary(0, 2, "minecraft:stone")),
            List.of(new SurfaceSample(0, 0, 0, "minecraft:stone")),
            new ExactBlockContext(
                true,
                2,
                List.of("minecraft:stone"),
                "0,0,0,0;1,0,0,0"
            )
        );
    }

    private IntentionSpec plan() {
        return new IntentionSpec(
            "Stone arch",
            "A small stone arch",
            List.of("Create a visible arch"),
            "Rustic",
            new PlanDimensions(4, 4, 4),
            "South-facing",
            List.of(new MaterialRole("structure", List.of("minecraft:stone_bricks"), "Arch body")),
            List.of("Walkable opening"),
            List.of("Simple trim"),
            List.of("Stay in bounds"),
            List.of("Fluids"),
            List.of("The south side is the front"),
            20,
            "The footprint fits the area.",
            "Place two pillars and a lintel."
        );
    }
}
