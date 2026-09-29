package io.forgeloop.runner;

/** Immutable loop accounting snapshot shared with tool implementations and reporters. */
public record LoopCounters(int turns, int toolCalls, long inputTokens, long outputTokens,
                           long conversationBytes, long knownCostMicros) {
    public LoopCounters {
        if (turns < 0 || toolCalls < 0 || inputTokens < 0 || outputTokens < 0 || conversationBytes < 0 || knownCostMicros < 0)
            throw new IllegalArgumentException("Loop counters cannot be negative");
    }
    public long tokens() { return inputTokens + outputTokens; }
}
