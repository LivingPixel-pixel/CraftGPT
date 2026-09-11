package dev.craftgpt.compat.network;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record WireFrame(ResourceLocation channel, UUID transfer, int total, int offset, byte[] bytes) {
    public static final int CHUNK_BYTES = 24_000;
    public WireFrame {
        if (channel == null || transfer == null || bytes == null || bytes.length == 0 || bytes.length > CHUNK_BYTES
            || total < 1 || total > FragmentAssembler.MAX_MESSAGE_BYTES || offset < 0
            || offset > total - bytes.length) throw new IllegalArgumentException("Invalid CraftGPT frame");
        bytes = bytes.clone();
    }
    @Override public byte[] bytes() { return bytes.clone(); }
    public void write(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(channel);
        buffer.writeUUID(transfer);
        buffer.writeVarInt(total);
        buffer.writeVarInt(offset);
        buffer.writeByteArray(bytes);
    }
    public static WireFrame read(FriendlyByteBuf buffer) {
        return new WireFrame(buffer.readResourceLocation(), buffer.readUUID(), buffer.readVarInt(),
            buffer.readVarInt(), buffer.readByteArray(CHUNK_BYTES));
    }
}
