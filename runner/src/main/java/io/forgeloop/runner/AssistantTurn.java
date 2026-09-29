package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** Vendor-native assistant replay. Only the adapter that produced replay may serialize it. */
public record AssistantTurn(String text, List<ToolCall> toolCalls, JsonNode replay) implements ConversationItem {
    public AssistantTurn {
        text = text == null ? "" : text;
        toolCalls = List.copyOf(toolCalls == null ? List.of() : toolCalls);
        if (replay == null || (!replay.isArray() && !replay.isObject()))
            throw new IllegalArgumentException("Assistant replay must be a JSON object or array");
        replay = replay.deepCopy();
    }

    @Override public JsonNode replay() { return replay.deepCopy(); }
}
