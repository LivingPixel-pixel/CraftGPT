package dev.craftgpt.client.planning.model;

import dev.craftgpt.context.AreaContext;

import java.util.List;

public record PlanProjectManifest(
    int schemaVersion,
    String projectId,
    String worldScopeId,
    String name,
    String createdAt,
    String updatedAt,
    AreaContext areaContext,
    String activeVersionId,
    int nextVersionNumber,
    List<String> versionIds
) {
    public PlanProjectManifest {
        versionIds = List.copyOf(versionIds);
    }
}
