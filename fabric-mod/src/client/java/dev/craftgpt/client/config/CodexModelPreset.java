package dev.craftgpt.client.config;

import net.minecraft.network.chat.Component;

/** Human-readable Codex model choices with an explicit custom escape hatch. */
public enum CodexModelPreset {
    ASTRA("gpt-6-astra", "astra"),
    SOL("gpt-5.6-sol", "sol"),
    TERRA("gpt-5.6-terra", "terra"),
    LUNA("gpt-5.6-luna", "luna"),
    CUSTOM("", "custom");

    private final String modelId;
    private final String translationSuffix;

    CodexModelPreset(String modelId, String translationSuffix) {
        this.modelId = modelId;
        this.translationSuffix = translationSuffix;
    }

    public String modelId() {
        return modelId;
    }

    public boolean custom() {
        return this == CUSTOM;
    }

    public Component displayName() {
        return Component.translatable("craftgpt.codex_model_preset." + translationSuffix);
    }

    public Component description() {
        return Component.translatable("craftgpt.codex_model_preset." + translationSuffix + ".description");
    }

    public CodexModelPreset next() {
        CodexModelPreset[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static CodexModelPreset fromModel(String modelId) {
        for (CodexModelPreset preset : values()) {
            if (!preset.custom() && preset.modelId.equalsIgnoreCase(modelId)) return preset;
        }
        return CUSTOM;
    }
}
