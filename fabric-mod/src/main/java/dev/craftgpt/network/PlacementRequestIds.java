package dev.craftgpt.network;

import java.util.UUID;

final class PlacementRequestIds {
    private PlacementRequestIds() {
    }

    static void validate(String value) {
        if (value == null
            || !value.matches("[A-Za-z0-9._-]{1," + PlacementRequestPayload.MAX_REQUEST_ID_LENGTH + "}")) {
            throw new IllegalArgumentException("Invalid placement request ID");
        }
    }

    static void validateUuid(String value, String field) {
        try {
            UUID parsed = UUID.fromString(value == null ? "" : value);
            if (!parsed.toString().equals(value)) {
                throw new IllegalArgumentException("Non-canonical UUID");
            }
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid " + field, exception);
        }
    }
}
