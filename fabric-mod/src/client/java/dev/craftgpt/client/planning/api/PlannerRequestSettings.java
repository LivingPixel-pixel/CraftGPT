package dev.craftgpt.client.planning.api;

import dev.craftgpt.client.config.CraftGptConfig;
import dev.craftgpt.client.config.ContextMode;
import dev.craftgpt.client.context.AreaContextPromptFormatter;

import java.net.URI;
import java.util.Set;

public record PlannerRequestSettings(
    String endpoint,
    String apiKey,
    String model,
    String reasoningLevel,
    int maximumBlockChanges,
    ContextMode contextMode,
    int maximumFullContextBlocks
) {
    private static final Set<String> REASONING_LEVELS = Set.of(
        "none", "low", "medium", "high", "xhigh", "max"
    );

    public PlannerRequestSettings(
        String endpoint,
        String apiKey,
        String model,
        String reasoningLevel,
        int maximumBlockChanges
    ) {
        this(
            endpoint, apiKey, model, reasoningLevel, maximumBlockChanges,
            ContextMode.COMPRESSED, 8_192
        );
    }
    public static PlannerRequestSettings from(CraftGptConfig config) {
        return new PlannerRequestSettings(
            config.apiEndpoint(),
            config.apiKey(),
            config.planningModel(),
            config.reasoningLevel().serializedName(),
            config.maxBlockChanges(),
            config.contextMode(),
            config.maxFullContextBlocks()
        );
    }

    public void validate() {
        URI uri;
        try {
            uri = URI.create(endpoint == null ? "" : endpoint);
        } catch (IllegalArgumentException exception) {
            throw new PlannerException("invalid_endpoint", exception);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new PlannerException("invalid_endpoint");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new PlannerException("missing_api_key");
        }
        if (model == null || model.isBlank()) {
            throw new PlannerException("missing_model");
        }
        if (!REASONING_LEVELS.contains(reasoningLevel)
            || (dev.craftgpt.client.config.ModelPreset.requiresReasoning(model) && "none".equals(reasoningLevel))) {
            throw new PlannerException("invalid_reasoning_level");
        }
        if (contextMode == null
            || maximumFullContextBlocks <= 0
            || maximumFullContextBlocks > AreaContextPromptFormatter.HARD_MAX_FULL_CONTEXT_BLOCKS) {
            throw new PlannerException("invalid_context_settings");
        }
    }
}
