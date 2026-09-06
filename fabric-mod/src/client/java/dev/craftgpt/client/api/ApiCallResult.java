package dev.craftgpt.client.api;

import java.util.Objects;

public record ApiCallResult<T>(T value, ApiCallMetrics metrics) {
    public ApiCallResult {
        value = Objects.requireNonNull(value, "value");
        metrics = Objects.requireNonNull(metrics, "metrics");
    }
}
