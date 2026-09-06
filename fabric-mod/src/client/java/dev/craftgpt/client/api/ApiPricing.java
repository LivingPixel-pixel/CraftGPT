package dev.craftgpt.client.api;

import java.util.Map;
import java.util.Optional;

/**
 * Local pricing snapshot for the built-in model presets.
 *
 * <p>Rates are USD per one million tokens and were checked against the official
 * OpenAI model catalog on 2026-07-28, with Astra added on 2026-09-06.
 * Custom models deliberately have no guessed price. These are standard short-context rates.</p>
 */
public record ApiPricing(
    double inputPerMillion,
    double cachedInputPerMillion,
    double cacheWritePerMillion,
    double outputPerMillion
) {
    public static final String SNAPSHOT_DATE = "2026-07-28";

    private static final Map<String, ApiPricing> BUILT_INS = Map.of(
        // https://developers.openai.com/api/docs/models/gpt-6-astra (2026-09-06)
        "gpt-6-astra", new ApiPricing(10.00, 1.00, 12.50, 50.00),
        "gpt-5.6-sol", new ApiPricing(5.00, 0.50, 6.25, 30.00),
        "gpt-5.6-terra", new ApiPricing(2.50, 0.25, 3.125, 15.00),
        "gpt-5.6-luna", new ApiPricing(1.00, 0.10, 1.25, 6.00)
    );

    public static Optional<ApiPricing> forModel(String model) {
        return Optional.ofNullable(BUILT_INS.get(model));
    }

    public double cost(ApiUsage usage) {
        if (usage == null || !usage.reported()) {
            return 0.0;
        }
        double input = usage.uncachedInputTokens() * inputPerMillion;
        double cached = usage.cachedInputTokens() * cachedInputPerMillion;
        double cacheWrite = usage.cacheWriteTokens() * cacheWritePerMillion;
        double output = usage.outputTokens() * outputPerMillion;
        return (input + cached + cacheWrite + output) / 1_000_000.0;
    }

    public double estimate(long inputTokens, long outputTokens) {
        return (
            Math.max(0L, inputTokens) * inputPerMillion
                + Math.max(0L, outputTokens) * outputPerMillion
        ) / 1_000_000.0;
    }
}
