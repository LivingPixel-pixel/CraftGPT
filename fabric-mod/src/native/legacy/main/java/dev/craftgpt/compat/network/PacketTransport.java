package dev.craftgpt.compat.network;

import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;

public final class PacketTransport {
    private PacketTransport() {}
    @SuppressWarnings("unchecked")
    public static void send(PayloadTypeRegistry registry, CustomPacketPayload payload, Consumer<WireFrame> output) {
        var definition = (PayloadTypeRegistry.Definition<CustomPacketPayload>) registry.definition(payload.type().id());
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            definition.codec().encode(buffer, payload);
            int length = buffer.readableBytes();
            if (length < 1 || length > definition.maximum()) throw new IllegalArgumentException("Oversized CraftGPT packet");
            byte[] bytes = new byte[length];
            buffer.readBytes(bytes);
            UUID transfer = UUID.randomUUID();
            for (int offset = 0; offset < length; offset += WireFrame.CHUNK_BYTES) {
                output.accept(new WireFrame(payload.type().id(), transfer, length, offset,
                    Arrays.copyOfRange(bytes, offset, Math.min(length, offset + WireFrame.CHUNK_BYTES))));
            }
        } finally { buffer.release(); }
    }
    public static CustomPacketPayload decode(PayloadTypeRegistry.Definition<?> definition, byte[] bytes) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        try {
            var result = definition.codec().decode(buffer);
            if (buffer.isReadable()) throw new IllegalArgumentException("Trailing CraftGPT packet data");
            return result;
        } finally { buffer.release(); }
    }
}
