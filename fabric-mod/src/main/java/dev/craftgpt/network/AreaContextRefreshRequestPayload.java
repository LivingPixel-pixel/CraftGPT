package dev.craftgpt.network;

import dev.craftgpt.CraftGptMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** Requests a fresh read-only scan of the currently selected area. */
public record AreaContextRefreshRequestPayload(String requestId) implements CustomPacketPayload {
    public static final Type<AreaContextRefreshRequestPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(CraftGptMod.MOD_ID, "area_context_refresh")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, AreaContextRefreshRequestPayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.stringUtf8(36), AreaContextRefreshRequestPayload::requestId,
            AreaContextRefreshRequestPayload::new
        );

    public AreaContextRefreshRequestPayload {
        if (requestId == null || !UUID.fromString(requestId).toString().equals(requestId)) {
            throw new IllegalArgumentException("Invalid context refresh request ID");
        }
    }

    public static AreaContextRefreshRequestPayload create() {
        return new AreaContextRefreshRequestPayload(UUID.randomUUID().toString());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
