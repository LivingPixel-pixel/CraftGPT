package dev.craftgpt.network;

import dev.craftgpt.CraftGptMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PlanningActionPayload(String action, String value) implements CustomPacketPayload {
    public static final int MAX_ACTION_LENGTH = 32;
    public static final int MAX_VALUE_LENGTH = 8_000;
    public static final String OPEN = "open";
    public static final String PROMPT = "prompt";
    public static final String DISCUSS = "discuss";
    public static final String VERSIONS = "versions";
    public static final String REVERT = "revert";
    public static final String PREVIEW = "preview";
    public static final String PLACE = "place";
    public static final String UNDO = "undo";
    public static final String HISTORY = "history";
    public static final String EXPORT = "export";
    public static final String IMPORT = "import";
    public static final String EXCHANGE = "exchange";

    public static final Type<PlanningActionPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(CraftGptMod.MOD_ID, "planning_action")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, PlanningActionPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(MAX_ACTION_LENGTH), PlanningActionPayload::action,
        ByteBufCodecs.stringUtf8(MAX_VALUE_LENGTH), PlanningActionPayload::value,
        PlanningActionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
