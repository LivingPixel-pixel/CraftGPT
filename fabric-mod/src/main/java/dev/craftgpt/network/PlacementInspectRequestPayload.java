package dev.craftgpt.network;

import dev.craftgpt.CraftGptMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PlacementInspectRequestPayload(String requestId, String placementId)
    implements CustomPacketPayload {
    public static final Type<PlacementInspectRequestPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(CraftGptMod.MOD_ID, "placement_inspect_request")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, PlacementInspectRequestPayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.stringUtf8(PlacementRequestPayload.MAX_REQUEST_ID_LENGTH),
            PlacementInspectRequestPayload::requestId,
            ByteBufCodecs.stringUtf8(36), PlacementInspectRequestPayload::placementId,
            PlacementInspectRequestPayload::new
        );

    public PlacementInspectRequestPayload {
        PlacementRequestIds.validate(requestId);
        PlacementRequestIds.validateUuid(placementId, "placement ID");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
