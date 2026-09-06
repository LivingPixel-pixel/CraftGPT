package dev.craftgpt.network;

import dev.craftgpt.CraftGptMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Set;

public record PlacementRecoveryRequestPayload(
    String requestId,
    String placementId,
    String action
) implements CustomPacketPayload {
    public static final String UNDO = "undo";
    public static final String REDO = "redo";
    private static final Set<String> ACTIONS = Set.of(UNDO, REDO);

    public static final Type<PlacementRecoveryRequestPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(CraftGptMod.MOD_ID, "placement_recovery_request")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, PlacementRecoveryRequestPayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.stringUtf8(PlacementRequestPayload.MAX_REQUEST_ID_LENGTH),
            PlacementRecoveryRequestPayload::requestId,
            ByteBufCodecs.stringUtf8(36), PlacementRecoveryRequestPayload::placementId,
            ByteBufCodecs.stringUtf8(8), PlacementRecoveryRequestPayload::action,
            PlacementRecoveryRequestPayload::new
        );

    public PlacementRecoveryRequestPayload {
        PlacementRequestIds.validate(requestId);
        PlacementRequestIds.validateUuid(placementId, "placement ID");
        if (!ACTIONS.contains(action)) {
            throw new IllegalArgumentException("Invalid recovery action");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
