package dev.craftgpt.client.api;

import java.nio.charset.StandardCharsets;

/** Conservative character-based preflight estimate for text-only structured requests. */
public final class ApiCostEstimator {
    private ApiCostEstimator() {
    }

    public static ApiCostEstimate estimate(
        String model,
        String serializedRequest,
        long expectedOutputTokens,
        long maximumOutputTokens
    ) {
        if (serializedRequest == null || serializedRequest.isEmpty()) {
            return ApiCostEstimate.unavailable();
        }
        long bytes = serializedRequest.getBytes(StandardCharsets.UTF_8).length;
        long lowInput = divideRoundUp(bytes, 5L);
        long highInput = divideRoundUp(bytes, 3L);
        long highOutput = Math.max(256L, Math.min(maximumOutputTokens, expectedOutputTokens));
        long lowOutput = Math.max(128L, highOutput / 2L);
        long lowTokens = lowInput + lowOutput;
        long highTokens = highInput + highOutput;

        return ApiPricing.forModel(model)
            .map(pricing -> new ApiCostEstimate(
                true,
                lowTokens,
                highTokens,
                true,
                pricing.estimate(lowInput, lowOutput),
                pricing.estimate(highInput, highOutput)
            ))
            .orElseGet(() -> new ApiCostEstimate(
                true,
                lowTokens,
                highTokens,
                false,
                0.0,
                0.0
            ));
    }

    private static long divideRoundUp(long value, long divisor) {
        return (value + divisor - 1L) / divisor;
    }
}
