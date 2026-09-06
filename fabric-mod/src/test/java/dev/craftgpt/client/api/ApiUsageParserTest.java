package dev.craftgpt.client.api;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ApiUsageParserTest {
    @Test
    void parsesResponsesUsageAndNestedDetails() {
        JsonObject inputDetails = new JsonObject();
        inputDetails.addProperty("cached_tokens", 200);
        inputDetails.addProperty("cache_write_tokens", 100);
        JsonObject outputDetails = new JsonObject();
        outputDetails.addProperty("reasoning_tokens", 300);
        JsonObject usage = new JsonObject();
        usage.addProperty("input_tokens", 1_000);
        usage.add("input_tokens_details", inputDetails);
        usage.addProperty("output_tokens", 500);
        usage.add("output_tokens_details", outputDetails);
        usage.addProperty("total_tokens", 1_500);
        JsonObject response = new JsonObject();
        response.add("usage", usage);

        ApiUsage parsed = ApiUsageParser.parse(response);

        assertTrue(parsed.reported());
        assertEquals(1_000, parsed.inputTokens());
        assertEquals(200, parsed.cachedInputTokens());
        assertEquals(100, parsed.cacheWriteTokens());
        assertEquals(700, parsed.uncachedInputTokens());
        assertEquals(500, parsed.outputTokens());
        assertEquals(300, parsed.reasoningTokens());
        assertEquals(1_500, parsed.totalTokens());
    }

    @Test
    void missingUsageRemainsExplicitlyUnavailable() {
        ApiUsage parsed = ApiUsageParser.parse(new JsonObject());

        assertFalse(parsed.reported());
        assertEquals(0, parsed.totalTokens());
    }

    @Test
    void clampsUntrustedDetailCountsToTheirParentTotals() {
        JsonObject inputDetails = new JsonObject();
        inputDetails.addProperty("cached_tokens", 2_000);
        inputDetails.addProperty("cache_write_tokens", 2_000);
        JsonObject outputDetails = new JsonObject();
        outputDetails.addProperty("reasoning_tokens", 2_000);
        JsonObject usage = new JsonObject();
        usage.addProperty("input_tokens", 100);
        usage.add("input_tokens_details", inputDetails);
        usage.addProperty("output_tokens", 50);
        usage.add("output_tokens_details", outputDetails);
        JsonObject response = new JsonObject();
        response.add("usage", usage);

        ApiUsage parsed = ApiUsageParser.parse(response);

        assertEquals(100, parsed.cachedInputTokens());
        assertEquals(0, parsed.cacheWriteTokens());
        assertEquals(50, parsed.reasoningTokens());
        assertEquals(150, parsed.totalTokens());
    }
}
