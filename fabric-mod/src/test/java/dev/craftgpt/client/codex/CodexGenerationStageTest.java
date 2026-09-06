package dev.craftgpt.client.codex;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CodexGenerationStageTest {
    @Test
    void activeBuildStepIsCurrentButNotCompleted() {
        assertEquals(0, CodexGenerationStage.REFRESHING.completedSteps());
        assertEquals(1, CodexGenerationStage.REFRESHING.currentStep());
        assertEquals(1, CodexGenerationStage.THINKING.completedSteps());
        assertEquals(2, CodexGenerationStage.THINKING.currentStep());
        assertEquals(1, CodexGenerationStage.WRITING.completedSteps());
        assertEquals(2, CodexGenerationStage.WRITING.currentStep());
    }

    @Test
    void laterStagesSeparateCompletedAndCurrentWork() {
        assertEquals(2, CodexGenerationStage.IMPORTING.completedSteps());
        assertEquals(3, CodexGenerationStage.IMPORTING.currentStep());
        assertEquals(3, CodexGenerationStage.VALIDATING.completedSteps());
        assertEquals(4, CodexGenerationStage.VALIDATING.currentStep());
        assertEquals(5, CodexGenerationStage.READY.completedSteps());
        assertEquals(0, CodexGenerationStage.READY.currentStep());
    }
}
