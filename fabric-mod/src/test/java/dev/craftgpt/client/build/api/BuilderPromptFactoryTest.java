package dev.craftgpt.client.build.api;

import dev.craftgpt.context.AreaContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BuilderPromptFactoryTest {
    @Test
    void promptContainsPlanAndRelativeContextButNoPrivateAreaMetadata() {
        AreaContext context = BuilderTestFixtures.context();

        String prompt = BuilderPromptFactory.userPrompt(
            context,
            BuilderTestFixtures.planVersion(),
            20
        );

        assertTrue(prompt.contains("A compact stone forge"));
        assertTrue(prompt.contains("4 x 4 x 4"));
        assertTrue(prompt.contains("1,2@3:minecraft:stone"));
        assertTrue(prompt.contains("maximum operations: 20"));
        assertTrue(prompt.contains("minecraft:command_block"));
        assertFalse(prompt.contains("123456"));
        assertFalse(prompt.contains("-234567"));
        assertFalse(prompt.contains(context.selectionId()));
        assertFalse(prompt.contains(context.worldStateHash()));
        assertFalse(prompt.contains(BuilderTestFixtures.planVersion().contextHash()));
        assertFalse(prompt.contains(BuilderTestFixtures.planVersion().contentHash()));
    }

    @Test
    void systemPromptRequiresCompactCanonicalRelativeOperations() {
        assertTrue(BuilderPromptFactory.SYSTEM_PROMPT.contains("x,y,z,paletteIndex"));
        assertTrue(BuilderPromptFactory.SYSTEM_PROMPT.contains("absolute world coordinates"));
        assertTrue(BuilderPromptFactory.SYSTEM_PROMPT.contains("canonical lowercase Minecraft block states"));
        assertTrue(BuilderPromptFactory.SYSTEM_PROMPT.contains("waterlogged=true"));
        assertTrue(BuilderPromptFactory.SYSTEM_PROMPT.contains("gravity-affected"));
        assertTrue(BuilderPromptFactory.SYSTEM_PROMPT.contains("block-entity-backed"));
        assertTrue(BuilderPromptFactory.SYSTEM_PROMPT.contains("COMPONENTS:"));
        assertTrue(BuilderPromptFactory.SYSTEM_PROMPT.contains("You choose every material"));
        assertTrue(BuilderPromptFactory.SYSTEM_PROMPT.contains("recognizable silhouette"));
        assertTrue(BuilderPromptFactory.SYSTEM_PROMPT.contains("no house checklist"));
    }
}
