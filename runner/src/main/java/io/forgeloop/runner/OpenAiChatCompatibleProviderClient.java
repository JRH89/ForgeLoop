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

/** Chat-completions adapter for a runner-local OpenAI-compatible server. */
public final class OpenAiChatCompatibleProviderClient implements ProviderClient, ConversationClient {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http; private final URI endpoint; private final String apiKey;
    public OpenAiChatCompatibleProviderClient(HttpClient http, URI endpoint, String apiKey) {
        if (http == null || endpoint == null) throw new IllegalArgumentException("Local provider endpoint is required");
        this.http = http; this.endpoint = endpoint; this.apiKey = apiKey == null ? "" : apiKey;
    }
    @Override public ProviderResult execute(ProviderRequest request) throws ProviderException {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", request.model());
            payload.put("max_tokens", request.maxOutputTokens());
            payload.put("messages", List.of(Map.of("role", "system", "content", request.instructions()), Map.of("role", "user", "content", request.input())));
            if (request.outputSchema() != null) {
                payload.put("response_format", Map.of("type", "json_schema", "json_schema", Map.of(
                        "name", "forgeloop_output", "strict", true, "schema", request.outputSchema())));
            }
            String body = JSON.writeValueAsString(payload);
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint).timeout(Duration.ofMinutes(5)).header("content-type", "application/json");
            if (!apiKey.isBlank()) builder.header("authorization", "Bearer " + apiKey);
            HttpResponse<String> response = http.send(builder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw ProviderHttpErrors.from("Local", response.statusCode(), response.body());
            return parse(response.body());
        } catch (ProviderException exception) { throw exception;
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new ProviderException("Local provider request was interrupted", true, exception);
        } catch (Exception exception) { throw new ProviderException("Local provider request failed", true, exception); }
    }
    static ProviderResult parse(String body) throws Exception {
        JsonNode response = JSON.readTree(body); StringBuilder output = new StringBuilder();
        for (JsonNode choice : response.path("choices")) if (choice.path("message").hasNonNull("content")) output.append(choice.path("message").path("content").asText());
        JsonNode usage = response.path("usage");
        return new ProviderResult(output.toString(), usage.path("prompt_tokens").asLong(), usage.path("completion_tokens").asLong(), response.path("id").asText(null), response.path("model").asText(null));
    }

    @Override public String serialize(ConversationRequest request) {
        try { return conversationBody(request); }
        catch (Exception exception) { throw new IllegalArgumentException("Local conversation request could not be serialized", exception); }
    }

    static String conversationBody(ConversationRequest request) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", request.model());
        payload.put("max_tokens", request.maxOutputTokens());
        List<Object> messages = new java.util.ArrayList<>();
        messages.add(Map.of("role", "system", "content", request.instructions()));
        for (ConversationItem item : request.items()) {
            if (item instanceof UserText user) messages.add(Map.of("role", "user", "content", user.text()));
            else if (item instanceof AssistantTurn assistant) messages.add(assistant.replay());
            else if (item instanceof ToolResults results) for (ToolResultItem result : results.results()) {
                messages.add(Map.of("role", "tool", "tool_call_id", result.callId(),
                        "content", result.error() ? "ERROR: " + result.content() : result.content()));
            }
        }
        payload.put("messages", messages);
        if (!request.tools().isEmpty()) payload.put("tools", request.tools().stream().map(tool -> Map.of("type", "function",
                "function", Map.of("name", tool.name(), "description", tool.description(), "parameters", tool.inputSchema()))).toList());
        return JSON.writeValueAsString(payload);
    }

    @Override public ConversationTurn converse(ConversationRequest request) throws ProviderException {
        try {
            String body = serialize(request);
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint).timeout(request.timeout()).header("content-type", "application/json");
            if (!apiKey.isBlank()) builder.header("authorization", "Bearer " + apiKey);
            HttpResponse<String> response = http.send(builder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw ProviderHttpErrors.from("Local", response.statusCode(), response.body());
            return parseConversation(response.body());
        } catch (ProviderException exception) { throw exception;
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new ProviderException("Local conversation was interrupted", true, exception);
        } catch (Exception exception) { throw new ProviderException("Local conversation failed", true, exception); }
    }

    static ConversationTurn parseConversation(String body) throws Exception {
        JsonNode response = JSON.readTree(body);
        JsonNode choices = response.path("choices");
        if (!response.isObject() || !choices.isArray() || choices.isEmpty()) throw new IllegalArgumentException("Local conversation response is invalid");
        JsonNode choice = choices.get(0), message = choice.path("message");
        if (!message.isObject()) throw new IllegalArgumentException("Local conversation message is invalid");
        String text = message.path("content").isTextual() ? message.path("content").asText() : "";
        List<ToolCall> calls = new java.util.ArrayList<>();
        JsonNode rawCalls = message.path("tool_calls");
        if (!rawCalls.isMissingNode() && !rawCalls.isNull()) {
            if (!rawCalls.isArray()) throw new IllegalArgumentException("Local tool calls must be an array");
            for (JsonNode call : rawCalls) {
                String rawArguments = call.path("function").path("arguments").asText(null);
                JsonNode arguments = rawArguments == null ? null : JSON.readTree(rawArguments);
                if (arguments == null || !arguments.isObject()) throw new IllegalArgumentException("Local tool arguments must be a JSON object");
                calls.add(new ToolCall(call.path("id").asText(), call.path("function").path("name").asText(), arguments));
            }
        }
        String finishReason = choice.path("finish_reason").asText("");
        StopReason reason = switch (finishReason) {
            case "tool_calls" -> StopReason.TOOL_USE;
            case "length" -> StopReason.MAX_TOKENS;
            case "content_filter" -> StopReason.REFUSAL;
            case "stop" -> StopReason.END_TURN;
            default -> StopReason.OTHER;
        };
        JsonNode usage = response.path("usage");
        return new ConversationTurn(text, calls, reason, usage.path("prompt_tokens").asLong(), usage.path("completion_tokens").asLong(),
                response.path("id").asText(null), message, body, response.path("model").asText(null));
    }
}
