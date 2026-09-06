package dev.craftgpt.network;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class AreaSelectionPayloadTest {
    @Test
    void acceptsBoundedCompleteSelection() {
        AreaSelectionPayload payload = new AreaSelectionPayload(
            AreaSelectionPayload.COMPLETE,
            new BlockPos(0, 64, 0),
            new BlockPos(15, 71, 15),
            "minecraft:overworld",
            "00000000-0000-0000-0000-000000000001"
        );

        assertDoesNotThrow(payload::validate);
    }

    @Test
    void rejectsOversizedOrUnknownSelection() {
        AreaSelectionPayload oversized = new AreaSelectionPayload(
            AreaSelectionPayload.COMPLETE,
            BlockPos.ZERO,
            new BlockPos(128, 0, 0),
            "minecraft:overworld",
            "00000000-0000-0000-0000-000000000001"
        );
        AreaSelectionPayload unknown = new AreaSelectionPayload(
            99,
            BlockPos.ZERO,
            BlockPos.ZERO,
            "minecraft:overworld",
            "00000000-0000-0000-0000-000000000001"
        );

        assertThrows(IllegalArgumentException.class, oversized::validate);
        assertThrows(IllegalArgumentException.class, unknown::validate);
    }

    @Test
    void rejectsInvalidSelectionIdentity() {
        AreaSelectionPayload invalid = new AreaSelectionPayload(
            AreaSelectionPayload.STARTED,
            BlockPos.ZERO,
            BlockPos.ZERO,
            "minecraft:overworld",
            "not-a-uuid"
        );

        assertThrows(IllegalArgumentException.class, invalid::validate);
    }
}
