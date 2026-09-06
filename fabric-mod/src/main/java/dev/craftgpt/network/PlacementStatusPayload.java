package dev.craftgpt.network;

import dev.craftgpt.CraftGptMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PlacementStatusPayload(
    String requestId,
    String placementId,
    String buildId,
    String status,
    int processed,
    int total,
    int conflicts
) implements CustomPacketPayload {
    public static final int MAX_STATUS_LENGTH = 64;
    public static final Type<PlacementStatusPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(CraftGptMod.MOD_ID, "placement_status")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, PlacementStatusPayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.stringUtf8(PlacementRequestPayload.MAX_REQUEST_ID_LENGTH), PlacementStatusPayload::requestId,
            ByteBufCodecs.stringUtf8(36), PlacementStatusPayload::placementId,
            ByteBufCodecs.stringUtf8(36), PlacementStatusPayload::buildId,
            ByteBufCodecs.stringUtf8(MAX_STATUS_LENGTH), PlacementStatusPayload::status,
            ByteBufCodecs.VAR_INT, PlacementStatusPayload::processed,
            ByteBufCodecs.VAR_INT, PlacementStatusPayload::total,
            ByteBufCodecs.VAR_INT, PlacementStatusPayload::conflicts,
            PlacementStatusPayload::new
        );

    public PlacementStatusPayload {
        requestId = requestId == null ? "" : requestId;
        placementId = placementId == null ? "" : placementId;
        buildId = buildId == null ? "" : buildId;
        status = status == null ? "invalid_request" : status;
        if (!requestId.isEmpty()) {
            PlacementRequestIds.validate(requestId);
        }
        if (!placementId.isEmpty()) {
            PlacementRequestIds.validateUuid(placementId, "placement ID");
        }
        if (!buildId.isEmpty()) {
            PlacementRequestIds.validateUuid(buildId, "build ID");
        }
        if (!status.matches("[a-z_]{1," + MAX_STATUS_LENGTH + "}")
            || processed < 0
            || total < 0
            || processed > total
            || conflicts < 0
            || conflicts > (long) total * 2L) {
            throw new IllegalArgumentException("Invalid placement status payload");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
