package dev.craftgpt.network;

import dev.craftgpt.CraftGptMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Map;

/** A closed action set: the client can invoke CraftGPT commands, never arbitrary server text. */
public record CraftBookActionPayload(String action) implements CustomPacketPayload {
    public static final String AREA_START = "area_start";
    public static final String AREA_STOP = "area_stop";
    public static final String AREA_CLEAR = "area_clear";
    public static final String PLAN = "plan";
    public static final String VERSIONS = "versions";
    public static final String PREVIEW = "preview";
    public static final String HISTORY = "history";

    private static final Map<String, String> COMMANDS = Map.of(
        AREA_START, "craftgpt setArea start",
        AREA_STOP, "craftgpt setArea stop",
        AREA_CLEAR, "craftgpt area clear",
        PLAN, "craftgpt plan",
        VERSIONS, "craftgpt versions",
        PREVIEW, "craftgpt preview",
        HISTORY, "craftgpt history"
    );

    public static final Type<CraftBookActionPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(CraftGptMod.MOD_ID, "craft_book_action")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, CraftBookActionPayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.stringUtf8(24), CraftBookActionPayload::action,
            CraftBookActionPayload::new
        );

    public CraftBookActionPayload {
        if (!COMMANDS.containsKey(action)) throw new IllegalArgumentException("Unknown CraftBook action");
    }

    public String command() { return COMMANDS.get(action); }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
