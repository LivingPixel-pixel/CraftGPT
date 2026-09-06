package dev.craftgpt.network;

import dev.craftgpt.CraftGptMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenCraftBookPayload(boolean open) implements CustomPacketPayload {
    public static final Type<OpenCraftBookPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(CraftGptMod.MOD_ID, "open_craft_book")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenCraftBookPayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.BOOL, OpenCraftBookPayload::open,
            OpenCraftBookPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
