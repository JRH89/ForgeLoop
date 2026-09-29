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

/** Native Gemini generateContent adapter; credentials and raw content remain runner-local. */
public final class GeminiGenerateContentProviderClient implements ProviderClient, ConversationClient {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http; private final URI endpoint; private final String apiKey;
    public GeminiGenerateContentProviderClient(HttpClient http, URI endpoint, String apiKey) {
        if (http == null || endpoint == null || apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("Gemini runner credential is required");
        this.http = http; this.endpoint = endpoint; this.apiKey = apiKey;
    }
    @Override public ProviderResult execute(ProviderRequest request) throws ProviderException {
        try {
            URI target = endpoint.resolve("models/" + request.model() + ":generateContent");
            String body = requestBody(request);
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(target).timeout(Duration.ofMinutes(5))
                    .header("x-goog-api-key", apiKey).header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw ProviderHttpErrors.from("Gemini", response.statusCode(), response.body());
            return parse(response.body());
        } catch (ProviderException exception) { throw exception;
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new ProviderException("Gemini provider request was interrupted", true, exception);
        } catch (Exception exception) { throw new ProviderException("Gemini provider request failed", true, exception); }
    }
    static String requestBody(ProviderRequest request) throws Exception {
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("maxOutputTokens", request.maxOutputTokens());
        generationConfig.put("responseMimeType", "application/json");
        if (request.outputSchema() != null) generationConfig.put("responseJsonSchema", request.outputSchema());
        return JSON.writeValueAsString(Map.of(
                "system_instruction", Map.of("parts", List.of(Map.of("text", request.instructions()))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", request.input())))),
                "generationConfig", generationConfig));
    }
    static ProviderResult parse(String body) throws Exception {
        JsonNode response = JSON.readTree(body); StringBuilder output = new StringBuilder();
        for (JsonNode candidate : response.path("candidates")) for (JsonNode part : candidate.path("content").path("parts")) if (part.hasNonNull("text")) output.append(part.path("text").asText());
        JsonNode usage = response.path("usageMetadata");
        return new ProviderResult(output.toString(), usage.path("promptTokenCount").asLong(), usage.path("candidatesTokenCount").asLong(), response.path("responseId").asText(null), response.path("modelVersion").asText(null), body);
    }

    @Override public String serialize(ConversationRequest request) {
        try { return conversationBody(request); }
        catch (Exception exception) { throw new IllegalArgumentException("Gemini conversation request could not be serialized", exception); }
    }

    static String conversationBody(ConversationRequest request) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("system_instruction", Map.of("parts", List.of(Map.of("text", request.instructions()))));
        payload.put("contents", conversationContents(request.items()));
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("maxOutputTokens", request.maxOutputTokens());
        payload.put("generationConfig", generationConfig);
        if (!request.tools().isEmpty()) {
            payload.put("tools", List.of(Map.of("functionDeclarations", request.tools().stream().map(tool -> Map.of(
                    "name", tool.name(), "description", tool.description(), "parameters", tool.inputSchema())).toList())));
            payload.put("toolConfig", Map.of("functionCallingConfig", Map.of("mode", "AUTO")));
        }
        return JSON.writeValueAsString(payload);
    }

    private static List<Object> conversationContents(List<ConversationItem> items) {
        List<Object> contents = new java.util.ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            ConversationItem item = items.get(index);
            if (item instanceof UserText user) {
                contents.add(Map.of("role", "user", "parts", List.of(Map.of("text", user.text()))));
            } else if (item instanceof AssistantTurn assistant) {
                contents.add(assistant.replay());
            } else if (item instanceof ToolResults toolResults) {
                List<Object> parts = new java.util.ArrayList<>();
                for (ToolResultItem result : toolResults.results()) parts.add(Map.of("functionResponse", Map.of("id", result.callId(),
                        "name", result.toolName(), "response", result.error() ? Map.of("error", result.content()) : Map.of("result", result.content()))));
                // Keep provider tool replies and immediately following user text in one user turn.
                while (index + 1 < items.size() && items.get(index + 1) instanceof UserText user) {
                    parts.add(Map.of("text", user.text()));
                    index++;
                }
                contents.add(Map.of("role", "user", "parts", parts));
            }
        }
        return contents;
    }

