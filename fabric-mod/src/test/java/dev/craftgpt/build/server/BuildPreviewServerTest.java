package dev.craftgpt.build.server;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BuildPreviewServerTest {
    @Test
    void rateLimitsRepeatedValidationAndClearsPlayerState() {
        UUID playerId = UUID.randomUUID();
        long start = 10_000L;

        assertTrue(BuildPreviewServer.tryAcquire(playerId, start));
        assertFalse(BuildPreviewServer.tryAcquire(playerId, start + TimeUnit.SECONDS.toNanos(1)));
        assertTrue(BuildPreviewServer.tryAcquire(playerId, start + TimeUnit.SECONDS.toNanos(2)));

        BuildPreviewServer.clear(playerId);
        assertTrue(BuildPreviewServer.tryAcquire(playerId, start));
        BuildPreviewServer.clear(playerId);
    }
}
