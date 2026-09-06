package dev.craftgpt.client.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ApiPricingTest {
    @Test
    void pricesReportedUsageWithoutDoubleCountingReasoningTokens() {
        ApiUsage usage = new ApiUsage(true, 1_000, 200, 100, 500, 300, 1_500);
        ApiPricing pricing = ApiPricing.forModel("gpt-5.6-luna").orElseThrow();

        assertEquals(0.003845, pricing.cost(usage), 0.0000001);
    }

    @Test
    void builtInModelsHavePricesButCustomModelsDoNot() {
        assertTrue(ApiPricing.forModel("gpt-5.6-sol").isPresent());
        assertTrue(ApiPricing.forModel("gpt-5.6-terra").isPresent());
        assertTrue(ApiPricing.forModel("gpt-5.6-luna").isPresent());
        assertFalse(ApiPricing.forModel("custom-provider-model").isPresent());
    }

    @Test
    void estimatePreservesTokenRangeWhenCustomPriceIsUnknown() {
        ApiCostEstimate estimate = ApiCostEstimator.estimate(
            "custom-provider-model",
            "x".repeat(4_000),
            2_000,
            4_000
        );

        assertTrue(estimate.available());
        assertFalse(estimate.priceKnown());
        assertTrue(estimate.lowTokens() > 0);
        assertTrue(estimate.highTokens() > estimate.lowTokens());
    }
}
