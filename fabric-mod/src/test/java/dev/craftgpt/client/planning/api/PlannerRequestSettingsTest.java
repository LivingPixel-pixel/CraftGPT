package dev.craftgpt.client.planning.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class PlannerRequestSettingsTest {
    @Test
    void acceptsCurrentReasoningEffortsAndRejectsUnknownValues() {
        new PlannerRequestSettings(
            "https://api.openai.com/v1/responses", "key", "gpt-5.6-terra", "xhigh", 500
        ).validate();
        PlannerException exception = assertThrows(PlannerException.class, () ->
            new PlannerRequestSettings(
                "https://api.openai.com/v1/responses", "key", "model", "extreme", 500
            ).validate());
        assertEquals("invalid_reasoning_level", exception.getMessage());
    }
}
