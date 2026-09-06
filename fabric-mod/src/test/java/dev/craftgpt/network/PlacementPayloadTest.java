package dev.craftgpt.network;

import dev.craftgpt.build.BuildLimits;
import dev.craftgpt.build.model.BuildOperation;
import dev.craftgpt.build.model.BuildValidationRequest;
import dev.craftgpt.build.model.CompiledBuildArtifact;
import dev.craftgpt.placement.model.PlacementHistoryEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class PlacementPayloadTest {
    @Test
    void roundTripsOnlyGeometryRequiredByTheServer() {
        CompiledBuildArtifact artifact = artifact();
        PlacementRequestPayload payload = PlacementRequestPayload.create("request-1", artifact);
        PlacementRequestPayload.RequestBody decoded = payload.decodeValidated();

        assertEquals("request-1", decoded.requestId());
        assertEquals(artifact.buildId(), decoded.buildId());
        assertFalse(decoded.allowBlockEntityReplacement());
        assertEquals(BuildValidationRequest.from(artifact), decoded.build());
        assertFalse(payload.json().contains("projectId"));
        assertFalse(payload.json().contains("planVersionId"));
        assertFalse(payload.json().contains("planContentHash"));
        assertFalse(payload.json().contains("createdAt"));
        assertFalse(payload.json().contains("gpt-test"));
        assertFalse(payload.json().contains("Safe test build"));
    }

    @Test
    void rejectsUnexpectedFieldsAndInvalidProgress() {
        PlacementRequestPayload unexpected = new PlacementRequestPayload(
            "{\"requestId\":\"request-1\",\"buildId\":\"33333333-3333-3333-3333-333333333333\",\"allowBlockEntityReplacement\":false,\"build\":null,\"extra\":true}"
        );

        assertThrows(IllegalArgumentException.class, unexpected::decodeValidated);
        assertThrows(IllegalArgumentException.class, () -> new PlacementUndoRequestPayload("bad request"));
        assertThrows(IllegalArgumentException.class, () -> new PlacementStatusRequestPayload(""));
        assertThrows(IllegalArgumentException.class, () -> new PlacementStatusPayload(
            "request-1", "", "", "placing", 2, 1, 0
        ));
    }

    @Test
    void acceptsTerminalAndErrorStatusEnvelopes() {
        PlacementStatusPayload placed = new PlacementStatusPayload(
            "request-1",
            "55555555-5555-5555-5555-555555555555",
            "33333333-3333-3333-3333-333333333333",
            "placed",
            20,
            20,
            0
        );
        PlacementStatusPayload error = new PlacementStatusPayload(
            "request-2", "", "", "forbidden", 0, 0, 0
        );

        assertEquals("placed", placed.status());
        assertEquals("forbidden", error.status());
    }

    @Test
    void historyIsCoordinateFreeBoundedMetadataAndRecoveryIsTargeted() {
        PlacementHistoryEntry entry = new PlacementHistoryEntry(
            "55555555-5555-5555-5555-555555555555",
            "33333333-3333-3333-3333-333333333333",
            "minecraft:overworld",
            "2026-07-15T10:00:00Z",
            "undone",
            20,
            20,
            0,
            true,
            true,
            false
        );
        PlacementHistoryPayload payload = PlacementHistoryPayload.create("request-3", List.of(entry));

        assertEquals(List.of(entry), payload.decodeValidated());
        assertFalse(payload.json().contains("beforeState"));
        assertFalse(payload.json().contains("BlockEntityNbt"));

        PlacementRecoveryRequestPayload recovery = new PlacementRecoveryRequestPayload(
            "request-4",
            entry.placementId(),
            PlacementRecoveryRequestPayload.REDO
        );
        assertEquals(entry.placementId(), recovery.placementId());
        assertThrows(IllegalArgumentException.class, () -> new PlacementRecoveryRequestPayload(
            "request-5", entry.placementId(), "delete"
        ));
        assertEquals(PlacementMaintenanceRequestPayload.EXPORT, new PlacementMaintenanceRequestPayload(
            "request-6", entry.placementId(), PlacementMaintenanceRequestPayload.EXPORT, 0).action());
        assertThrows(IllegalArgumentException.class, () -> new PlacementMaintenanceRequestPayload(
            "request-7", "", PlacementMaintenanceRequestPayload.PRUNE, 1));

        PlacementInspectPayload inspect = new PlacementInspectPayload(
            "request-8", entry.placementId(), "minecraft:overworld", 1, 2, 3, 4, 5, 6);
        assertEquals(4, inspect.maxX());
        assertThrows(IllegalArgumentException.class, () -> new PlacementInspectPayload(
            "request-9", entry.placementId(), "minecraft:overworld", 4, 2, 3, 1, 5, 6));
    }

    private CompiledBuildArtifact artifact() {
        return new CompiledBuildArtifact(
            BuildLimits.SCHEMA_VERSION,
            "33333333-3333-3333-3333-333333333333",
            "11111111-1111-1111-1111-111111111111",
            "44444444-4444-4444-4444-444444444444",
            "v1",
            "b".repeat(64),
            "a".repeat(64),
            "2026-07-15T08:00:00Z",
            "gpt-test",
            "low",
            "Safe test build",
            List.of("minecraft:stone"),
            List.of(new BuildOperation(0, 0, 0, 0))
        );
    }
}
