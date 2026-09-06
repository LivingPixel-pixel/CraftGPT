package dev.craftgpt.client.build.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class BuilderRequestSettingsTest {
    @Test
    void acceptsHttpsAndKnownReasoningLevel() {
        BuilderTestFixtures.settings(20).validate();
        new BuilderRequestSettings(
            "https://example.test/v1/responses", "key", "gpt-5.6-luna", "max", 20
        ).validate();
    }

    @Test
    void rejectsInsecureEndpointsAndUnknownReasoning() {
        BuilderException insecure = assertThrows(
            BuilderException.class,
            () -> new BuilderRequestSettings("http://example.test", "key", "model", "low", 20).validate()
        );
        assertEquals("invalid_endpoint", insecure.getMessage());

        BuilderException reasoning = assertThrows(
            BuilderException.class,
            () -> new BuilderRequestSettings(
                "https://example.test/v1/responses", "key", "model", "extreme", 20
            ).validate()
        );
        assertEquals("invalid_reasoning_level", reasoning.getMessage());
    }
}
