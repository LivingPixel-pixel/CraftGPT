package dev.craftgpt.compat.placement;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyMixinTest {
    @Test void bothPlacementRedirectsActuallyApplyToMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var names = java.util.Arrays.stream(LevelChunk.class.getDeclaredMethods()).map(java.lang.reflect.Method::getName).toList();
        assertTrue(names.stream().anyMatch(name -> name.contains("craftgpt$onPlace")), "Placement redirect was not applied");
        assertTrue(names.stream().anyMatch(name -> name.contains("craftgpt$onRemove")), "Removal redirect was not applied");
    }
}
