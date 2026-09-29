package io.forgeloop.control.domain;

import java.util.List;

/** Agent-loop policy captured for one eligible writing task. */
public record TaskAgentLoop(AgentLoopBudget budget, List<AgentLoopGate> gates) {
    public TaskAgentLoop {
        if (budget == null) throw new IllegalArgumentException("Agent loop budget is required");
        budget = budget.copy();
        gates = List.copyOf(gates == null ? List.of() : gates);
    }
}
