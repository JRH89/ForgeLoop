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

/** OpenAI Responses API adapter. The API key is read only from the runner environment at construction time. */
public final class OpenAiResponsesProviderClient implements ProviderClient, ConversationClient {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http;
    private final URI endpoint;
    private final String apiKey;

    public OpenAiResponsesProviderClient(HttpClient http, URI endpoint, String apiKey) {
        if (http == null || endpoint == null || apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("OpenAI runner credential is required");
        this.http = http; this.endpoint = endpoint; this.apiKey = apiKey;
    }

    @Override public ProviderResult execute(ProviderRequest request) throws ProviderException {
        String body = executeRaw(request);
        try { return parse(body); }
        catch (Exception exception) { throw new ProviderException("OpenAI provider response could not be parsed", true, exception); }
    }

    @Override public String executeRaw(ProviderRequest request) throws ProviderException {
        try {
            String body = requestBody(request);
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(endpoint).timeout(Duration.ofMinutes(5))
                    .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw ProviderHttpErrors.from("OpenAI", response.statusCode(), response.body());
            return response.body();
        } catch (ProviderException exception) { throw exception;
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new ProviderException("OpenAI provider request was interrupted", true, exception);
        } catch (Exception exception) { throw new ProviderException("OpenAI provider request failed", true, exception); }
    }

    @Override public String adapterId() { return "openai-responses/1"; }

    static String requestBody(ProviderRequest request) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", request.model());
        payload.put("instructions", request.instructions());
        payload.put("input", request.input());
        payload.put("max_output_tokens", request.maxOutputTokens());
        payload.put("store", false);
        if (request.outputSchema() != null) payload.put("text", Map.of("format", Map.of("type", "json_schema",
                "name", "forgeloop_output", "strict", true, "schema", request.outputSchema())));
        return JSON.writeValueAsString(payload);
    }

    static ProviderResult parse(String body) throws Exception {
        JsonNode response = JSON.readTree(body); StringBuilder output = new StringBuilder();
        for (JsonNode item : response.path("output")) for (JsonNode content : item.path("content")) if ("output_text".equals(content.path("type").asText())) output.append(content.path("text").asText());
        JsonNode usage = response.path("usage");
        return new ProviderResult(output.toString(), usage.path("input_tokens").asLong(), usage.path("output_tokens").asLong(), response.path("id").asText(null), response.path("model").asText(null), body);
    }

    @Override public String serialize(ConversationRequest request) {
        try { return conversationBody(request); }
        catch (Exception exception) { throw new IllegalArgumentException("OpenAI conversation request could not be serialized", exception); }
    }

    static String conversationBody(ConversationRequest request) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", request.model());
        payload.put("instructions", request.instructions());
        payload.put("input", conversationInput(request.items()));
        payload.put("max_output_tokens", request.maxOutputTokens());
        payload.put("store", false);
        if (!request.tools().isEmpty()) {
            payload.put("tools", request.tools().stream().map(tool -> Map.of("type", "function", "name", tool.name(),
                    "description", tool.description(), "parameters", tool.inputSchema())).toList());
            payload.put("include", List.of("reasoning.encrypted_content"));
        }
        return JSON.writeValueAsString(payload);
    }

    private static List<Object> conversationInput(List<ConversationItem> items) {
        List<Object> input = new java.util.ArrayList<>();
        for (ConversationItem item : items) {
            if (item instanceof UserText user) {
                input.add(Map.of("role", "user", "content", user.text()));
            } else if (item instanceof AssistantTurn assistant) {
                JsonNode replay = assistant.replay();
                replay.forEach(input::add);
            } else if (item instanceof ToolResults results) {
                for (ToolResultItem result : results.results()) input.add(Map.of("type", "function_call_output", "call_id", result.callId(),
                        "output", result.error() ? "ERROR: " + result.content() : result.content()));
            }
        }
        return input;
    }

    @Override public ConversationTurn converse(ConversationRequest request) throws ProviderException {
        try {
            String body = serialize(request);
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(endpoint).timeout(request.timeout())
                    .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw ProviderHttpErrors.from("OpenAI", response.statusCode(), response.body());
            return parseConversation(response.body());
        } catch (ProviderException exception) { throw exception;
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new ProviderException("OpenAI conversation was interrupted", true, exception);
        } catch (Exception exception) { throw new ProviderException("OpenAI conversation failed", true, exception); }
    }

    static ConversationTurn parseConversation(String body) throws Exception {
        JsonNode response = JSON.readTree(body);
        JsonNode output = response.path("output");
        if (!response.isObject() || !output.isArray()) throw new IllegalArgumentException("OpenAI conversation response is invalid");
        StringBuilder text = new StringBuilder();
        List<ToolCall> calls = new java.util.ArrayList<>();
        boolean refusal = false;
        for (JsonNode item : output) {
            if ("function_call".equals(item.path("type").asText())) {
                String rawArguments = item.path("arguments").asText(null);
                JsonNode arguments = rawArguments == null ? null : JSON.readTree(rawArguments);
                if (arguments == null || !arguments.isObject()) throw new IllegalArgumentException("OpenAI tool arguments must be a JSON object");
                calls.add(new ToolCall(item.path("call_id").asText(), item.path("name").asText(), arguments));
            }
            if ("refusal".equals(item.path("type").asText())) refusal = true;
            for (JsonNode content : item.path("content")) {
                if ("output_text".equals(content.path("type").asText())) text.append(content.path("text").asText(""));
                if ("refusal".equals(content.path("type").asText())) refusal = true;
            }
        }
        String status = response.path("status").asText("");
        String incompleteReason = response.path("incomplete_details").path("reason").asText("");
        StopReason reason = "incomplete".equals(status) && "max_output_tokens".equals(incompleteReason) ? StopReason.MAX_TOKENS
                : refusal ? StopReason.REFUSAL : !calls.isEmpty() ? StopReason.TOOL_USE
                : StopReason.END_TURN;
        JsonNode usage = response.path("usage");
        return new ConversationTurn(text.toString(), calls, reason, usage.path("input_tokens").asLong(), usage.path("output_tokens").asLong(),
                response.path("id").asText(null), output, body, response.path("model").asText(null));
    }
}
