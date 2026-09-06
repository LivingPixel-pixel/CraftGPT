package dev.craftgpt.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class CraftGptNetworking {
    private CraftGptNetworking() {
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.clientboundPlay().register(AreaSelectionPayload.TYPE, AreaSelectionPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AreaContextPayload.TYPE, AreaContextPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
            AreaContextRefreshRequestPayload.TYPE,
            AreaContextRefreshRequestPayload.CODEC
        );
        PayloadTypeRegistry.clientboundPlay().register(PlanningActionPayload.TYPE, PlanningActionPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenSettingsPayload.TYPE, OpenSettingsPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenCraftBookPayload.TYPE, OpenCraftBookPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CraftBookActionPayload.TYPE, CraftBookActionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().registerLarge(
            BuildPreviewRequestPayload.TYPE,
            BuildPreviewRequestPayload.CODEC,
            BuildPreviewRequestPayload.MAX_PACKET_BYTES
        );
        PayloadTypeRegistry.clientboundPlay().register(
            BuildPreviewResponsePayload.TYPE,
            BuildPreviewResponsePayload.CODEC
        );
        PayloadTypeRegistry.serverboundPlay().registerLarge(
            PlacementRequestPayload.TYPE,
            PlacementRequestPayload.CODEC,
            PlacementRequestPayload.MAX_PACKET_BYTES
        );
        PayloadTypeRegistry.serverboundPlay().register(
            PlacementUndoRequestPayload.TYPE,
            PlacementUndoRequestPayload.CODEC
        );
        PayloadTypeRegistry.serverboundPlay().register(
            PlacementStatusRequestPayload.TYPE,
            PlacementStatusRequestPayload.CODEC
        );
        PayloadTypeRegistry.clientboundPlay().register(
            PlacementStatusPayload.TYPE,
            PlacementStatusPayload.CODEC
        );
        PayloadTypeRegistry.serverboundPlay().register(
            PlacementHistoryRequestPayload.TYPE,
            PlacementHistoryRequestPayload.CODEC
        );
        PayloadTypeRegistry.serverboundPlay().register(
            PlacementRecoveryRequestPayload.TYPE,
            PlacementRecoveryRequestPayload.CODEC
        );
        PayloadTypeRegistry.clientboundPlay().registerLarge(
            PlacementHistoryPayload.TYPE,
            PlacementHistoryPayload.CODEC,
            PlacementHistoryPayload.MAX_JSON_BYTES + 128
        );
        PayloadTypeRegistry.serverboundPlay().register(
            PlacementMaintenanceRequestPayload.TYPE,
            PlacementMaintenanceRequestPayload.CODEC
        );
        PayloadTypeRegistry.serverboundPlay().register(
            PlacementInspectRequestPayload.TYPE,
            PlacementInspectRequestPayload.CODEC
        );
        PayloadTypeRegistry.clientboundPlay().register(
            PlacementInspectPayload.TYPE,
            PlacementInspectPayload.CODEC
        );
    }
}
