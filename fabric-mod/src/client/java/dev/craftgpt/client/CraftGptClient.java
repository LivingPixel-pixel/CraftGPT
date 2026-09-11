package dev.craftgpt.client;

import dev.craftgpt.client.platform.ClientPlatform;

import dev.craftgpt.client.area.ClientAreaState;
import dev.craftgpt.client.config.CraftGptConfig;
import dev.craftgpt.client.build.preview.GhostPreviewManager;
import dev.craftgpt.client.planning.PlanningController;
import dev.craftgpt.client.planning.storage.WorldScopeResolver;
import dev.craftgpt.client.ui.CraftGptSettingsScreen;
import dev.craftgpt.client.ui.CraftBookScreen;
import dev.craftgpt.network.AreaContextPayload;
import dev.craftgpt.network.AreaSelectionPayload;
import dev.craftgpt.network.OpenSettingsPayload;
import dev.craftgpt.network.PlanningActionPayload;
import dev.craftgpt.network.BuildPreviewResponsePayload;
import dev.craftgpt.network.PlacementStatusPayload;
import dev.craftgpt.network.PlacementHistoryPayload;
import dev.craftgpt.network.PlacementInspectPayload;
import dev.craftgpt.network.OpenCraftBookPayload;
import dev.craftgpt.client.placement.RecoveryVisualizationState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;

public final class CraftGptClient implements ClientModInitializer {
    private static CraftGptConfig config;
    private static PlanningController planningController;

    @Override
    public void onInitializeClient() {
        config = CraftGptConfig.load();
        planningController = new PlanningController(config);
        GhostPreviewManager.INSTANCE.register();

        ClientPlayNetworking.registerGlobalReceiver(AreaSelectionPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                try {
                    AreaSelectionPayload validated = payload.validate();
                    ClientAreaState.INSTANCE.update(validated);
                    if (validated.state() == AreaSelectionPayload.CLEARED
                        || validated.state() == AreaSelectionPayload.STARTED) {
                        planningController.clearContext();
                    }
                } catch (RuntimeException ignored) {
                    ClientAreaState.INSTANCE.clear();
                    planningController.rejectContext();
                }
            }));

        ClientPlayNetworking.registerGlobalReceiver(AreaContextPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                try {
                    planningController.updateContext(payload.decodeValidated());
                } catch (RuntimeException ignored) {
                    planningController.rejectContext();
                }
            }));

        ClientPlayNetworking.registerGlobalReceiver(PlanningActionPayload.TYPE, (payload, context) ->
            context.client().execute(() -> planningController.handleAction(context.client(), payload)));

        ClientPlayNetworking.registerGlobalReceiver(BuildPreviewResponsePayload.TYPE, (payload, context) ->
            context.client().execute(() ->
                planningController.handleBuildValidationResponse(context.client(), payload)));

        ClientPlayNetworking.registerGlobalReceiver(PlacementStatusPayload.TYPE, (payload, context) ->
            context.client().execute(() ->
                planningController.handlePlacementStatus(context.client(), payload)));

        ClientPlayNetworking.registerGlobalReceiver(PlacementHistoryPayload.TYPE, (payload, context) ->
            context.client().execute(() ->
                planningController.handlePlacementHistory(context.client(), payload)));

        ClientPlayNetworking.registerGlobalReceiver(PlacementInspectPayload.TYPE, (payload, context) ->
            context.client().execute(() -> RecoveryVisualizationState.INSTANCE.handle(payload)));

        ClientPlayNetworking.registerGlobalReceiver(OpenSettingsPayload.TYPE, (payload, context) -> {
            if (payload.open()) {
                context.client().execute(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (ClientPlatform.screen(minecraft) == null || ClientPlatform.screen(minecraft) instanceof ChatScreen) {
                        ClientPlatform.setScreen(minecraft, new CraftGptSettingsScreen(ClientPlatform.screen(minecraft), config));
                    }
                });
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(OpenCraftBookPayload.TYPE, (payload, context) -> {
            if (payload.open()) {
                context.client().execute(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (ClientPlatform.screen(minecraft) == null || ClientPlatform.screen(minecraft) instanceof ChatScreen) {
                        ClientPlatform.setScreen(minecraft, new CraftBookScreen(ClientPlatform.screen(minecraft), planningController, config));
                    }
                });
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(ClientAreaState.INSTANCE::tick);
        ClientTickEvents.END_CLIENT_TICK.register(RecoveryVisualizationState.INSTANCE::tick);
        ClientTickEvents.END_CLIENT_TICK.register(planningController::tick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, minecraft) -> {
            try {
                planningController.setWorldScope(WorldScopeResolver.resolve(minecraft));
                planningController.refreshPlacementStatus();
            } catch (RuntimeException ignored) {
                planningController.disconnect();
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> {
            ClientAreaState.INSTANCE.clear();
            RecoveryVisualizationState.INSTANCE.clear();
            planningController.disconnect();
        });
    }

    public static CraftGptConfig config() {
        return config;
    }

    public static PlanningController planningController() {
        return planningController;
    }
}
