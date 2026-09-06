package dev.craftgpt.client.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ContextModeTest {
    @Test
    void parsesCyclesAndFallsBackSafely() {
        assertEquals(ContextMode.FULL, ContextMode.parse("full"));
        assertEquals(ContextMode.COMPRESSED, ContextMode.parse("unknown"));
        assertEquals(ContextMode.FULL, ContextMode.COMPRESSED.next());
        assertEquals(ContextMode.COMPRESSED, ContextMode.FULL.next());
    }
}
