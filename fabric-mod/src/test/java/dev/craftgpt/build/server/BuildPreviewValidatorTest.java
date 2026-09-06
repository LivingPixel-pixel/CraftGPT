package dev.craftgpt.build.server;

import dev.craftgpt.area.AreaBounds;
import dev.craftgpt.area.AreaSelection;
import dev.craftgpt.build.BuildLimits;
import dev.craftgpt.build.model.BuildOperation;
import dev.craftgpt.build.model.BuildValidationRequest;
import net.minecraft.SharedConstants;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BuildPreviewValidatorTest {
    private static final String SELECTION_ID = "11111111-1111-1111-1111-111111111111";
    private static final String CONTEXT_HASH = "a".repeat(64);
    private static final AreaBounds BOUNDS = AreaBounds.between(
        new BlockPos(10, 20, 30),
        new BlockPos(11, 21, 31)
    );

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void reportsAllIndependentServerProblemsTogether() {
        BuildValidationRequest artifact = artifact(
            List.of(
                "minecraft:not_a_block",
                BlockStateParser.serialize(Blocks.TNT.defaultBlockState()),
                "minecraft:stone"
            ),
            List.of(
                new BuildOperation(9, 0, 0, 9),
                new BuildOperation(0, 0, 0, 2),
                new BuildOperation(0, 0, 0, 2)
            )
        );

        BuildPreviewValidationResult result = validate(artifact, ignored -> Blocks.AIR.defaultBlockState());

        assertFalse(result.accepted());
        assertTrue(result.problems().stream().anyMatch(problem ->
            problem.code().equals(BuildPreviewValidationResult.INVALID_BLOCK_STATE)
                && problem.location().equals("build.palette[0]")));
        assertTrue(result.problems().stream().anyMatch(problem ->
            problem.code().equals(BuildPreviewValidationResult.UNSAFE_BLOCK)
                && problem.location().equals("build.palette[1]")));
        assertTrue(result.problems().stream().anyMatch(problem ->
            problem.code().equals(BuildPreviewValidationResult.OUT_OF_BOUNDS)));
        assertTrue(result.problems().stream().anyMatch(problem ->
            problem.code().equals(BuildPreviewValidationResult.INVALID_PALETTE_INDEX)));
        assertTrue(result.problems().stream().anyMatch(problem ->
            problem.code().equals(BuildPreviewValidationResult.DUPLICATE_POSITION)));
    }

    @Test
    void acceptsSafeChangedOperationsAndCountsOnlyActualChanges() {
        BuildValidationRequest artifact = artifact(
            List.of("minecraft:stone"),
            List.of(
                new BuildOperation(0, 0, 0, 0),
                new BuildOperation(1, 0, 0, 0)
            )
        );

        BuildPreviewValidationResult result = validate(
            artifact,
            position -> position.equals(new BlockPos(10, 20, 30))
                ? Blocks.STONE.defaultBlockState()
                : Blocks.AIR.defaultBlockState()
        );

        assertTrue(result.accepted());
        assertEquals(BuildPreviewValidationResult.OK, result.code());
        assertEquals(1, result.actualChanges());
    }

    @Test
    void rejectsPreviewContainingOnlyNoOps() {
        BuildValidationRequest artifact = artifact(
            List.of("minecraft:stone"),
            List.of(new BuildOperation(0, 0, 0, 0))
        );

        BuildPreviewValidationResult result = validate(artifact, ignored -> Blocks.STONE.defaultBlockState());

        assertFalse(result.accepted());
        assertEquals(BuildPreviewValidationResult.NO_CHANGES, result.code());
    }

    @Test
    void rejectsOutOfBoundsDuplicateAndInvalidPaletteReferences() {
        BuildPreviewValidationResult outOfBounds = validate(
            artifact(List.of("minecraft:stone"), List.of(new BuildOperation(2, 0, 0, 0))),
            ignored -> Blocks.AIR.defaultBlockState()
        );
        BuildPreviewValidationResult duplicate = validate(
            artifact(
                List.of("minecraft:stone"),
                List.of(new BuildOperation(0, 0, 0, 0), new BuildOperation(0, 0, 0, 0))
            ),
            ignored -> Blocks.AIR.defaultBlockState()
        );
        BuildPreviewValidationResult paletteIndex = validate(
            artifact(List.of("minecraft:stone"), List.of(new BuildOperation(0, 0, 0, 1))),
            ignored -> Blocks.AIR.defaultBlockState()
        );

        assertEquals(BuildPreviewValidationResult.OUT_OF_BOUNDS, outOfBounds.code());
        assertEquals(BuildPreviewValidationResult.DUPLICATE_POSITION, duplicate.code());
        assertEquals(BuildPreviewValidationResult.INVALID_PALETTE_INDEX, paletteIndex.code());
    }

    @Test
    void rejectsNonCanonicalUnknownAndUnsafePaletteStates() {
        BuildPreviewValidationResult trailing = validate(
            artifact(List.of("minecraft:stone trailing"), List.of(new BuildOperation(0, 0, 0, 0))),
            ignored -> Blocks.AIR.defaultBlockState()
        );
        BuildPreviewValidationResult unknown = validate(
            artifact(List.of("minecraft:not_a_block"), List.of(new BuildOperation(0, 0, 0, 0))),
            ignored -> Blocks.AIR.defaultBlockState()
        );
        String tnt = BlockStateParser.serialize(Blocks.TNT.defaultBlockState());
        BuildPreviewValidationResult unsafe = validate(
            artifact(List.of(tnt), List.of(new BuildOperation(0, 0, 0, 0))),
            ignored -> Blocks.AIR.defaultBlockState()
        );
        String waterlogged = BlockStateParser.serialize(
            Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true)
        );
        BuildPreviewValidationResult fluid = validate(
            artifact(List.of(waterlogged), List.of(new BuildOperation(0, 0, 0, 0))),
            ignored -> Blocks.AIR.defaultBlockState()
        );
        String chest = BlockStateParser.serialize(Blocks.CHEST.defaultBlockState());
        BuildPreviewValidationResult blockEntityTarget = validate(
            artifact(List.of(chest), List.of(new BuildOperation(0, 0, 0, 0))),
            ignored -> Blocks.AIR.defaultBlockState()
        );

        assertEquals(BuildPreviewValidationResult.INVALID_BLOCK_STATE, trailing.code());
        assertEquals(BuildPreviewValidationResult.INVALID_BLOCK_STATE, unknown.code());
        assertEquals(BuildPreviewValidationResult.UNSAFE_BLOCK, unsafe.code());
        assertEquals(BuildPreviewValidationResult.UNSAFE_BLOCK, fluid.code());
        assertEquals(BuildPreviewValidationResult.UNSAFE_BLOCK, blockEntityTarget.code());
    }

    @Test
    void rejectsClearingProtectedExistingBlocks() {
        BuildPreviewValidationResult result = validate(
            artifact(List.of("minecraft:air"), List.of(new BuildOperation(0, 0, 0, 0))),
            ignored -> Blocks.BEDROCK.defaultBlockState()
        );

        assertEquals(BuildPreviewValidationResult.UNSAFE_BLOCK, result.code());
    }

    @Test
    void rejectsStaleContextWrongSelectionAndHardOperationOverflow() {
        BuildValidationRequest base = artifact(
            List.of("minecraft:stone"),
            List.of(new BuildOperation(0, 0, 0, 0))
        );
        BuildPreviewValidationResult stale = BuildPreviewValidator.validateArtifact(
            base,
            SELECTION_ID,
            "b".repeat(64),
            BOUNDS,
            BuiltInRegistries.BLOCK,
            ignored -> Blocks.AIR.defaultBlockState()
        );
        BuildPreviewValidationResult selection = BuildPreviewValidator.validateArtifact(
            base,
            "22222222-2222-2222-2222-222222222222",
            CONTEXT_HASH,
            BOUNDS,
            BuiltInRegistries.BLOCK,
            ignored -> Blocks.AIR.defaultBlockState()
        );

        AreaBounds largeBounds = AreaBounds.between(BlockPos.ZERO, new BlockPos(21, 21, 21));
        BuildValidationRequest oversized = artifact(
            List.of("minecraft:stone"),
            Collections.nCopies(BuildLimits.HARD_MAX_OPERATIONS + 1, new BuildOperation(0, 0, 0, 0))
        );
        BuildPreviewValidationResult tooMany = BuildPreviewValidator.validateArtifact(
            oversized,
            SELECTION_ID,
            CONTEXT_HASH,
            largeBounds,
            BuiltInRegistries.BLOCK,
            ignored -> Blocks.AIR.defaultBlockState()
        );

        assertEquals(BuildPreviewValidationResult.STALE_CONTEXT, stale.code());
        assertEquals(BuildPreviewValidationResult.SELECTION_MISMATCH, selection.code());
        assertEquals(BuildPreviewValidationResult.TOO_MANY_OPERATIONS, tooMany.code());
    }

    @Test
    void requiresCompleteSelectionInPlayersCurrentDimension() {
        AreaSelection incomplete = new AreaSelection(
            SELECTION_ID,
            "minecraft:overworld",
            BlockPos.ZERO,
            Optional.empty()
        );
        AreaSelection complete = incomplete.complete(new BlockPos(1, 1, 1));
        BuildValidationRequest artifact = artifact(
            List.of("minecraft:stone"),
            List.of(new BuildOperation(0, 0, 0, 0))
        );

        assertEquals(
            BuildPreviewValidationResult.INCOMPLETE_SELECTION,
            BuildPreviewValidator.validateSelection(incomplete, "minecraft:overworld", artifact).code()
        );
        assertEquals(
            BuildPreviewValidationResult.DIMENSION_MISMATCH,
            BuildPreviewValidator.validateSelection(complete, "minecraft:the_nether", artifact).code()
        );
        assertTrue(BuildPreviewValidator.validateSelection(complete, "minecraft:overworld", artifact).accepted());
    }

    private BuildPreviewValidationResult validate(
        BuildValidationRequest artifact,
        java.util.function.Function<BlockPos, net.minecraft.world.level.block.state.BlockState> currentState
    ) {
        return BuildPreviewValidator.validateArtifact(
            artifact,
            SELECTION_ID,
            CONTEXT_HASH,
            BOUNDS,
            BuiltInRegistries.BLOCK,
            currentState
        );
    }

    private BuildValidationRequest artifact(List<String> palette, List<BuildOperation> operations) {
        return new BuildValidationRequest(
            BuildLimits.SCHEMA_VERSION,
            SELECTION_ID,
            CONTEXT_HASH,
            palette,
            operations
        );
    }
}
