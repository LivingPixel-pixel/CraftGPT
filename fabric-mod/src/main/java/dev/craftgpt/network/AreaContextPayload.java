package dev.craftgpt.network;

import dev.craftgpt.CraftGptMod;
import dev.craftgpt.context.AreaContext;
import dev.craftgpt.context.AreaContextJson;
import dev.craftgpt.context.AreaContextValidator;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record AreaContextPayload(String json) implements CustomPacketPayload {
    public static final Type<AreaContextPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(CraftGptMod.MOD_ID, "area_context")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, AreaContextPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(2_000_000),
        AreaContextPayload::json,
        AreaContextPayload::new
    );

    public static AreaContextPayload from(AreaContext context) {
        return new AreaContextPayload(AreaContextJson.encode(AreaContextValidator.validate(context)));
    }

    public AreaContext decode() {
        return AreaContextJson.decode(json);
    }

    public AreaContext decodeValidated() {
        return AreaContextValidator.validate(decode());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
