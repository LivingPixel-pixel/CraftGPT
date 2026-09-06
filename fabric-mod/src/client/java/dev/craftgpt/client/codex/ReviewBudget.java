package dev.craftgpt.client.codex;

/** Immutable limit for one run, independent of subsequent settings changes. */
public final class ReviewBudget {
    private final int rounds;
    private int completed;
    private int inspections;

    public ReviewBudget(int rounds) {
        if (rounds < 0 || rounds > 6) throw new IllegalArgumentException("invalid_review_budget");
        this.rounds = rounds;
    }
    public int rounds() { return rounds; }
    public int completed() { return completed; }
    public boolean hasNext() { return completed < rounds; }
    public void completeRound() {
        if (!hasNext()) throw new IllegalStateException("review_budget_exhausted");
        completed++;
    }
    public boolean requestInspection() {
        if (inspections >= 2 || !hasNext()) return false;
        inspections++;
        return true;
    }
    public void keep() { completed = rounds; }
    public static boolean exportBlocked(boolean plannerBusy, boolean builderBusy, boolean placementBusy,
                                        boolean codexActive, boolean continuation) {
        return plannerBusy || builderBusy || placementBusy || (codexActive && !continuation);
    }
}
