package io.forgeloop.runner;

import java.util.List;

/** Results for all tool calls in one assistant turn, kept together in call order. */
public record ToolResults(List<ToolResultItem> results) implements ConversationItem {
    public ToolResults {
        results = List.copyOf(results == null ? List.of() : results);
        if (results.isEmpty()) throw new IllegalArgumentException("At least one tool result is required");
    }
}
