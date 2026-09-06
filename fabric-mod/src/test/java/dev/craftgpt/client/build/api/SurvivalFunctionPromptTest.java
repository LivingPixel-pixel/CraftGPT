package dev.craftgpt.client.build.api;

import dev.craftgpt.client.planning.api.PlannerPromptFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class SurvivalFunctionPromptTest {
    @Test
    void planningBuildingAndVisualReviewRequireFunctionalUtilities() {
        for (String prompt : new String[]{
            PlannerPromptFactory.SYSTEM_PROMPT,
            BuilderPromptFactory.SYSTEM_PROMPT,
            BuildWorkerContract.REVIEW
        }) {
            assertTrue(prompt.contains(BuildWorkerContract.SURVIVAL_FUNCTION));
            assertTrue(prompt.contains("wool, carpet or slab bedding"));
            assertTrue(prompt.contains("unavailable/unfulfilled"));
            assertTrue(prompt.contains("Do not bypass this restriction"));
            assertTrue(prompt.contains("explicitly\nrequests a nonfunctional replica"));
        }
    }
}
