package dev.craftgpt.placement.server;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PlacementConflictPolicyTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void appliesOnlyExpectedStateAndPreservesUnrelatedChanges() {
        var before = Blocks.STONE.defaultBlockState();
        var after = Blocks.OAK_PLANKS.defaultBlockState();

        assertEquals(
            PlacementConflictPolicy.Decision.APPLY,
            PlacementConflictPolicy.decide(before, before, after)
        );
        assertEquals(
            PlacementConflictPolicy.Decision.ALREADY_APPLIED,
            PlacementConflictPolicy.decide(after, before, after)
        );
        assertEquals(
            PlacementConflictPolicy.Decision.CONFLICT,
            PlacementConflictPolicy.decide(Blocks.DIAMOND_BLOCK.defaultBlockState(), before, after)
        );
    }

    @Test
    void samePolicyMakesUndoIdempotent() {
        var before = Blocks.STONE.defaultBlockState();
        var after = Blocks.OAK_PLANKS.defaultBlockState();

        assertEquals(
            PlacementConflictPolicy.Decision.APPLY,
            PlacementConflictPolicy.decide(after, after, before)
        );
        assertEquals(
            PlacementConflictPolicy.Decision.ALREADY_APPLIED,
            PlacementConflictPolicy.decide(before, after, before)
        );
    }

    @Test
    void blockEntityDataMustMatchBeforeDestructiveReplacementOrIdempotentUndo() {
        var before = Blocks.CHEST.defaultBlockState();
        var after = Blocks.STONE.defaultBlockState();

        assertEquals(
            PlacementConflictPolicy.Decision.APPLY,
            PlacementConflictPolicy.decide(before, "{id:\"minecraft:chest\"}", before,
                "{id:\"minecraft:chest\"}", after, null)
        );
        assertEquals(
            PlacementConflictPolicy.Decision.CONFLICT,
            PlacementConflictPolicy.decide(before, "{id:\"minecraft:chest\",changed:1b}", before,
                "{id:\"minecraft:chest\"}", after, null)
        );
        assertEquals(
            PlacementConflictPolicy.Decision.CONFLICT,
            PlacementConflictPolicy.decide(before, "{id:\"minecraft:chest\",changed:1b}", after,
                null, before, "{id:\"minecraft:chest\"}")
        );
    }
}
