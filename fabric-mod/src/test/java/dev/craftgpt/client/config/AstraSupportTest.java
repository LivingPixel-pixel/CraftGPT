package dev.craftgpt.client.config;

import dev.craftgpt.client.api.ApiPricing;
import dev.craftgpt.client.build.api.BuilderException;
import dev.craftgpt.client.build.api.BuilderRequestSettings;
import dev.craftgpt.client.codex.CodexRunSettings;
import dev.craftgpt.client.planning.api.PlannerException;
import dev.craftgpt.client.planning.api.PlannerRequestSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class AstraSupportTest {
    private static final String ENDPOINT = "https://api.openai.com/v1/responses";

    @Test
    void acceptsAstraWithEverySupportedReasoningLevel() {
        for (String level : new String[]{"low", "medium", "high", "xhigh", "max"}) {
            assertDoesNotThrow(() -> new CodexRunSettings("gpt-6-astra", level));
            assertDoesNotThrow(() -> new PlannerRequestSettings(
                ENDPOINT, "test-key", "gpt-6-astra", level, 100).validate());
            assertDoesNotThrow(() -> new BuilderRequestSettings(
                ENDPOINT, "test-key", "gpt-6-astra", level, 100).validate());
        }
    }

    @Test
    void rejectsUnsupportedNoneBeforeSendingAstraRequests() {
        assertThrows(PlannerException.class, () -> new PlannerRequestSettings(
            ENDPOINT, "test-key", "gpt-6-astra", "none", 100).validate());
        assertThrows(BuilderException.class, () -> new BuilderRequestSettings(
            ENDPOINT, "test-key", "gpt-6-astra", "none", 100).validate());
        assertThrows(IllegalArgumentException.class, () -> new CodexRunSettings("gpt-6-astra", "none"));
        assertDoesNotThrow(() -> new PlannerRequestSettings(
            ENDPOINT, "test-key", "gpt-5.6-sol", "none", 100).validate());
        assertDoesNotThrow(() -> new BuilderRequestSettings(
            ENDPOINT, "test-key", "gpt-5.6-sol", "none", 100).validate());
    }

    @Test
    void providesAstraStandardShortContextPriceEstimates() {
        ApiPricing pricing = ApiPricing.forModel("gpt-6-astra").orElseThrow();
        assertEquals(10.0, pricing.inputPerMillion());
        assertEquals(1.0, pricing.cachedInputPerMillion());
        assertEquals(12.5, pricing.cacheWritePerMillion());
        assertEquals(50.0, pricing.outputPerMillion());
        assertEquals(0.06, pricing.estimate(1000, 1000), 0.0000001);
    }
}
