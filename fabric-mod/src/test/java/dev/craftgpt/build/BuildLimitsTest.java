package dev.craftgpt.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BuildLimitsTest {
    @Test
    void effectiveMaximumIsBoundedByConfigurationAreaAndProtocol() {
        assertEquals(20, BuildLimits.effectiveMaximumOperations(20, 100));
        assertEquals(12, BuildLimits.effectiveMaximumOperations(20, 12));
        assertEquals(
            BuildLimits.HARD_MAX_OPERATIONS,
            BuildLimits.effectiveMaximumOperations(Integer.MAX_VALUE, Long.MAX_VALUE)
        );
        assertThrows(IllegalArgumentException.class, () -> BuildLimits.effectiveMaximumOperations(0, 20));
    }

    @Test
    void dangerousStatesIncludeAdministrativeBlocksFluidsAndWaterlogging() {
        assertTrue(BuildLimits.isDangerousState("minecraft:command_block"));
        assertTrue(BuildLimits.isDangerousState("minecraft:test_instance_block"));
        assertTrue(BuildLimits.isDangerousState("minecraft:sand"));
        assertTrue(BuildLimits.isDangerousState("minecraft:blue_concrete_powder"));
        assertTrue(BuildLimits.isDangerousState("minecraft:oak_stairs[waterlogged=true]"));
        assertFalse(BuildLimits.isDangerousState("minecraft:air"));
        assertFalse(BuildLimits.isDangerousState("minecraft:stone"));
    }
}
