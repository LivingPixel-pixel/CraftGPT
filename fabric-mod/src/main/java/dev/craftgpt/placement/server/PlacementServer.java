package dev.craftgpt.placement.server;

import dev.craftgpt.network.PlacementRequestPayload;
import dev.craftgpt.network.PlacementHistoryRequestPayload;
import dev.craftgpt.network.PlacementRecoveryRequestPayload;
import dev.craftgpt.network.PlacementStatusRequestPayload;
import dev.craftgpt.network.PlacementUndoRequestPayload;
import dev.craftgpt.network.PlacementMaintenanceRequestPayload;
import dev.craftgpt.network.PlacementInspectRequestPayload;
import dev.craftgpt.placement.PlacementStatusCodes;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Registers the destructive Phase-5 protocol. No server packet can trigger an API call. */
public final class PlacementServer {
    private PlacementServer() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(PlacementManager.INSTANCE::startServer);
        ServerLifecycleEvents.SERVER_STOPPING.register(PlacementManager.INSTANCE::stopServer);
        ServerTickEvents.END_SERVER_TICK.register(PlacementManager.INSTANCE::tick);

        ServerPlayNetworking.registerGlobalReceiver(PlacementRequestPayload.TYPE, (payload, context) -> {
            String requestId = payload.requestIdOrEmpty();
            try {
                PlacementRequestPayload.RequestBody request = payload.decodeValidated();
                PlacementManager.INSTANCE.requestPlacement(
                    context.player(),
                    request.requestId(),
                    request.buildId(),
                    request.allowBlockEntityReplacement(),
                    request.build()
                );
            } catch (RuntimeException exception) {
                PlacementManager.INSTANCE.sendError(
                    context.player(),
                    requestId,
                    PlacementStatusCodes.INVALID_REQUEST
                );
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(PlacementUndoRequestPayload.TYPE, (payload, context) ->
            PlacementManager.INSTANCE.requestUndo(context.player(), payload.requestId()));

        ServerPlayNetworking.registerGlobalReceiver(PlacementStatusRequestPayload.TYPE, (payload, context) ->
            PlacementManager.INSTANCE.requestStatus(context.player(), payload.requestId()));

        ServerPlayNetworking.registerGlobalReceiver(PlacementHistoryRequestPayload.TYPE, (payload, context) ->
            PlacementManager.INSTANCE.requestHistory(context.player(), payload.requestId()));

        ServerPlayNetworking.registerGlobalReceiver(PlacementRecoveryRequestPayload.TYPE, (payload, context) ->
            PlacementManager.INSTANCE.requestRecovery(
                context.player(),
                payload.requestId(),
                payload.placementId(),
                payload.action()
            ));

        ServerPlayNetworking.registerGlobalReceiver(PlacementMaintenanceRequestPayload.TYPE, (payload, context) ->
            PlacementManager.INSTANCE.requestMaintenance(
                context.player(), payload.requestId(), payload.placementId(), payload.action(), payload.retentionCount()));

        ServerPlayNetworking.registerGlobalReceiver(PlacementInspectRequestPayload.TYPE, (payload, context) ->
            PlacementManager.INSTANCE.requestInspect(context.player(), payload.requestId(), payload.placementId()));
    }
}
