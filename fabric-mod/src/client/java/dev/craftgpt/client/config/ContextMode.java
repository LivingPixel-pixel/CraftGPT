package dev.craftgpt.client.config;

import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum ContextMode {
    COMPRESSED,
    FULL;

    public static ContextMode parse(String value) {
        if (value == null) {
            return COMPRESSED;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return COMPRESSED;
        }
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public ContextMode next() {
        return this == COMPRESSED ? FULL : COMPRESSED;
    }

    public boolean full() {
        return this == FULL;
    }

    public Component displayName() {
        return Component.translatable(full()
            ? "craftgpt.context_mode.full"
            : "craftgpt.context_mode.compressed");
    }
}