    @Override public ConversationTurn converse(ConversationRequest request) throws ProviderException {
        try {
            String body = serialize(request);
            URI target = endpoint.resolve("models/" + request.model() + ":generateContent");
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(target).timeout(request.timeout())
                    .header("x-goog-api-key", apiKey).header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw ProviderHttpErrors.from("Gemini", response.statusCode(), response.body());
            return parseConversation(response.body(), request.items().stream().filter(AssistantTurn.class::isInstance).count() + 1);
        } catch (ProviderException exception) { throw exception;
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new ProviderException("Gemini conversation was interrupted", true, exception);
        } catch (Exception exception) { throw new ProviderException("Gemini conversation failed", true, exception); }
    }

    static ConversationTurn parseConversation(String body) throws Exception { return parseConversation(body, 1); }

    static ConversationTurn parseConversation(String body, long turnNumber) throws Exception {
        JsonNode response = JSON.readTree(body);
        JsonNode candidates = response.path("candidates");
        if (!response.isObject()) throw new IllegalArgumentException("Gemini conversation response is invalid");
        if (!candidates.isArray() || candidates.isEmpty()) {
            String blocked = response.path("promptFeedback").path("blockReason").asText("");
            if (!blocked.isBlank()) return new ConversationTurn("", List.of(), StopReason.REFUSAL, response.path("usageMetadata").path("promptTokenCount").asLong(), 0,
                    response.path("responseId").asText(null), JSON.createArrayNode(), body, response.path("modelVersion").asText(null));
            throw new IllegalArgumentException("Gemini response has no candidate");
        }
        JsonNode candidate = candidates.get(0), content = candidate.path("content"), parts = content.path("parts");
        if (!parts.isArray()) throw new IllegalArgumentException("Gemini conversation parts are invalid");
        StringBuilder text = new StringBuilder();
        List<ToolCall> calls = new java.util.ArrayList<>();
        for (JsonNode part : parts) {
            if (part.hasNonNull("text")) text.append(part.path("text").asText());
            if (part.has("functionCall")) {
                JsonNode function = part.path("functionCall"), arguments = function.path("args");
                if (!arguments.isObject()) throw new IllegalArgumentException("Gemini tool arguments must be a JSON object");
                String id = function.path("id").asText(null);
                if (id == null || id.isBlank()) id = "g" + turnNumber + "-" + calls.size();
                calls.add(new ToolCall(id, function.path("name").asText(), arguments));
            }
        }
        String finish = candidate.path("finishReason").asText("");
        if ("MALFORMED_FUNCTION_CALL".equals(finish) || "UNEXPECTED_TOOL_CALL".equals(finish))
            throw new IllegalArgumentException("Gemini returned an invalid function call");
        StopReason reason = switch (finish) {
            case "MAX_TOKENS" -> StopReason.MAX_TOKENS;
            case "TOO_MANY_TOOL_CALLS" -> StopReason.OTHER;
            case "STOP" -> calls.isEmpty() ? StopReason.END_TURN : StopReason.TOOL_USE;
            case "" -> calls.isEmpty() ? StopReason.OTHER : StopReason.TOOL_USE;
            default -> StopReason.REFUSAL;
        };
        JsonNode usage = response.path("usageMetadata");
        long outputTokens = usage.path("candidatesTokenCount").asLong() + usage.path("thoughtsTokenCount").asLong();
        return new ConversationTurn(text.toString(), calls, reason, usage.path("promptTokenCount").asLong(), outputTokens,
                response.path("responseId").asText(null), content, body, response.path("modelVersion").asText(null));
    }
}
