package dev.craftgpt.client.config;

import net.minecraft.network.chat.Component;

public enum ReasoningLevel {
    NONE("none"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    XHIGH("xhigh"),
    MAX("max");

    private final String serializedName;

    ReasoningLevel(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public Component displayName() {
        return Component.translatable("craftgpt.reasoning." + serializedName);
    }

    public ReasoningLevel next() {
        ReasoningLevel[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static ReasoningLevel parse(String value) {
        for (ReasoningLevel level : values()) {
            if (level.serializedName.equalsIgnoreCase(value)) {
                return level;
            }
        }
        return MEDIUM;
    }
}
