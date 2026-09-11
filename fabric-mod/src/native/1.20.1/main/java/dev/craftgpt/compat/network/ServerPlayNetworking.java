package dev.craftgpt.compat.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public final class ServerPlayNetworking {
    private static final Map<ResourceLocation, Handler<?>> HANDLERS = new HashMap<>();
    private static final FragmentAssembler<UUID> ASSEMBLER = new FragmentAssembler<>();
    private static boolean initialized;
    private ServerPlayNetworking() {}
    public record Context(MinecraftServer server, ServerPlayer player) {}
    @FunctionalInterface public interface Handler<T> { void receive(T payload, Context context); }
    public static <T extends CustomPacketPayload> void registerGlobalReceiver(CustomPacketPayload.Type<T> type, Handler<T> handler) {
        if (HANDLERS.putIfAbsent(type.id(), handler) != null) throw new IllegalStateException("Duplicate packet handler");
        if (!initialized) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(NativeNetwork.CHANNEL,
                (server, player, connection, buffer, sender) -> {
                    try {
                        WireFrame frame = WireFrame.read(buffer);
                        if (buffer.isReadable()) throw new IllegalArgumentException("Trailing frame data");
                        server.execute(() -> {
                            if (server.getPlayerList().getPlayer(player.getUUID()) == player) {
                                receive(frame, new Context(server, player));
                            }
                        });
                    } catch (RuntimeException invalid) {
                        server.execute(() -> player.connection.disconnect(net.minecraft.network.chat.Component.literal("Invalid CraftGPT packet")));
                    }
                });
            ServerPlayConnectionEvents.DISCONNECT.register((handlerContext, server) -> ASSEMBLER.clear(handlerContext.player.getUUID()));
            net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> ASSEMBLER.clear());
            initialized = true;
        }
    }
    @SuppressWarnings("unchecked")
    private static void receive(WireFrame frame, Context context) {
        UUID peer = context.player().getUUID();
        try {
            var definition = PayloadTypeRegistry.playC2S().definition(frame.channel());
            var handler = (Handler<CustomPacketPayload>) HANDLERS.get(frame.channel());
            if (handler == null) throw new IllegalArgumentException("Unknown CraftGPT handler");
            byte[] bytes = ASSEMBLER.accept(peer, frame, definition.maximum());
            if (bytes != null) handler.receive(PacketTransport.decode(definition, bytes), context);
        } catch (RuntimeException invalid) {
            ASSEMBLER.clear(peer);
            context.player().connection.disconnect(net.minecraft.network.chat.Component.literal("Invalid CraftGPT packet"));
        }
    }
    public static boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, NativeNetwork.CHANNEL);
    }
    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        PacketTransport.send(PayloadTypeRegistry.playS2C(), payload,
            frame -> { var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer()); frame.write(buffer); net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, NativeNetwork.CHANNEL, buffer); });
    }
}
