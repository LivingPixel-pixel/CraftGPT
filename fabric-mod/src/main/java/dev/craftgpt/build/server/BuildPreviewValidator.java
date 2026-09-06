package dev.craftgpt.build.server;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.craftgpt.area.AreaBounds;
import dev.craftgpt.area.AreaCommands;
import dev.craftgpt.area.AreaSelection;
import dev.craftgpt.area.AreaSelectionManager;
import dev.craftgpt.build.BuildLimits;
import dev.craftgpt.build.model.BuildOperation;
import dev.craftgpt.build.model.BuildValidationRequest;
import dev.craftgpt.context.AreaContext;
import dev.craftgpt.context.AreaContextExtractor;
import dev.craftgpt.context.AreaContextHasher;
import dev.craftgpt.validation.ValidationProblemCollector;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Revalidates every client-provided build detail against current server state. This class only
 * reads the world; it deliberately has no placement or command-execution path.
 */
public final class BuildPreviewValidator {
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> REPLACEABLE_TECHNICAL_AIR = Set.of(
        "minecraft:cave_air",
        "minecraft:void_air"
    );

    public BuildPreviewValidationResult validate(ServerPlayer player, BuildValidationRequest build) {
        if (player == null || build == null) {
            return BuildPreviewValidationResult.rejected(BuildPreviewValidationResult.INVALID_ARTIFACT);
        }

        AreaSelection selection = AreaSelectionManager.INSTANCE.get(player.getUUID()).orElse(null);
        String currentDimension = player.level().dimension().identifier().toString();
        BuildPreviewValidationResult selectionResult = validateSelection(selection, currentDimension, build);
        if (!selectionResult.accepted()) {
            return selectionResult;
        }

        AreaBounds bounds = selection.bounds().orElseThrow();
        ServerLevel level = player.level();
        AreaContext currentContext = AreaContextExtractor.extract(level, selection);
        if (currentContext.unloadedBlocks() > 0) {
            return BuildPreviewValidationResult.rejected(BuildPreviewValidationResult.UNLOADED_AREA);
        }

        String currentContextHash = AreaContextHasher.sha256(currentContext);
        Registry<Block> blockRegistry = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        return validateArtifact(
            build,
            selection.selectionId(),
            currentContextHash,
            bounds,
            blockRegistry,
            level::getBlockState
        );
    }

    static BuildPreviewValidationResult validateSelection(
        AreaSelection selection,
        String currentDimension,
        BuildValidationRequest build
    ) {
        if (selection == null) {
            return BuildPreviewValidationResult.rejected(BuildPreviewValidationResult.NO_SELECTION);
        }
        if (!selection.isComplete()) {
            return BuildPreviewValidationResult.rejected(BuildPreviewValidationResult.INCOMPLETE_SELECTION);
        }
        if (currentDimension == null || !selection.dimension().equals(currentDimension)) {
            return BuildPreviewValidationResult.rejected(BuildPreviewValidationResult.DIMENSION_MISMATCH);
        }
        if (build == null || !selection.selectionId().equals(build.selectionId())) {
            return BuildPreviewValidationResult.rejected(BuildPreviewValidationResult.SELECTION_MISMATCH);
        }
        AreaBounds bounds = selection.bounds().orElseThrow();
        if (bounds.volume() <= 0
            || bounds.volume() > AreaCommands.MAX_AREA_VOLUME
            || bounds.width() > AreaCommands.MAX_AXIS_LENGTH
            || bounds.height() > AreaCommands.MAX_AXIS_LENGTH
            || bounds.depth() > AreaCommands.MAX_AXIS_LENGTH) {
            return BuildPreviewValidationResult.rejected(BuildPreviewValidationResult.INVALID_SELECTION);
        }
        return BuildPreviewValidationResult.accepted(0);
    }

