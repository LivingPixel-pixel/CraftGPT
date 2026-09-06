package dev.craftgpt.build.model;

import java.util.List;

/**
 * Validated build data. Coordinates remain relative until a server verifies and applies them.
 */
public record CompiledBuildArtifact(
    int schemaVersion,
    String buildId,
    String selectionId,
    String projectId,
    String planVersionId,
    String planContentHash,
    String contextHash,
    String createdAt,
    String model,
    String reasoningLevel,
    String summary,
    List<String> palette,
    List<BuildOperation> operations
) {
    public CompiledBuildArtifact {
        palette = List.copyOf(palette);
        operations = List.copyOf(operations);
    }
}
