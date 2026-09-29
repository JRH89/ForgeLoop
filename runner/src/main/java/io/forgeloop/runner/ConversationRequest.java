package io.forgeloop.runner;

import java.time.Duration;
import java.util.List;

/** Immutable, bounded request for one turn of an append-only provider conversation. */
public record ConversationRequest(String model, String instructions, List<ConversationItem> items, List<ToolSpec> tools,
                                  int maxOutputTokens, Duration timeout) {
    public ConversationRequest {
        if (model == null || model.isBlank() || instructions == null || instructions.isBlank()
                || items == null || items.isEmpty() || tools == null || maxOutputTokens < 128 || maxOutputTokens > 32_768
                || timeout == null || timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofMinutes(10)) > 0)
            throw new IllegalArgumentException("Provider conversation request is invalid");
        items = List.copyOf(items);
        tools = List.copyOf(tools);
        if (items.stream().anyMatch(java.util.Objects::isNull) || tools.stream().anyMatch(java.util.Objects::isNull)
                || tools.stream().map(ToolSpec::name).distinct().count() != tools.size())
            throw new IllegalArgumentException("Conversation items and tool declarations must be valid and unique");
    }
}
