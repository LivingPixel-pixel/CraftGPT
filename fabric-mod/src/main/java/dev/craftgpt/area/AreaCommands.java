package dev.craftgpt.area;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.craftgpt.context.AreaContext;
import dev.craftgpt.context.AreaContextExtractor;
import dev.craftgpt.network.AreaContextPayload;
import dev.craftgpt.network.AreaSelectionPayload;
import dev.craftgpt.network.OpenSettingsPayload;
import dev.craftgpt.network.PlanningActionPayload;
import dev.craftgpt.item.CraftGptItems;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public final class AreaCommands {
    public static final long MAX_AREA_VOLUME = 32_768L;
    public static final int MAX_AXIS_LENGTH = 128;
    public static final int MAX_CHUNK_COUNT = 64;

    private AreaCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("craftgpt")
                .executes(AreaCommands::showHelp)
                .then(Commands.literal("book").executes(AreaCommands::giveCraftBook))
                .then(Commands.literal("settings").executes(AreaCommands::openSettings))
                .then(Commands.literal("plan").executes(AreaCommands::openPlanning))
                .then(Commands.literal("prompt")
                    .then(Commands.argument("idea", StringArgumentType.greedyString())
                        .executes(context -> forwardPlanningText(context, PlanningActionPayload.PROMPT, "idea"))))
                .then(Commands.literal("discuss")
                    .then(Commands.argument("change", StringArgumentType.greedyString())
                        .executes(context -> forwardPlanningText(context, PlanningActionPayload.DISCUSS, "change"))))
                .then(Commands.literal("versions").executes(context ->
                    forwardPlanningAction(context, PlanningActionPayload.VERSIONS, "")))
                .then(Commands.literal("preview").executes(context ->
                    forwardPlanningAction(context, PlanningActionPayload.PREVIEW, "")))
                .then(Commands.literal("place").executes(context ->
                    forwardPlanningAction(context, PlanningActionPayload.PLACE, "")))
                .then(Commands.literal("undo").executes(context ->
                    forwardPlanningAction(context, PlanningActionPayload.UNDO, "")))
                .then(Commands.literal("history").executes(context ->
                    forwardPlanningAction(context, PlanningActionPayload.HISTORY, "")))
                .then(Commands.literal("export")
                    .then(Commands.argument("idea", StringArgumentType.greedyString())
                        .executes(context -> forwardPlanningText(
                            context, PlanningActionPayload.EXPORT, "idea"
                        ))))
                .then(Commands.literal("import").executes(context ->
                    forwardPlanningAction(context, PlanningActionPayload.IMPORT, "")))
                .then(Commands.literal("exchange").executes(context ->
                    forwardPlanningAction(context, PlanningActionPayload.EXCHANGE, "")))
                .then(Commands.literal("revert")
                    .then(Commands.argument("version", StringArgumentType.word())
                        .executes(context -> forwardPlanningAction(
                            context,
                            PlanningActionPayload.REVERT,
                            StringArgumentType.getString(context, "version")
                        ))))
                .then(Commands.literal("setArea")
                    .then(Commands.literal("start").executes(AreaCommands::setStart))
                    .then(Commands.literal("stop").executes(AreaCommands::setStop)))
                .then(Commands.literal("area")
                    .then(Commands.literal("info").executes(AreaCommands::showAreaInfo))
                    .then(Commands.literal("clear").executes(AreaCommands::clearArea))))
        );
    }

    private static int giveCraftBook(CommandContext<CommandSourceStack> context)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemStack book = new ItemStack(CraftGptItems.CRAFT_BOOK);
        if (!player.addItem(book)) player.drop(book, false);
        player.playSound(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 0.8F, 1.1F);
        context.getSource().sendSuccess(() -> Component.translatable("craftgpt.command.book.given"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int showHelp(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.translatable("craftgpt.command.help"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int openPlanning(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<AreaSelection> selection = completeSelection(context, player);
        if (selection.isEmpty()) {
            return 0;
        }
        syncContext(player, selection.get());
        ServerPlayNetworking.send(player, new PlanningActionPayload(PlanningActionPayload.OPEN, ""));
        return Command.SINGLE_SUCCESS;
    }

    private static int openSettings(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerPlayNetworking.send(player, new OpenSettingsPayload(true));
        return Command.SINGLE_SUCCESS;
    }

    private static int setStart(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        BlockPos position = player.blockPosition();
        String dimension = dimensionId(player);
        AreaSelection selection = AreaSelectionManager.INSTANCE.start(player.getUUID(), dimension, position);
        sync(player, selection);
        context.getSource().sendSuccess(() -> Component.translatable(
            "craftgpt.command.area.start",
            position.getX(), position.getY(), position.getZ()
        ), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int setStop(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<AreaSelection> current = AreaSelectionManager.INSTANCE.get(player.getUUID());
        if (current.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("craftgpt.command.area.no_start"));
            return 0;
        }

        String dimension = dimensionId(player);
        if (!current.get().dimension().equals(dimension)) {
            context.getSource().sendFailure(Component.translatable("craftgpt.command.area.dimension_mismatch"));
            return 0;
        }

        BlockPos stop = player.blockPosition();
        AreaSelection completed = current.get().complete(stop);
        AreaBounds bounds = completed.bounds().orElseThrow();
        if (bounds.volume() > MAX_AREA_VOLUME) {
            context.getSource().sendFailure(Component.translatable(
                "craftgpt.command.area.too_large", bounds.volume(), MAX_AREA_VOLUME
            ));
            return 0;
        }
        if (bounds.width() > MAX_AXIS_LENGTH || bounds.height() > MAX_AXIS_LENGTH || bounds.depth() > MAX_AXIS_LENGTH) {
            context.getSource().sendFailure(Component.translatable(
                "craftgpt.command.area.axis_too_large", MAX_AXIS_LENGTH
            ));
            return 0;
        }
        long chunkCount = chunkCount(bounds);
        if (chunkCount > MAX_CHUNK_COUNT) {
            context.getSource().sendFailure(Component.translatable(
                "craftgpt.command.area.too_many_chunks", chunkCount, MAX_CHUNK_COUNT
            ));
            return 0;
        }

        AreaContext areaContext = AreaContextExtractor.extract((ServerLevel) player.level(), completed);
        if (areaContext.unloadedBlocks() > 0) {
            context.getSource().sendFailure(Component.translatable(
                "craftgpt.command.area.unloaded", areaContext.unloadedBlocks()
            ));
            return 0;
        }

        AreaSelectionManager.INSTANCE.put(player.getUUID(), completed);
        sync(player, completed);
        ServerPlayNetworking.send(player, AreaContextPayload.from(areaContext));
        ServerPlayNetworking.send(player, new PlanningActionPayload(PlanningActionPayload.OPEN, ""));
        context.getSource().sendSuccess(() -> Component.translatable(
            "craftgpt.command.area.complete",
            bounds.width(), bounds.height(), bounds.depth(), bounds.volume()
        ), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int forwardPlanningText(
        CommandContext<CommandSourceStack> context,
        String action,
        String argumentName
    ) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String value = StringArgumentType.getString(context, argumentName).trim();
        if (value.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("craftgpt.command.plan.empty"));
            return 0;
        }
        if (value.length() > PlanningActionPayload.MAX_VALUE_LENGTH) {
            context.getSource().sendFailure(Component.translatable(
                "craftgpt.command.plan.too_long", PlanningActionPayload.MAX_VALUE_LENGTH
            ));
            return 0;
        }
        return forwardPlanningAction(context, action, value);
    }

    private static int forwardPlanningAction(
        CommandContext<CommandSourceStack> context,
        String action,
        String value
    ) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (PlanningActionPayload.PROMPT.equals(action)
            || PlanningActionPayload.DISCUSS.equals(action)
            || PlanningActionPayload.PREVIEW.equals(action)
            || PlanningActionPayload.PLACE.equals(action)
            || PlanningActionPayload.VERSIONS.equals(action)
            || PlanningActionPayload.REVERT.equals(action)
            || PlanningActionPayload.EXPORT.equals(action)
            || PlanningActionPayload.IMPORT.equals(action)) {
            Optional<AreaSelection> selection = completeSelection(context, player);
            if (selection.isEmpty()) {
                return 0;
            }
            syncContext(player, selection.get());
        }
        ServerPlayNetworking.send(player, new PlanningActionPayload(action, value));
        return Command.SINGLE_SUCCESS;
    }

    private static Optional<AreaSelection> completeSelection(
        CommandContext<CommandSourceStack> context,
        ServerPlayer player
    ) {
        Optional<AreaSelection> selection = AreaSelectionManager.INSTANCE.get(player.getUUID());
        if (selection.isEmpty() || !selection.get().isComplete()) {
            context.getSource().sendFailure(Component.translatable("craftgpt.command.plan.area_required"));
            return Optional.empty();
        }
        if (!selection.get().dimension().equals(dimensionId(player))) {
            context.getSource().sendFailure(Component.translatable("craftgpt.command.area.dimension_mismatch"));
            return Optional.empty();
        }
        return selection;
    }

    private static int showAreaInfo(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<AreaSelection> selection = AreaSelectionManager.INSTANCE.get(player.getUUID());
        if (selection.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("craftgpt.command.area.none"));
            return 0;
        }

        AreaSelection value = selection.get();
        if (!value.isComplete()) {
            BlockPos start = value.start();
            context.getSource().sendSuccess(() -> Component.translatable(
                "craftgpt.command.area.info_started", start.getX(), start.getY(), start.getZ()
            ), false);
            return Command.SINGLE_SUCCESS;
        }

        AreaBounds bounds = value.bounds().orElseThrow();
        context.getSource().sendSuccess(() -> Component.translatable(
            "craftgpt.command.area.info_complete",
            bounds.min().getX(), bounds.min().getY(), bounds.min().getZ(),
            bounds.max().getX(), bounds.max().getY(), bounds.max().getZ(),
            bounds.width(), bounds.height(), bounds.depth(), bounds.volume()
        ), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int clearArea(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        AreaSelectionManager.INSTANCE.clear(player.getUUID());
        ServerPlayNetworking.send(player, AreaSelectionPayload.cleared());
        context.getSource().sendSuccess(() -> Component.translatable("craftgpt.command.area.cleared"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static void sync(ServerPlayer player, AreaSelection selection) {
        ServerPlayNetworking.send(player, AreaSelectionPayload.from(selection));
    }

    private static void syncContext(ServerPlayer player, AreaSelection selection) {
        AreaContext areaContext = AreaContextExtractor.extract((ServerLevel) player.level(), selection);
        ServerPlayNetworking.send(player, AreaContextPayload.from(areaContext));
    }

    private static String dimensionId(ServerPlayer player) {
        return player.level().dimension().identifier().toString();
    }

    private static long chunkCount(AreaBounds bounds) {
        long chunksX = (bounds.max().getX() >> 4) - (bounds.min().getX() >> 4) + 1L;
        long chunksZ = (bounds.max().getZ() >> 4) - (bounds.min().getZ() >> 4) + 1L;
        return chunksX * chunksZ;
    }
}
