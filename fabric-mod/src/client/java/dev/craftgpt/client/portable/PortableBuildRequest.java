package dev.craftgpt.client.portable;

import dev.craftgpt.client.planning.model.IntentionSpec;

public record PortableBuildRequest(
    int schemaVersion,
    String requestId,
    String createdAt,
    String contextHash,
    String instructionHash,
    String instruction,
    int maximumOperations,
    String previousPlanContentHash,
    IntentionSpec previousPlan,
    PortableAreaContext areaContext,
    String currentBuildHash,
    dev.craftgpt.build.model.BuildDraft currentBuild,
    BuildEditScope editScope
) {
    public PortableBuildRequest(int schemaVersion,String requestId,String createdAt,String contextHash,
        String instructionHash,String instruction,int maximumOperations,String previousPlanContentHash,
        IntentionSpec previousPlan,PortableAreaContext areaContext) {
        this(schemaVersion,requestId,createdAt,contextHash,instructionHash,instruction,maximumOperations,
            previousPlanContentHash,previousPlan,areaContext,null,null,null);
    }
}
