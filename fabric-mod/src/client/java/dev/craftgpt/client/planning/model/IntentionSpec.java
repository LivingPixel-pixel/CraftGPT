package dev.craftgpt.client.planning.model;

import java.util.List;

public record IntentionSpec(
    String title,
    String summary,
    List<String> goals,
    String style,
    PlanDimensions targetDimensions,
    String orientation,
    List<MaterialRole> materialRoles,
    List<String> requiredFeatures,
    List<String> preferredFeatures,
    List<String> constraints,
    List<String> avoid,
    List<String> assumptions,
    int estimatedBlockChanges,
    String designRationale,
    String implementationBrief
) {
    public IntentionSpec {
        goals = List.copyOf(goals);
        materialRoles = List.copyOf(materialRoles);
        requiredFeatures = List.copyOf(requiredFeatures);
        preferredFeatures = List.copyOf(preferredFeatures);
        constraints = List.copyOf(constraints);
        avoid = List.copyOf(avoid);
        assumptions = List.copyOf(assumptions);
    }
}
