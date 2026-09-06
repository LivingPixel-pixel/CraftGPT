package dev.craftgpt.client.codex;

public enum CodexGenerationStage {
    IDLE(false),
    REFRESHING(true),
    STARTING(true),
    THINKING(true),
    WRITING(true),
    REVIEWING(true),
    REPAIRING(true),
    IMPORTING(true),
    VALIDATING(true),
    READY(false),
    FAILED(false),
    CANCELLED(false);

    private final boolean active;

    CodexGenerationStage(boolean active) {
        this.active = active;
    }

    public boolean active() {
        return active;
    }

    /** Number of workflow steps that are genuinely complete, not merely active. */
    public int completedSteps() {
        return switch (this) {
            case IDLE, REFRESHING -> 0;
            case STARTING, THINKING, WRITING, REVIEWING, REPAIRING, FAILED, CANCELLED -> 1;
            case IMPORTING -> 2;
            case VALIDATING -> 3;
            case READY -> 5;
        };
    }

    /** One-based active workflow step, or zero when no step is currently running. */
    public int currentStep() {
        return switch (this) {
            case REFRESHING -> 1;
            case STARTING, THINKING, WRITING, REVIEWING, REPAIRING -> 2;
            case IMPORTING -> 3;
            case VALIDATING -> 4;
            default -> 0;
        };
    }
}
