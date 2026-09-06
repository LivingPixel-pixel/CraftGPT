package dev.craftgpt.client.config;

import net.minecraft.network.chat.Component;

/** Human-readable model roles backed by exact API model identifiers. */
public enum ModelPreset {
    ASTRA("gpt-6-astra", "astra"),
    SOL("gpt-5.6-sol", "sol"),
    TERRA("gpt-5.6-terra", "terra"),
    LUNA("gpt-5.6-luna", "luna"),
    CUSTOM("", "custom");

    private final String modelId;
    private final String translationSuffix;

    ModelPreset(String modelId, String translationSuffix) {
        this.modelId = modelId;
        this.translationSuffix = translationSuffix;
    }

    public String modelId() { return modelId; }
    public boolean custom() { return this == CUSTOM; }
    public static boolean requiresReasoning(String model) {
        return model != null && ASTRA.modelId.equalsIgnoreCase(model.trim());
    }
    public Component displayName() {
        return Component.translatable("craftgpt.model_preset." + translationSuffix);
    }
    public Component description() {
        return Component.translatable("craftgpt.model_preset." + translationSuffix + ".description");
    }
    public ModelPreset next() {
        ModelPreset[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
    public static ModelPreset fromModel(String modelId) {
        for (ModelPreset preset : values()) {
            if (!preset.custom() && preset.modelId.equalsIgnoreCase(modelId)) return preset;
        }
        return CUSTOM;
    }
}
