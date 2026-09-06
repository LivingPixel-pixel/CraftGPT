package dev.craftgpt.network;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class AreaContextRefreshRequestPayloadTest {
    @Test
    void createsAValidUniqueRequestId() {
        AreaContextRefreshRequestPayload payload = AreaContextRefreshRequestPayload.create();

        assertDoesNotThrow(() -> UUID.fromString(payload.requestId()));
    }

    @Test
    void rejectsMalformedRequestIds() {
        assertThrows(IllegalArgumentException.class, () ->
            new AreaContextRefreshRequestPayload("not-a-uuid"));
    }
}
