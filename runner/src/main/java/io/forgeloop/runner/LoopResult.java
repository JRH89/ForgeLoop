package io.forgeloop.runner;

/** Stable result for dispatch and tests; no prompt or repository content is included. */
public record LoopResult(LoopOutcome outcome, BudgetKind budgetKind, LoopCounters counters,
                         String changeSha, String category, int changedFiles) {
    public LoopResult {
        if (outcome == null || counters == null || changedFiles < 0) throw new IllegalArgumentException("Agent loop result is invalid");
        if (outcome != LoopOutcome.BUDGET_STOP && budgetKind != null) throw new IllegalArgumentException("Only a budget stop has a budget kind");
    }
}
