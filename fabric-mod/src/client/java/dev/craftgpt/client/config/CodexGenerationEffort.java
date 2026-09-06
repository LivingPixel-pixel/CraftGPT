package dev.craftgpt.client.config;

/** Maps the user-facing effort slider to a bounded number of Codex turns. */
public final class CodexGenerationEffort {
    public static final int MINIMUM = 1;
    public static final int MAXIMUM = 7;
    public static final int DEFAULT = 4;

    private CodexGenerationEffort() {
    }

    public static int clamp(int effort) {
        return Math.max(MINIMUM, Math.min(MAXIMUM, effort));
    }

    public static int visualReviewRounds(int effort) {
        return clamp(effort) - 1;
    }

    public static double sliderValue(int effort) {
        return (clamp(effort) - MINIMUM) / (double) (MAXIMUM - MINIMUM);
    }

    public static int fromSlider(double value) {
        double bounded = Math.max(0.0, Math.min(1.0, value));
        return clamp(MINIMUM + (int) Math.round(bounded * (MAXIMUM - MINIMUM)));
    }
}
