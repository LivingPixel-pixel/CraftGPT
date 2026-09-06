package dev.craftgpt.client.api;

/** Token usage reported by a completed Responses-compatible API call. */
public record ApiUsage(
    boolean reported,
    long inputTokens,
    long cachedInputTokens,
    long cacheWriteTokens,
    long outputTokens,
    long reasoningTokens,
    long totalTokens
) {
    public ApiUsage {
        inputTokens = nonNegative(inputTokens);
        cachedInputTokens = Math.min(inputTokens, nonNegative(cachedInputTokens));
        cacheWriteTokens = Math.min(
            Math.max(0L, inputTokens - cachedInputTokens),
            nonNegative(cacheWriteTokens)
        );
        outputTokens = nonNegative(outputTokens);
        reasoningTokens = Math.min(outputTokens, nonNegative(reasoningTokens));
        totalTokens = nonNegative(totalTokens);
        if (reported && totalTokens == 0L) {
            totalTokens = inputTokens + outputTokens;
        }
    }

    public static ApiUsage unavailable() {
        return new ApiUsage(false, 0L, 0L, 0L, 0L, 0L, 0L);
    }

    public long uncachedInputTokens() {
        return Math.max(0L, inputTokens - cachedInputTokens - cacheWriteTokens);
    }

    private static long nonNegative(long value) {
        return Math.max(0L, value);
    }
}
