package dev.craftgpt.compat.network;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Direction-specific allowlist and encoded size limits, populated during mod initialization. */
public final class PayloadTypeRegistry {
    private static final PayloadTypeRegistry C2S = new PayloadTypeRegistry();
    private static final PayloadTypeRegistry S2C = new PayloadTypeRegistry();
    private final Map<ResourceLocation, Definition<?>> definitions = new HashMap<>();
    PayloadTypeRegistry() {}
    public static PayloadTypeRegistry playC2S() { NativeNetwork.initialize(); return C2S; }
    public static PayloadTypeRegistry playS2C() { NativeNetwork.initialize(); return S2C; }

    public <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type, StreamCodec<FriendlyByteBuf, T> codec) {
        registerLarge(type, codec, 1_048_576);
    }
    public <T extends CustomPacketPayload> void registerLarge(CustomPacketPayload.Type<T> type, StreamCodec<FriendlyByteBuf, T> codec, int maximum) {
        if (maximum < 1 || maximum > FragmentAssembler.MAX_MESSAGE_BYTES
            || definitions.putIfAbsent(type.id(), new Definition<>(codec, maximum)) != null) {
            throw new IllegalStateException("Invalid or duplicate CraftGPT packet registration: " + type.id());
        }
    }
    public Definition<?> definition(ResourceLocation channel) {
        var result = definitions.get(channel);
        if (result == null) throw new IllegalArgumentException("Unregistered CraftGPT packet: " + channel);
        return result;
    }
    public record Definition<T extends CustomPacketPayload>(StreamCodec<FriendlyByteBuf, T> codec, int maximum) {}
}
