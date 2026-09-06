package dev.craftgpt.network;

import dev.craftgpt.CraftGptMod;
import dev.craftgpt.placement.PlacementLimits;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PlacementInspectPayload(
    String requestId,
    String placementId,
    String dimension,
    int minX, int minY, int minZ,
    int maxX, int maxY, int maxZ
) implements CustomPacketPayload {
    public static final Type<PlacementInspectPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(CraftGptMod.MOD_ID, "placement_inspect")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, PlacementInspectPayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.stringUtf8(PlacementRequestPayload.MAX_REQUEST_ID_LENGTH), PlacementInspectPayload::requestId,
            ByteBufCodecs.stringUtf8(36), PlacementInspectPayload::placementId,
            ByteBufCodecs.stringUtf8(PlacementLimits.MAX_DIMENSION_LENGTH), PlacementInspectPayload::dimension,
            ByteBufCodecs.VAR_INT, PlacementInspectPayload::minX,
            ByteBufCodecs.VAR_INT, PlacementInspectPayload::minY,
            ByteBufCodecs.VAR_INT, PlacementInspectPayload::minZ,
            ByteBufCodecs.VAR_INT, PlacementInspectPayload::maxX,
            ByteBufCodecs.VAR_INT, PlacementInspectPayload::maxY,
            ByteBufCodecs.VAR_INT, PlacementInspectPayload::maxZ,
            PlacementInspectPayload::new
        );

    public PlacementInspectPayload {
        PlacementRequestIds.validate(requestId);
        PlacementRequestIds.validateUuid(placementId, "placement ID");
        if (Identifier.tryParse(dimension) == null || dimension.length() > PlacementLimits.MAX_DIMENSION_LENGTH
            || minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Invalid placement inspection");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
