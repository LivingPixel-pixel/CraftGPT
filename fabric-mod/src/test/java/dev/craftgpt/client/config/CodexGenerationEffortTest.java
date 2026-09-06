package dev.craftgpt.client.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CodexGenerationEffortTest {
    @Test
    void clampsEffortAndMapsShotsToVisualReviewRounds() {
        assertEquals(1, CodexGenerationEffort.clamp(-4));
        assertEquals(7, CodexGenerationEffort.clamp(20));
        assertEquals(0, CodexGenerationEffort.visualReviewRounds(1));
        assertEquals(1, CodexGenerationEffort.visualReviewRounds(2));
        assertEquals(6, CodexGenerationEffort.visualReviewRounds(7));
    }

    @Test
    void sliderSnapsToSevenDiscreteEffortLevels() {
        for (int effort = 1; effort <= 7; effort++) {
            assertEquals(
                effort,
                CodexGenerationEffort.fromSlider(CodexGenerationEffort.sliderValue(effort))
            );
        }
        assertEquals(1, CodexGenerationEffort.fromSlider(-1.0));
        assertEquals(7, CodexGenerationEffort.fromSlider(2.0));
    }
}
