package dev.craftgpt.client.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CodexModelPresetTest {
    @Test
    void resolvesKnownAndCustomModelIds() {
        assertEquals(CodexModelPreset.ASTRA, CodexModelPreset.fromModel("GPT-6-ASTRA"));
        assertEquals("gpt-6-astra", CodexModelPreset.ASTRA.modelId());
        assertEquals(CodexModelPreset.ASTRA, CodexModelPreset.CUSTOM.next());
        assertEquals(CodexModelPreset.SOL, CodexModelPreset.ASTRA.next());
        assertEquals(CodexModelPreset.SOL, CodexModelPreset.fromModel("gpt-5.6-sol"));
        assertEquals(CodexModelPreset.TERRA, CodexModelPreset.fromModel("GPT-5.6-TERRA"));
        assertEquals(CodexModelPreset.CUSTOM, CodexModelPreset.fromModel("future-model"));
    }
}
