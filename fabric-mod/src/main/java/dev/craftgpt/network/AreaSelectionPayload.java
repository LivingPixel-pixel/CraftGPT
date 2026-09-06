package dev.craftgpt.network;

import dev.craftgpt.CraftGptMod;
import dev.craftgpt.area.AreaSelection;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record AreaSelectionPayload(int state, BlockPos start, BlockPos end, String dimension, String selectionId)
    implements CustomPacketPayload {

    public static final int CLEARED = 0;
    public static final int STARTED = 1;
    public static final int COMPLETE = 2;

    public static final Type<AreaSelectionPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(CraftGptMod.MOD_ID, "area_selection")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, AreaSelectionPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, AreaSelectionPayload::state,
        BlockPos.STREAM_CODEC, AreaSelectionPayload::start,
        BlockPos.STREAM_CODEC, AreaSelectionPayload::end,
        ByteBufCodecs.stringUtf8(256), AreaSelectionPayload::dimension,
        ByteBufCodecs.stringUtf8(64), AreaSelectionPayload::selectionId,
        AreaSelectionPayload::new
    );

    public static AreaSelectionPayload cleared() {
        return new AreaSelectionPayload(CLEARED, BlockPos.ZERO, BlockPos.ZERO, "", "");
    }

    public static AreaSelectionPayload from(AreaSelection selection) {
        return new AreaSelectionPayload(
            selection.isComplete() ? COMPLETE : STARTED,
            selection.start(),
            selection.end().orElse(selection.start()),
            selection.dimension(),
            selection.selectionId()
        ).validate();
    }

    public AreaSelectionPayload validate() {
        require(start != null && end != null, "Area selection has missing positions");
        if (state == CLEARED) {
            require(dimension != null && dimension.isEmpty(), "Cleared area has a dimension");
            require(selectionId != null && selectionId.isEmpty(), "Cleared area has a selection id");
            return this;
        }

        require(state == STARTED || state == COMPLETE, "Unknown area-selection state");
        Identifier dimensionId = dimension == null ? null : Identifier.tryParse(dimension);
        require(dimensionId != null && dimensionId.toString().equals(dimension), "Invalid area dimension");
        require(selectionId != null, "Invalid area selection id");
        try {
            UUID parsed = UUID.fromString(selectionId);
            require(parsed.toString().equalsIgnoreCase(selectionId), "Invalid area selection id");
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid area selection id", exception);
        }

        if (state == COMPLETE) {
            long width = axisLength(start.getX(), end.getX());
            long height = axisLength(start.getY(), end.getY());
            long depth = axisLength(start.getZ(), end.getZ());
            require(width <= 128 && height <= 128 && depth <= 128, "Area-selection axis exceeds limit");
            require(width * height * depth <= 32_768L, "Area-selection volume exceeds limit");
        }
        return this;
    }

    private static long axisLength(int first, int second) {
        return Math.abs((long) first - second) + 1L;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
