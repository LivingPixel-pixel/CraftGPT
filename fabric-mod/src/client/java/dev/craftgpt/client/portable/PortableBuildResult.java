package dev.craftgpt.client.portable;

import dev.craftgpt.build.model.BuildDraft;
import dev.craftgpt.client.planning.model.IntentionSpec;

public record PortableBuildResult(
    int schemaVersion,
    String requestId,
    String contextHash,
    String instructionHash,
    IntentionSpec plan,
    BuildDraft build,
    ReviewDecision review
) {
    public PortableBuildResult(int schemaVersion,String requestId,String contextHash,String instructionHash,
                               IntentionSpec plan,BuildDraft build) {
        this(schemaVersion,requestId,contextHash,instructionHash,plan,build,null);
    }
}
