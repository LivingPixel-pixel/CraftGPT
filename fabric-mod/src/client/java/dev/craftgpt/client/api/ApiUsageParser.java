package dev.craftgpt.client.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Defensive parser for the optional usage object returned by Responses-compatible APIs. */
public final class ApiUsageParser {
    private ApiUsageParser() {
    }

    public static ApiUsage parse(JsonObject response) {
        if (response == null || !response.has("usage") || !response.get("usage").isJsonObject()) {
            return ApiUsage.unavailable();
        }
        JsonObject usage = response.getAsJsonObject("usage");
        long input = integer(usage, "input_tokens");
        long output = integer(usage, "output_tokens");
        long total = integer(usage, "total_tokens");

        JsonObject inputDetails = object(usage, "input_tokens_details");
        long cached = integer(inputDetails, "cached_tokens");
        long cacheWrite = integer(inputDetails, "cache_write_tokens");
        if (cacheWrite == 0L) {
            cacheWrite = integer(usage, "cache_write_tokens");
        }

        JsonObject outputDetails = object(usage, "output_tokens_details");
        long reasoning = integer(outputDetails, "reasoning_tokens");
        return new ApiUsage(true, input, cached, cacheWrite, output, reasoning, total);
    }

    private static JsonObject object(JsonObject parent, String name) {
        return parent != null && parent.has(name) && parent.get(name).isJsonObject()
            ? parent.getAsJsonObject(name)
            : null;
    }

    private static long integer(JsonObject object, String name) {
        if (object == null || !object.has(name)) {
            return 0L;
        }
        JsonElement value = object.get(name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return 0L;
        }
        try {
            return Math.max(0L, value.getAsLong());
        } catch (RuntimeException exception) {
            return 0L;
        }
    }
}
