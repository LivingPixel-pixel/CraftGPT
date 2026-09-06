package dev.craftgpt.client.planning.model;

public record PlanVersion(
    int schemaVersion,
    String id,
    String parentVersionId,
    String sourceVersionId,
    String createdAt,
    String action,
    String instruction,
    String changeSummary,
    String contextHash,
    String contentHash,
    String model,
    String reasoningLevel,
    IntentionSpec intention
) {
}
