package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Anthropic Messages API adapter; its key remains in the runner's environment and is never serialized as telemetry. */
public final class AnthropicMessagesProviderClient implements ProviderClient, ConversationClient {
    public static final String API_VERSION = ProviderClientFactory.ANTHROPIC_API_VERSION;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http; private final URI endpoint; private final String apiKey;

    public AnthropicMessagesProviderClient(HttpClient http, URI endpoint, String apiKey) {
        if (http == null || endpoint == null || apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("Anthropic runner credential is required");
        this.http = http; this.endpoint = endpoint; this.apiKey = apiKey;
    }

    @Override public String adapterId() { return "anthropic-messages/1"; }

    @Override public ProviderResult execute(ProviderRequest request) throws ProviderException {
        String body = executeRaw(request);
        try { return parse(body); }
        catch (Exception exception) { throw new ProviderException("Anthropic provider response could not be parsed", true, exception); }
    }

    @Override public String executeRaw(ProviderRequest request) throws ProviderException {
        try {
            String body = requestBody(request);
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(endpoint).timeout(Duration.ofMinutes(5))
                    .header("x-api-key", apiKey).header("anthropic-version", API_VERSION).header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw ProviderHttpErrors.from("Anthropic", response.statusCode(), response.body());
            return response.body();
        } catch (ProviderException exception) { throw exception;
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new ProviderException("Anthropic provider request was interrupted", true, exception);
        } catch (Exception exception) { throw new ProviderException("Anthropic provider request failed", true, exception); }
    }

    static String requestBody(ProviderRequest request) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", request.model());
        payload.put("system", request.instructions());
        payload.put("max_tokens", request.maxOutputTokens());
        payload.put("messages", List.of(Map.of("role", "user", "content", request.input())));
        if (request.outputSchema() != null) {
            payload.put("output_config", Map.of("format", Map.of("type", "json_schema",
                    "schema", AnthropicSchemaCompatibility.normalize(request.outputSchema()))));
        }
        return JSON.writeValueAsString(payload);
    }

    static ProviderResult parse(String body) throws Exception {
        JsonNode response = JSON.readTree(body);
        if ("max_tokens".equals(response.path("stop_reason").asText())) throw new IllegalArgumentException("Anthropic output reached the token limit");
        StringBuilder output = new StringBuilder();
        for (JsonNode content : response.path("content")) if ("text".equals(content.path("type").asText())) output.append(content.path("text").asText());
        JsonNode usage = response.path("usage");
        return new ProviderResult(output.toString(), usage.path("input_tokens").asLong(), usage.path("output_tokens").asLong(), response.path("id").asText(null), response.path("model").asText(null), body);
    }

    @Override public String serialize(ConversationRequest request) {
        try { return conversationBody(request); }
        catch (Exception exception) { throw new IllegalArgumentException("Anthropic conversation request could not be serialized", exception); }
    }

    static String conversationBody(ConversationRequest request) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", request.model());
        payload.put("system", request.instructions());
        payload.put("max_tokens", request.maxOutputTokens());
        payload.put("messages", conversationMessages(request.items()));
        if (!request.tools().isEmpty()) payload.put("tools", request.tools().stream().map(tool -> Map.of(
                "name", tool.name(), "description", tool.description(), "input_schema", tool.inputSchema())).toList());
        return JSON.writeValueAsString(payload);
    }

    private static List<Map<String, Object>> conversationMessages(List<ConversationItem> items) {
        List<Map<String, Object>> messages = new java.util.ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            ConversationItem item = items.get(index);
            if (item instanceof UserText user) {
                messages.add(Map.of("role", "user", "content", user.text()));
            } else if (item instanceof AssistantTurn assistant) {
                messages.add(Map.of("role", "assistant", "content", assistant.replay()));
            } else if (item instanceof ToolResults toolResults) {
                List<Object> blocks = new java.util.ArrayList<>();
                for (ToolResultItem result : toolResults.results()) blocks.add(Map.of("type", "tool_result",
                        "tool_use_id", result.callId(), "content", result.content(), "is_error", result.error()));
                // Anthropic requires tool_result blocks first, followed by any user text in that same user turn.
                while (index + 1 < items.size() && items.get(index + 1) instanceof UserText user) {
                    blocks.add(Map.of("type", "text", "text", user.text()));
                    index++;
                }
                messages.add(Map.of("role", "user", "content", blocks));
            }
        }
        return messages;
    }

    @Override public ConversationTurn converse(ConversationRequest request) throws ProviderException {
        try {
            String body = serialize(request);
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(endpoint).timeout(request.timeout())
                    .header("x-api-key", apiKey).header("anthropic-version", API_VERSION).header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw ProviderHttpErrors.from("Anthropic", response.statusCode(), response.body());
            return parseConversation(response.body());
        } catch (ProviderException exception) { throw exception;
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new ProviderException("Anthropic conversation was interrupted", true, exception);
        } catch (Exception exception) { throw new ProviderException("Anthropic conversation failed", true, exception); }
    }

    static ConversationTurn parseConversation(String body) throws Exception {
        JsonNode response = JSON.readTree(body);
        JsonNode content = response.path("content");
        if (!response.isObject() || !content.isArray()) throw new IllegalArgumentException("Anthropic conversation response is invalid");
        StringBuilder text = new StringBuilder();
        List<ToolCall> calls = new java.util.ArrayList<>();
        for (JsonNode block : content) {
            if ("text".equals(block.path("type").asText())) text.append(block.path("text").asText(""));
            if ("tool_use".equals(block.path("type").asText())) {
                JsonNode arguments = block.path("input");
                if (!arguments.isObject()) throw new IllegalArgumentException("Anthropic tool arguments must be a JSON object");
                calls.add(new ToolCall(block.path("id").asText(), block.path("name").asText(), arguments));
            }
        }
        String stop = response.path("stop_reason").asText("");
        StopReason reason = switch (stop) {
            case "end_turn" -> StopReason.END_TURN;
            case "tool_use" -> StopReason.TOOL_USE;
            case "max_tokens", "model_context_window_exceeded" -> StopReason.MAX_TOKENS;
            case "refusal" -> StopReason.REFUSAL;
            default -> StopReason.OTHER;
        };
        JsonNode usage = response.path("usage");
        return new ConversationTurn(text.toString(), calls, reason, usage.path("input_tokens").asLong(),
                usage.path("output_tokens").asLong(), response.path("id").asText(null), content, body,
                response.path("model").asText(null));
    }
}
