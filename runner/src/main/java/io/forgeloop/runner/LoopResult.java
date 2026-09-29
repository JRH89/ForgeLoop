package io.forgeloop.runner;

/** Stable result for dispatch and tests; no prompt or repository content is included. */
public record LoopResult(LoopOutcome outcome, BudgetKind budgetKind, LoopCounters counters,
                         String changeSha, String category, int changedFiles, HoldClass holdClass, String holdRule) {
    public LoopResult {
        if (outcome == null || counters == null || changedFiles < 0) throw new IllegalArgumentException("Agent loop result is invalid");
        if (outcome != LoopOutcome.BUDGET_STOP && budgetKind != null) throw new IllegalArgumentException("Only a budget stop has a budget kind");
        if ((outcome == LoopOutcome.POLICY_HOLD) != (holdClass != null && holdRule != null && !holdRule.isBlank()))
            throw new IllegalArgumentException("Policy-hold result metadata is invalid");
    }

    public LoopResult(LoopOutcome outcome, BudgetKind budgetKind, LoopCounters counters,
                      String changeSha, String category, int changedFiles) {
        this(outcome, budgetKind, counters, changeSha, category, changedFiles, null, null);
    }
}
