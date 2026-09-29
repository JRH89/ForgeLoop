package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stores and rebuilds typed conversation requests without coupling journal JSON to provider wire formats. */
final class ConversationRequestJournalCodec {
    private static final ObjectMapper JSON = new ObjectMapper();

    private ConversationRequestJournalCodec() { }

    static Map<String, Object> encode(ConversationRequest request) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("model", request.model());
        value.put("instructions", request.instructions());
        value.put("maxOutputTokens", request.maxOutputTokens());
        value.put("timeoutMillis", request.timeout().toMillis());
        value.put("items", request.items().stream().map(ConversationRequestJournalCodec::encodeItem).toList());
        value.put("tools", request.tools().stream().map(tool -> Map.of("name", tool.name(), "description", tool.description(),
                "inputSchema", tool.inputSchema())).toList());
        return value;
    }

    static ConversationRequest decode(JsonNode value) {
        if (value == null || !value.isObject()) throw new IllegalArgumentException("Journaled conversation request is missing");
        List<ConversationItem> items = new ArrayList<>();
        for (JsonNode item : requireArray(value, "items")) items.add(decodeItem(item));
        List<ToolSpec> tools = new ArrayList<>();
        for (JsonNode tool : requireArray(value, "tools")) {
            tools.add(new ToolSpec(text(tool, "name"), text(tool, "description"), required(tool, "inputSchema")));
        }
        long timeoutMillis = value.path("timeoutMillis").asLong(-1);
        if (timeoutMillis < 1 || timeoutMillis > Duration.ofMinutes(10).toMillis())
            throw new IllegalArgumentException("Journaled conversation timeout is invalid");
        return new ConversationRequest(text(value, "model"), text(value, "instructions"), items, tools,
                value.path("maxOutputTokens").asInt(-1), Duration.ofMillis(timeoutMillis));
    }

    private static Map<String, Object> encodeItem(ConversationItem item) {
        Map<String, Object> value = new LinkedHashMap<>();
        if (item instanceof UserText text) {
            value.put("kind", "USER_TEXT"); value.put("text", text.text());
        } else if (item instanceof AssistantTurn assistant) {
            value.put("kind", "ASSISTANT_TURN"); value.put("text", assistant.text()); value.put("replay", assistant.replay());
            value.put("toolCalls", assistant.toolCalls().stream().map(call -> Map.of("id", call.id(), "name", call.name(),
                    "arguments", call.arguments())).toList());
        } else if (item instanceof ToolResults results) {
            value.put("kind", "TOOL_RESULTS");
            value.put("results", results.results().stream().map(result -> Map.of("callId", result.callId(),
                    "toolName", result.toolName(), "content", result.content(), "error", result.error())).toList());
        } else throw new IllegalArgumentException("Unknown conversation item type");
        return value;
    }

    private static ConversationItem decodeItem(JsonNode item) {
        return switch (text(item, "kind")) {
            case "USER_TEXT" -> new UserText(text(item, "text"));
            case "ASSISTANT_TURN" -> {
                List<ToolCall> calls = new ArrayList<>();
                for (JsonNode call : requireArray(item, "toolCalls"))
                    calls.add(new ToolCall(text(call, "id"), text(call, "name"), required(call, "arguments")));
                yield new AssistantTurn(item.path("text").asText(""), calls, required(item, "replay"));
            }
            case "TOOL_RESULTS" -> {
                List<ToolResultItem> results = new ArrayList<>();
                for (JsonNode result : requireArray(item, "results")) {
                    if (!result.path("error").isBoolean()) throw new IllegalArgumentException("Journaled tool result is invalid");
                    results.add(new ToolResultItem(text(result, "callId"), text(result, "toolName"),
                            text(result, "content"), result.path("error").asBoolean()));
                }
                yield new ToolResults(results);
            }
            default -> throw new IllegalArgumentException("Journaled conversation item type is unsupported");
        };
    }

    private static List<JsonNode> requireArray(JsonNode node, String field) {
        JsonNode array = node.path(field);
        if (!array.isArray()) throw new IllegalArgumentException("Journaled conversation request field is invalid");
        List<JsonNode> values = new ArrayList<>(); array.forEach(values::add); return values;
    }

    private static JsonNode required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) throw new IllegalArgumentException("Journaled conversation value is missing");
        return value.deepCopy();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) throw new IllegalArgumentException("Journaled conversation text is invalid");
        return value.asText();
    }
}
