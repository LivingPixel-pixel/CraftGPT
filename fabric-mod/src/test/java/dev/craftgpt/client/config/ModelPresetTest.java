package dev.craftgpt.client.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ModelPresetTest {
    @Test
    void resolvesOfficialPresetsAndPreservesUnknownModelsAsCustom() {
        assertEquals(ModelPreset.ASTRA, ModelPreset.fromModel("GPT-6-ASTRA"));
        assertEquals("gpt-6-astra", ModelPreset.ASTRA.modelId());
        assertEquals(ModelPreset.SOL, ModelPreset.fromModel("gpt-5.6-sol"));
        assertEquals(ModelPreset.TERRA, ModelPreset.fromModel("gpt-5.6-terra"));
        assertEquals(ModelPreset.LUNA, ModelPreset.fromModel("gpt-5.6-luna"));
        assertEquals(ModelPreset.CUSTOM, ModelPreset.fromModel("provider/custom-model"));
        assertTrue(ModelPreset.CUSTOM.custom());
    }

    @Test
    void cyclesThroughQualityBalancedBudgetAndCustom() {
        assertEquals(ModelPreset.TERRA, ModelPreset.SOL.next());
        assertEquals(ModelPreset.LUNA, ModelPreset.TERRA.next());
        assertEquals(ModelPreset.CUSTOM, ModelPreset.LUNA.next());
        assertEquals(ModelPreset.ASTRA, ModelPreset.CUSTOM.next());
        assertEquals(ModelPreset.SOL, ModelPreset.ASTRA.next());
    }

    @Test
    void exposesAllCurrentReasoningEfforts() {
        assertEquals(ReasoningLevel.NONE, ReasoningLevel.parse("none"));
        assertEquals(ReasoningLevel.XHIGH, ReasoningLevel.parse("xhigh"));
        assertEquals(ReasoningLevel.MAX, ReasoningLevel.parse("max"));
    }
}
