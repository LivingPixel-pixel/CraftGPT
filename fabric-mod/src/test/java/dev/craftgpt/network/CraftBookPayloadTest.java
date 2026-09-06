package dev.craftgpt.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class CraftBookPayloadTest {
    @Test
    void mapsOnlyClosedVisualActionsToCommands() {
        assertEquals(
            "craftgpt setArea start",
            new CraftBookActionPayload(CraftBookActionPayload.AREA_START).command()
        );
        assertEquals(
            "craftgpt history",
            new CraftBookActionPayload(CraftBookActionPayload.HISTORY).command()
        );
        assertThrows(IllegalArgumentException.class, () ->
            new CraftBookActionPayload("say arbitrary text"));
        assertThrows(IllegalArgumentException.class, () ->
            new CraftBookActionPayload("craftgpt prompt injected"));
    }

    @Test
    void openPayloadCarriesOnlyBooleanState() {
        assertEquals(true, new OpenCraftBookPayload(true).open());
    }
}
