package dev.craftgpt.network;

import dev.craftgpt.build.BuildLimits;
import dev.craftgpt.build.model.BuildOperation;
import dev.craftgpt.build.model.BuildValidationRequest;
import dev.craftgpt.build.model.CompiledBuildArtifact;
import dev.craftgpt.validation.ValidationProblemCatalog;
import dev.craftgpt.validation.ValidationProblemJson;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class BuildPreviewPayloadTest {
    @Test
    void transportsAllDetailedServerProblems() {
        String json = ValidationProblemJson.encode(List.of(
            ValidationProblemCatalog.problem("invalid_block_state", "build.palette[2]"),
            ValidationProblemCatalog.problem("out_of_bounds", "build.operations[7]")
        ));
        BuildPreviewResponsePayload payload = new BuildPreviewResponsePayload(
            "request-1",
            false,
            "invalid_block_state",
            0,
            json
        );

        assertEquals(2, payload.decodedProblems().size());
        assertEquals("build.operations[7]", payload.decodedProblems().get(1).location());
        assertFalse(payload.decodedProblems().get(1).suggestion().isBlank());
    }

    @Test
    void roundTripsBoundedJsonEnvelope() {
        CompiledBuildArtifact artifact = artifact();

        BuildPreviewRequestPayload payload = BuildPreviewRequestPayload.create("request-1", artifact);
        BuildPreviewRequestPayload.RequestBody decoded = payload.decodeValidated();

        assertEquals("request-1", decoded.requestId());
        assertEquals(BuildValidationRequest.from(artifact), decoded.build());
        assertFalse(payload.json().contains("projectId"));
        assertFalse(payload.json().contains("planVersionId"));
        assertFalse(payload.json().contains("planContentHash"));
        assertFalse(payload.json().contains("createdAt"));
        assertFalse(payload.json().contains("reasoningLevel"));
        assertFalse(payload.json().contains("Safe test build"));
    }

    @Test
    void rejectsInvalidEnvelopeFieldsAndOversizedUtf8() {
        BuildPreviewRequestPayload extraField = new BuildPreviewRequestPayload(
            "{\"requestId\":\"request-1\",\"build\":null,\"extra\":true}"
        );
        BuildPreviewRequestPayload oversized = new BuildPreviewRequestPayload(
            "x".repeat(BuildPreviewRequestPayload.MAX_JSON_BYTES + 1)
        );

        assertThrows(IllegalArgumentException.class, extraField::decodeValidated);
        assertThrows(IllegalArgumentException.class, oversized::decodeValidated);
        assertThrows(IllegalArgumentException.class, () ->
            BuildPreviewRequestPayload.create("invalid request id", artifact()));
    }

    @Test
    void responseRejectsInvalidCountsAndCodes() {
        assertThrows(IllegalArgumentException.class, () ->
            new BuildPreviewResponsePayload("request-1", true, "ok", -1));
        assertThrows(IllegalArgumentException.class, () ->
            new BuildPreviewResponsePayload("request-1", false, " ", 0));
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
            "2026-07-11T08:00:00Z",
            "gpt-test",
            "medium",
            "Safe test build",
            List.of("minecraft:stone"),
            List.of(new BuildOperation(0, 0, 0, 0))
        );
    }
}
