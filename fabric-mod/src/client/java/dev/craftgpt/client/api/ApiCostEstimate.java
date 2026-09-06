package dev.craftgpt.client.api;

/** Approximate preflight range; actual response usage remains the source of truth. */
public record ApiCostEstimate(
    boolean available,
    long lowTokens,
    long highTokens,
    boolean priceKnown,
    double lowUsd,
    double highUsd
) {
    public ApiCostEstimate {
        lowTokens = Math.max(0L, lowTokens);
        highTokens = Math.max(lowTokens, highTokens);
        lowUsd = Math.max(0.0, lowUsd);
        highUsd = Math.max(lowUsd, highUsd);
    }

    public static ApiCostEstimate unavailable() {
        return new ApiCostEstimate(false, 0L, 0L, false, 0.0, 0.0);
    }
}
