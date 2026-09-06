package dev.craftgpt.validation;

/** One bounded, user-visible validation finding with an actionable correction. */
public record ValidationProblem(
    String code,
    String location,
    String cause,
    String suggestion
) {
    public static final int MAX_PROBLEMS = 64;
    public static final int MAX_CODE_LENGTH = 96;
    public static final int MAX_LOCATION_LENGTH = 160;
    public static final int MAX_EXPLANATION_LENGTH = 384;

    public ValidationProblem {
        code = bounded(code, "unknown_failure", MAX_CODE_LENGTH);
        location = bounded(location, "result", MAX_LOCATION_LENGTH);
        cause = bounded(cause, "The validator could not accept this value.", MAX_EXPLANATION_LENGTH);
        suggestion = bounded(
            suggestion,
            "Replace the value with one that follows the CraftGPT schema and current area limits.",
            MAX_EXPLANATION_LENGTH
        );
    }

    private static String bounded(String value, String fallback, int maximumLength) {
        String safe = value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
        if (safe.isBlank()) safe = fallback;
        return safe.length() <= maximumLength ? safe : safe.substring(0, maximumLength);
    }
}
