package dev.craftgpt.compat.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

public final class ByteBufCodecs {
    private ByteBufCodecs() {}
    public static final StreamCodec<FriendlyByteBuf, Boolean> BOOL = StreamCodec.of(FriendlyByteBuf::writeBoolean, FriendlyByteBuf::readBoolean);
    public static final StreamCodec<FriendlyByteBuf, Integer> VAR_INT = StreamCodec.of(FriendlyByteBuf::writeVarInt, FriendlyByteBuf::readVarInt);
    public static final StreamCodec<FriendlyByteBuf, BlockPos> BLOCK_POS = StreamCodec.of((buffer, value) -> buffer.writeBlockPos(value), buffer -> buffer.readBlockPos());
    public static StreamCodec<FriendlyByteBuf, String> stringUtf8(int maximum) {
        return StreamCodec.of((buffer, text) -> buffer.writeUtf(text, maximum), buffer -> buffer.readUtf(maximum));
    }
}
