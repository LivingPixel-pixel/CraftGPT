package dev.craftgpt.client.planning.storage;

import dev.craftgpt.client.planning.model.IntentionSpec;
import dev.craftgpt.client.planning.model.MaterialRole;
import dev.craftgpt.client.planning.model.PlanDimensions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlanContentHasherTest {
    @Test
    void hashIsDeterministicAndChangesWithPlanContent() {
        IntentionSpec first = plan("Forge");
        IntentionSpec equivalent = plan("Forge");
        IntentionSpec changed = plan("Workshop");

        assertEquals(PlanContentHasher.sha256(first), PlanContentHasher.sha256(equivalent));
        assertNotEquals(PlanContentHasher.sha256(first), PlanContentHasher.sha256(changed));
        assertTrue(PlanContentHasher.sha256(first).matches("[a-f0-9]{64}"));
    }

    private IntentionSpec plan(String title) {
        return new IntentionSpec(
            title, title + " summary", List.of("Goal"), "Medieval",
            new PlanDimensions(8, 7, 8), "South",
            List.of(new MaterialRole("walls", List.of("minecraft:stone"), "Shell")),
            List.of("Chimney"), List.of(), List.of("Stay in area"), List.of(), List.of(),
            500, "Rationale", "Complete implementation brief"
        );
    }
}
