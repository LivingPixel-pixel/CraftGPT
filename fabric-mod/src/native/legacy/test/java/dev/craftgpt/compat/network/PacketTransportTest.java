package dev.craftgpt.compat.network;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PacketTransportTest {
    private static final ResourceLocation CHANNEL = NativeNetwork.id("craftgpt", "test");
    record Text(String value) implements CustomPacketPayload {
        static final Type<Text> TYPE = new Type<>(CHANNEL);
        public Type<Text> type() { return TYPE; }
    }
    @Test void transfersLargeUnicodePayloadWithoutChangingBytes() {
        var registry = new PayloadTypeRegistry();
        StreamCodec<FriendlyByteBuf, Text> codec = StreamCodec.composite(ByteBufCodecs.stringUtf8(2_000_000), Text::value, Text::new);
        registry.registerLarge(Text.TYPE, codec, 2_000_005);
        Text expected = new Text("Minecraft 雪 🧱 ".repeat(50_000));
        var frames = new ArrayList<WireFrame>();
        PacketTransport.send(registry, expected, frames::add);
        assertTrue(frames.size() > 10);
        var assembler = new FragmentAssembler<String>();
        byte[] received = null;
        for (int index = 0; index < frames.size(); index++) {
            var frame = frames.get(index);
            assertTrue(frame.bytes().length <= WireFrame.CHUNK_BYTES);
            received = assembler.accept("peer", frame, 2_000_005);
            if (index < frames.size() - 1) assertNull(received);
        }
        assertEquals(expected, PacketTransport.decode(registry.definition(CHANNEL), received));
        byte[] trailing = java.util.Arrays.copyOf(received, received.length + 1);
        assertThrows(IllegalArgumentException.class, () -> PacketTransport.decode(registry.definition(CHANNEL), trailing));
    }
    @Test void rejectsOutOfOrderDuplicateMismatchedAndOversizedFrames() {
        var assembler = new FragmentAssembler<String>();
        UUID transfer = UUID.randomUUID();
        WireFrame start = new WireFrame(CHANNEL, transfer, 4, 0, new byte[]{1, 2});
        WireFrame end = new WireFrame(CHANNEL, transfer, 4, 2, new byte[]{3, 4});
        assertThrows(IllegalArgumentException.class, () -> assembler.accept("peer", end, 4));
        assertNull(assembler.accept("peer", start, 4));
        assertThrows(IllegalArgumentException.class, () -> assembler.accept("peer", start, 4));
        assertThrows(IllegalArgumentException.class, () -> assembler.accept("peer", start, 3));
        assertNull(assembler.accept("peer", start, 4));
        var wrong = new WireFrame(CHANNEL, UUID.randomUUID(), 4, 2, new byte[]{3, 4});
        assertThrows(IllegalArgumentException.class, () -> assembler.accept("peer", wrong, 4));
        assertNull(assembler.accept("peer", start, 4));
        assertArrayEquals(new byte[]{1, 2, 3, 4}, assembler.accept("peer", end, 4));
    }
    @Test void expiresAndClearsConnectionState() {
        var time = new AtomicLong();
        var assembler = new FragmentAssembler<String>(time::get);
        UUID transfer = UUID.randomUUID();
        var start = new WireFrame(CHANNEL, transfer, 4, 0, new byte[]{1, 2});
        var end = new WireFrame(CHANNEL, transfer, 4, 2, new byte[]{3, 4});
        assembler.accept("a", start, 4);
        assertThrows(IllegalArgumentException.class, () -> assembler.accept("b", end, 4));
        time.set(31_000_000_000L);
        assertThrows(IllegalArgumentException.class, () -> assembler.accept("a", end, 4));
        assembler.accept("a", start, 4);
        assembler.clear("a");
        assertThrows(IllegalArgumentException.class, () -> assembler.accept("a", end, 4));
    }
    @Test void capsTotalMemoryAndRejectsUnknownChannels() {
        var assembler = new FragmentAssembler<Integer>();
        var frame = new WireFrame(CHANNEL, UUID.randomUUID(), 2_100_000, 0, new byte[]{1});
        for (int peer = 0; peer < 15; peer++) assertNull(assembler.accept(peer, frame, 2_100_000));
        assertThrows(IllegalArgumentException.class, () -> assembler.accept(16, frame, 2_100_000));
        assertThrows(IllegalArgumentException.class, () -> new PayloadTypeRegistry().definition(CHANNEL));
        assembler.clear();
        assertNull(assembler.accept(16, frame, 2_100_000));
    }
}
