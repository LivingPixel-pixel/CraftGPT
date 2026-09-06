package dev.craftgpt.client.ui;

import dev.craftgpt.client.api.ApiCallMetrics;
import dev.craftgpt.client.api.ApiCostEstimate;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.OptionalDouble;

final class ApiUiText {
    private ApiUiText() {
    }

    static Component estimate(ApiCostEstimate estimate) {
        if (estimate == null || !estimate.available()) {
            return Component.translatable("craftgpt.api.estimate.unavailable");
        }
        if (estimate.priceKnown()) {
            return Component.translatable(
                "craftgpt.api.estimate.known",
                usd(estimate.lowUsd()),
                usd(estimate.highUsd()),
                tokens(estimate.lowTokens()),
                tokens(estimate.highTokens())
            );
        }
        return Component.translatable(
            "craftgpt.api.estimate.custom",
            tokens(estimate.lowTokens()),
            tokens(estimate.highTokens())
        );
    }

    static Component actual(ApiCallMetrics metrics) {
        if (metrics == null || !metrics.usage().reported()) {
            return Component.translatable("craftgpt.api.actual.unreported");
        }
        OptionalDouble cost = metrics.costUsd();
        if (cost.isPresent()) {
            return Component.translatable(
                "craftgpt.api.actual.known",
                tokens(metrics.usage().totalTokens()),
                usd(cost.getAsDouble()),
                seconds(metrics.latencyMillis())
            );
        }
        return Component.translatable(
            "craftgpt.api.actual.custom",
            tokens(metrics.usage().totalTokens()),
            seconds(metrics.latencyMillis())
        );
    }

    static Component details(ApiCallMetrics metrics) {
        if (metrics == null || !metrics.usage().reported()) {
            return Component.translatable("craftgpt.api.actual.unreported");
        }
        return Component.translatable(
            "craftgpt.api.actual.details",
            tokens(metrics.usage().inputTokens()),
            tokens(metrics.usage().cachedInputTokens()),
            tokens(metrics.usage().outputTokens()),
            tokens(metrics.usage().reasoningTokens())
        );
    }

    private static String tokens(long value) {
        return String.format(Locale.ROOT, "%,d", Math.max(0L, value));
    }

    private static String usd(double value) {
        if (value > 0.0 && value < 0.0001) {
            return "<$0.0001";
        }
        return String.format(Locale.ROOT, "$%.4f", Math.max(0.0, value));
    }

    private static String seconds(long millis) {
        return String.format(Locale.ROOT, "%.1fs", Math.max(0L, millis) / 1_000.0);
    }
}