    static BuildPreviewValidationResult validateArtifact(
        BuildValidationRequest build,
        String expectedSelectionId,
        String expectedContextHash,
        AreaBounds bounds,
        Registry<Block> blockRegistry,
        Function<BlockPos, BlockState> currentStateAt
    ) {
        if (!validMetadata(build)) {
            return BuildPreviewValidationResult.rejected(BuildPreviewValidationResult.INVALID_ARTIFACT);
        }
        if (!expectedSelectionId.equals(build.selectionId())) {
            return BuildPreviewValidationResult.rejected(BuildPreviewValidationResult.SELECTION_MISMATCH);
        }
        if (!expectedContextHash.equals(build.contextHash())) {
            return BuildPreviewValidationResult.rejected(BuildPreviewValidationResult.STALE_CONTEXT);
        }

        int maximumOperations = BuildLimits.effectiveMaximumOperations(
            BuildLimits.HARD_MAX_OPERATIONS,
            bounds.volume()
        );
        ValidationProblemCollector problems = new ValidationProblemCollector();
        if (build.operations().size() > maximumOperations) {
            problems.add(BuildPreviewValidationResult.TOO_MANY_OPERATIONS, "build.operations");
        }
        if (build.palette().isEmpty() || build.palette().size() > BuildLimits.MAX_PALETTE_ENTRIES) {
            problems.add(BuildPreviewValidationResult.INVALID_PALETTE, "build.palette");
        }

        List<BlockState> paletteStates = new ArrayList<>(build.palette().size());
        Set<String> uniquePalette = new HashSet<>();
        for (int index = 0; index < build.palette().size(); index++) {
            String serializedState = build.palette().get(index);
            String location = "build.palette[" + index + "]";
            if (serializedState == null
                || serializedState.isBlank()
                || serializedState.length() > BuildLimits.MAX_BLOCK_STATE_LENGTH) {
                problems.add(BuildPreviewValidationResult.INVALID_PALETTE, location);
                paletteStates.add(null);
                continue;
            }
            if (!uniquePalette.add(serializedState)) {
                problems.add(BuildPreviewValidationResult.INVALID_PALETTE, location);
            }

            BlockState state;
            try {
                state = parseCanonicalState(blockRegistry, serializedState);
            } catch (CommandSyntaxException | IllegalArgumentException exception) {
                problems.add(BuildPreviewValidationResult.INVALID_BLOCK_STATE, location);
                paletteStates.add(null);
                continue;
            }
            if (unsafeTarget(blockRegistry, serializedState, state)) {
                problems.add(BuildPreviewValidationResult.UNSAFE_BLOCK, location);
            }
            paletteStates.add(state);
        }

        Set<BlockPos> positions = new HashSet<>();
        int actualChanges = 0;
        for (int index = 0; index < build.operations().size(); index++) {
            BuildOperation operation = build.operations().get(index);
            String location = "build.operations[" + index + "]";
            if (operation == null) {
                problems.add(BuildPreviewValidationResult.INVALID_ARTIFACT, location);
                continue;
            }
            boolean inBounds = !(operation.relativeX() < 0 || operation.relativeX() >= bounds.width()
                || operation.relativeY() < 0 || operation.relativeY() >= bounds.height()
                || operation.relativeZ() < 0 || operation.relativeZ() >= bounds.depth());
            if (!inBounds) {
                problems.add(BuildPreviewValidationResult.OUT_OF_BOUNDS, location);
            }
            boolean validPaletteIndex = operation.paletteIndex() >= 0
                && operation.paletteIndex() < paletteStates.size();
            if (!validPaletteIndex) {
                problems.add(BuildPreviewValidationResult.INVALID_PALETTE_INDEX, location);
            }

            if (!inBounds) continue;
            BlockPos position = bounds.min().offset(operation.relativeX(), operation.relativeY(), operation.relativeZ());
            if (!positions.add(position)) {
                problems.add(BuildPreviewValidationResult.DUPLICATE_POSITION, location);
            }

            BlockState currentState = currentStateAt.apply(position);
            if (currentState == null || unsafeExisting(blockRegistry, currentState)) {
                problems.add(BuildPreviewValidationResult.UNSAFE_BLOCK, location + ".existingBlock");
                continue;
            }
            BlockState targetState = validPaletteIndex ? paletteStates.get(operation.paletteIndex()) : null;
            if (targetState != null && !currentState.equals(targetState)) {
                actualChanges++;
            }
        }
        if (!problems.isEmpty()) {
            return BuildPreviewValidationResult.rejected(problems.problems());
        }
        if (actualChanges == 0) {
            return BuildPreviewValidationResult.rejected(BuildPreviewValidationResult.NO_CHANGES);
        }
        java.util.Map<BlockPos,BlockState> targets=new java.util.HashMap<>();
        for(var op:build.operations()) targets.put(bounds.min().offset(op.relativeX(),op.relativeY(),op.relativeZ()),paletteStates.get(op.paletteIndex()));
        var functional=BuildFunctionalChecks.check(targets,currentStateAt,bounds.min());
        if(!functional.isEmpty()) return BuildPreviewValidationResult.rejected(functional);
        return BuildPreviewValidationResult.accepted(actualChanges);
    }

    public static BlockState parseCanonicalState(
        Registry<Block> blockRegistry,
        String serializedState
    ) throws CommandSyntaxException {
        StringReader reader = new StringReader(serializedState);
        BlockStateParser.BlockResult parsed = BlockStateParser.parseForBlock(blockRegistry, reader, false);
        reader.skipWhitespace();
        if (reader.canRead() || parsed.nbt() != null) {
            throw new IllegalArgumentException("Trailing or NBT block-state data is not allowed");
        }
        BlockState state = parsed.blockState();
        if (!BlockStateParser.serialize(state).equals(serializedState)
            || parsed.properties().size() > BuildLimits.MAX_STATE_PROPERTIES) {
            throw new IllegalArgumentException("Block state is not canonical");
        }
        return state;
    }

    private static boolean unsafeTarget(
        Registry<Block> blockRegistry,
        String serializedState,
        BlockState state
    ) {
        if (BuildLimits.isDangerousState(serializedState)
            || !state.getFluidState().isEmpty()
            || state.getBlock() instanceof EntityBlock) {
            return true;
        }
        return blockRegistry != null
            && BuildLimits.isDangerousBlockId(blockRegistry.getKey(state.getBlock()).toString());
    }

    private static boolean unsafeExisting(Registry<Block> blockRegistry, BlockState state) {
        if (!state.getFluidState().isEmpty() || blockRegistry == null) {
            return !state.getFluidState().isEmpty();
        }
        String blockId = blockRegistry.getKey(state.getBlock()).toString();
        return BuildLimits.isDangerousBlockId(blockId) && !REPLACEABLE_TECHNICAL_AIR.contains(blockId);
    }

    private static boolean validMetadata(BuildValidationRequest build) {
        return build != null
            && build.schemaVersion() == BuildLimits.SCHEMA_VERSION
            && validUuid(build.selectionId())
            && build.contextHash() != null
            && SHA_256.matcher(build.contextHash()).matches()
            && build.palette() != null
            && build.operations() != null;
    }

    private static boolean validUuid(String value) {
        if (value == null || value.length() != 36) {
            return false;
        }
        try {
            return UUID.fromString(value).toString().equals(value.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

}
