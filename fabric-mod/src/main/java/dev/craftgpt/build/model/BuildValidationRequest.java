package dev.craftgpt.build.model;

import java.util.List;

/**
 * Minimal geometry-only DTO sent to the authoritative Minecraft server for preview validation.
 * Planning metadata stays on the client because the server neither needs nor verifies it.
 */
public record BuildValidationRequest(
    int schemaVersion,
    String selectionId,
    String contextHash,
    List<String> palette,
    List<BuildOperation> operations
) {
    public BuildValidationRequest {
        palette = List.copyOf(palette);
        operations = List.copyOf(operations);
    }

    public static BuildValidationRequest from(CompiledBuildArtifact artifact) {
        if (artifact == null) {
            throw new IllegalArgumentException("Build artifact is required");
        }
        return new BuildValidationRequest(
            artifact.schemaVersion(),
            artifact.selectionId(),
            artifact.contextHash(),
            artifact.palette(),
            artifact.operations()
        );
    }
}
