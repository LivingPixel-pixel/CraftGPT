package dev.craftgpt.compat.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class NativeNetwork {
    private static boolean initialized;
    private NativeNetwork() {}
    public static ResourceLocation id(String namespace, String path) { return ResourceLocation.fromNamespaceAndPath(namespace, path); }
    public static ResourceLocation parse(String value) { return ResourceLocation.parse(value); }
    public static synchronized void initialize() {
        if (initialized) return;
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(Frame.TYPE, Frame.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(Frame.TYPE, Frame.CODEC);
        initialized = true;
    }
    public record Frame(WireFrame value) implements CustomPacketPayload {
        public static final Type<Frame> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("craftgpt", "transport_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Frame> CODEC = new StreamCodec<>() {
            public Frame decode(RegistryFriendlyByteBuf buffer) { return new Frame(WireFrame.read(buffer)); }
            public void encode(RegistryFriendlyByteBuf buffer, Frame frame) { frame.value.write(buffer); }
        };
        @Override public Type<Frame> type() { return TYPE; }
    }
}
