package dev.craftgpt.client.api;

import java.util.OptionalDouble;

/** Measured metadata for one completed API call. */
public record ApiCallMetrics(
    String model,
    String reasoningLevel,
    ApiUsage usage,
    long latencyMillis
) {
    public ApiCallMetrics {
        model = model == null ? "" : model;
        reasoningLevel = reasoningLevel == null ? "" : reasoningLevel;
        usage = usage == null ? ApiUsage.unavailable() : usage;
        latencyMillis = Math.max(0L, latencyMillis);
    }

    public OptionalDouble costUsd() {
        return usage.reported()
            ? ApiPricing.forModel(model).map(pricing -> OptionalDouble.of(pricing.cost(usage)))
                .orElseGet(OptionalDouble::empty)
            : OptionalDouble.empty();
    }
}
