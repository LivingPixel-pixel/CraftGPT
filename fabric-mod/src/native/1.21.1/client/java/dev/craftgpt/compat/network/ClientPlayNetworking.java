package dev.craftgpt.compat.network;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

public final class ClientPlayNetworking {
    private static final Map<ResourceLocation, Handler<?>> HANDLERS = new HashMap<>();
    private static final FragmentAssembler<Integer> ASSEMBLER = new FragmentAssembler<>();
    private static boolean initialized;
    private ClientPlayNetworking() {}
    public record Context(Minecraft client) {}
    @FunctionalInterface public interface Handler<T> { void receive(T payload, Context context); }
    public static <T extends CustomPacketPayload> void registerGlobalReceiver(CustomPacketPayload.Type<T> type, Handler<T> handler) {
        if (HANDLERS.putIfAbsent(type.id(), handler) != null) throw new IllegalStateException("Duplicate packet handler");
        if (!initialized) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(NativeNetwork.Frame.TYPE,
                (payload, context) -> receive(payload.value(), new Context(context.client())));
            ClientPlayConnectionEvents.DISCONNECT.register((connection, client) -> ASSEMBLER.clear());
            initialized = true;
        }
    }
    @SuppressWarnings("unchecked")
    private static void receive(WireFrame frame, Context context) {
        try {
            var definition = PayloadTypeRegistry.playS2C().definition(frame.channel());
            var handler = (Handler<CustomPacketPayload>) HANDLERS.get(frame.channel());
            if (handler == null) throw new IllegalArgumentException("Unknown CraftGPT handler");
            byte[] bytes = ASSEMBLER.accept(0, frame, definition.maximum());
            if (bytes != null) handler.receive(PacketTransport.decode(definition, bytes), context);
        } catch (RuntimeException invalid) {
            ASSEMBLER.clear();
            var connection = context.client().getConnection();
            if (connection != null) connection.getConnection().disconnect(net.minecraft.network.chat.Component.literal("Invalid CraftGPT packet"));
        }
    }
    public static boolean canSend(CustomPacketPayload.Type<?> type) {
        return net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(NativeNetwork.Frame.TYPE);
    }
    public static void send(CustomPacketPayload payload) {
        PacketTransport.send(PayloadTypeRegistry.playC2S(), payload,
            frame -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new NativeNetwork.Frame(frame)));
    }
}
