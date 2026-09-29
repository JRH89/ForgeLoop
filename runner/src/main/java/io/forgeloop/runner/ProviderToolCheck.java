package io.forgeloop.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;

/** Executes a bounded echo-tool conversation without displaying provider or tool content. */
public final class ProviderToolCheck {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String INSTRUCTIONS = "This is a tool-calling connectivity check. Call echo exactly once with a short text, then reply briefly.";
    private static final ToolSpec ECHO = new ToolSpec("echo", "Return the supplied text unchanged.", schema());

    public ProviderToolCheckResult run(ConversationClient client, String model, int maxAttempts) throws ProviderExecutionFailure {
        ConversationRequest firstRequest = new ConversationRequest(model, INSTRUCTIONS,
                List.of(new UserText("Use the echo tool with the text ForgeLoop tool check.")), List.of(ECHO), 512, Duration.ofMinutes(2));
        ConversationExecution first = new ConversationExecutionService().converse(client, firstRequest, maxAttempts);
        if (first.turn().stopReason() != StopReason.TOOL_USE || first.turn().toolCalls().isEmpty()
                || first.turn().toolCalls().stream().anyMatch(call -> !ECHO.name().equals(call.name()) || !call.arguments().path("text").isTextual()))
            throw new IllegalStateException("Provider tool check did not produce the expected echo call");

        List<ToolResultItem> results = first.turn().toolCalls().stream().map(call -> new ToolResultItem(
                call.id(), ECHO.name(), call.arguments().path("text").asText(), false)).toList();
        ConversationRequest secondRequest = new ConversationRequest(model, INSTRUCTIONS,
                List.of(firstRequest.items().getFirst(), first.turn().toAssistantTurn(), new ToolResults(results)), List.of(ECHO), 512, Duration.ofMinutes(2));
        ConversationExecution second = new ConversationExecutionService().converse(client, secondRequest, maxAttempts);
        if (second.turn().stopReason() != StopReason.END_TURN || !second.turn().toolCalls().isEmpty())
            throw new IllegalStateException("Provider tool check did not complete the follow-up turn");
        return new ProviderToolCheckResult(first.turn().stopReason(), second.turn().stopReason(),
                Math.addExact(first.turn().inputTokens(), second.turn().inputTokens()),
                Math.addExact(first.turn().outputTokens(), second.turn().outputTokens()), first.attemptCount(), second.attemptCount());
    }

    private static com.fasterxml.jackson.databind.JsonNode schema() {
        var schema = JSON.createObjectNode();
        schema.put("type", "object");
        var properties = JSON.createObjectNode();
        properties.set("text", JSON.createObjectNode().put("type", "string"));
        schema.set("properties", properties);
        schema.set("required", JSON.createArrayNode().add("text"));
        schema.put("additionalProperties", false);
        return schema;
    }
}
