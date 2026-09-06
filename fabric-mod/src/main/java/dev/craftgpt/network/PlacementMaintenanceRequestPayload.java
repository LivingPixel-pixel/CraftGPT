package dev.craftgpt.network;

import dev.craftgpt.CraftGptMod;
import dev.craftgpt.placement.PlacementLimits;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PlacementMaintenanceRequestPayload(
    String requestId,
    String placementId,
    String action,
    int retentionCount
) implements CustomPacketPayload {
    public static final String EXPORT = "export";
    public static final String IMPORT = "import";
    public static final String PRUNE = "prune";
    public static final Type<PlacementMaintenanceRequestPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(CraftGptMod.MOD_ID, "placement_maintenance")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, PlacementMaintenanceRequestPayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.stringUtf8(PlacementRequestPayload.MAX_REQUEST_ID_LENGTH),
            PlacementMaintenanceRequestPayload::requestId,
            ByteBufCodecs.stringUtf8(36), PlacementMaintenanceRequestPayload::placementId,
            ByteBufCodecs.stringUtf8(16), PlacementMaintenanceRequestPayload::action,
            ByteBufCodecs.VAR_INT, PlacementMaintenanceRequestPayload::retentionCount,
            PlacementMaintenanceRequestPayload::new
        );

    public PlacementMaintenanceRequestPayload {
        PlacementRequestIds.validate(requestId);
        if (!placementId.isEmpty()) {
            PlacementRequestIds.validateUuid(placementId, "placement ID");
        }
        if (!EXPORT.equals(action) && !IMPORT.equals(action) && !PRUNE.equals(action)) {
            throw new IllegalArgumentException("Invalid maintenance action");
        }
        if (PRUNE.equals(action)) {
            if (!placementId.isEmpty()
                || retentionCount < PlacementLimits.MIN_RETENTION_COUNT
                || retentionCount > PlacementLimits.MAX_RETENTION_COUNT) {
                throw new IllegalArgumentException("Invalid retention request");
            }
        } else if (placementId.isEmpty() || retentionCount != 0) {
            throw new IllegalArgumentException("Invalid placement maintenance request");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
