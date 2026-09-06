package dev.craftgpt.client.build.api;

import dev.craftgpt.build.BuildLimits;
import dev.craftgpt.client.config.CraftGptConfig;
import dev.craftgpt.client.config.ContextMode;
import dev.craftgpt.client.context.AreaContextPromptFormatter;

import java.net.URI;
import java.util.Set;

public record BuilderRequestSettings(
    String endpoint,
    String apiKey,
    String model,
    String reasoningLevel,
    int maximumOperations,
    ContextMode contextMode,
    int maximumFullContextBlocks
) {
    private static final Set<String> REASONING_LEVELS = Set.of(
        "none", "low", "medium", "high", "xhigh", "max"
    );

    public BuilderRequestSettings(
        String endpoint,
        String apiKey,
        String model,
        String reasoningLevel,
        int maximumOperations
    ) {
        this(endpoint, apiKey, model, reasoningLevel, maximumOperations, ContextMode.COMPRESSED, 8_192);
    }

    public static BuilderRequestSettings from(CraftGptConfig config) {
        return new BuilderRequestSettings(
            config.apiEndpoint(),
            config.apiKey(),
            config.builderModel(),
            config.builderReasoningLevel().serializedName(),
            Math.min(config.maxBlockChanges(), BuildLimits.HARD_MAX_OPERATIONS),
            config.contextMode(),
            config.maxFullContextBlocks()
        );
    }

    public void validate() {
        URI uri;
        try {
            uri = URI.create(endpoint == null ? "" : endpoint);
        } catch (IllegalArgumentException exception) {
            throw new BuilderException("invalid_endpoint", exception);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new BuilderException("invalid_endpoint");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new BuilderException("missing_api_key");
        }
        if (model == null || model.isBlank() || model.length() > 256) {
            throw new BuilderException("missing_model");
        }
        if (!REASONING_LEVELS.contains(reasoningLevel)
            || (dev.craftgpt.client.config.ModelPreset.requiresReasoning(model) && "none".equals(reasoningLevel))) {
            throw new BuilderException("invalid_reasoning_level");
        }
        if (maximumOperations <= 0 || maximumOperations > BuildLimits.HARD_MAX_OPERATIONS) {
            throw new BuilderException("invalid_maximum_operations");
        }
        if (contextMode == null
            || maximumFullContextBlocks <= 0
            || maximumFullContextBlocks > AreaContextPromptFormatter.HARD_MAX_FULL_CONTEXT_BLOCKS) {
            throw new BuilderException("invalid_context_settings");
        }
    }
}
