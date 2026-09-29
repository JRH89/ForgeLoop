package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** Normalized turn result plus private vendor replay and response data for the next request. */
public record ConversationTurn(String text, List<ToolCall> toolCalls, StopReason stopReason, long inputTokens,
                               long outputTokens, String providerRequestId, JsonNode replay, String responseBody,
                               String answeredModel) {
    public ConversationTurn(String text, List<ToolCall> toolCalls, StopReason stopReason, long inputTokens,
                            long outputTokens, String providerRequestId, JsonNode replay, String responseBody) {
        this(text, toolCalls, stopReason, inputTokens, outputTokens, providerRequestId, replay, responseBody, null);
    }

    public ConversationTurn {
        text = text == null ? "" : text;
        toolCalls = List.copyOf(toolCalls == null ? List.of() : toolCalls);
        if (stopReason == null || inputTokens < 0 || outputTokens < 0 || replay == null
                || (!replay.isArray() && !replay.isObject()) || responseBody == null)
            throw new IllegalArgumentException("Provider conversation turn is invalid");
        replay = replay.deepCopy();
    }

    @Override public JsonNode replay() { return replay.deepCopy(); }

    public AssistantTurn toAssistantTurn() { return new AssistantTurn(text, toolCalls, replay); }
}
