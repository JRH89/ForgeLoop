package io.forgeloop.runner;

/** Per-attempt hard limits. Values mirror the bounded control-plane policy contract. */
public record LoopBudget(int maxToolCalls, int maxTokens, int maxWallSeconds, int maxConversationBytes) {
    public LoopBudget {
        if (maxToolCalls < 1 || maxToolCalls > 1_000 || maxTokens < 10_000 || maxTokens > 100_000_000
                || maxWallSeconds < 60 || maxWallSeconds > 14_400 || maxConversationBytes < 65_536 || maxConversationBytes > 4_194_304)
            throw new IllegalArgumentException("Agent loop budget is outside policy bounds");
    }
}
