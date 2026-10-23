package io.forgeloop.runner;

import java.util.List;

/** Complete server-snapshotted limits and advisory gates; no model output can configure this policy. */
public record RunnerLoopConfiguration(LoopBudget budget, List<LoopGate> gates, long budgetMicros, long spentCostMicros) {
    public RunnerLoopConfiguration {
        if (budget == null || gates == null || budgetMicros < 0 || spentCostMicros < 0)
            throw new IllegalArgumentException("Dispatched loop policy is incomplete");
        gates = List.copyOf(gates);
        if (gates.stream().map(LoopGate::name).distinct().count() != gates.size()
                || gates.stream().anyMatch(gate -> !gate.image().matches(".+@sha256:[0-9a-f]{64}")))
            throw new IllegalArgumentException("Dispatched loop gates must be unique and digest-pinned");
    }
}
