package dev.craftgpt.network;

import dev.craftgpt.CraftGptMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PlacementUndoRequestPayload(String requestId) implements CustomPacketPayload {
    public static final Type<PlacementUndoRequestPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(CraftGptMod.MOD_ID, "placement_undo_request")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, PlacementUndoRequestPayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.stringUtf8(PlacementRequestPayload.MAX_REQUEST_ID_LENGTH),
            PlacementUndoRequestPayload::requestId,
            PlacementUndoRequestPayload::new
        );

    public PlacementUndoRequestPayload {
        PlacementRequestIds.validate(requestId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
