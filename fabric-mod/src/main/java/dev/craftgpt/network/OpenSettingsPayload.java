package dev.craftgpt.network;

import dev.craftgpt.CraftGptMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenSettingsPayload(boolean open) implements CustomPacketPayload {
    public static final Type<OpenSettingsPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(CraftGptMod.MOD_ID, "open_settings")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenSettingsPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL,
        OpenSettingsPayload::open,
        OpenSettingsPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
