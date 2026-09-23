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
public final class GeminiGenerateContentProviderClient implements ProviderClient {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http; private final URI endpoint; private final String apiKey;
    public GeminiGenerateContentProviderClient(HttpClient http, URI endpoint, String apiKey) {
        if (http == null || endpoint == null || apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("Gemini runner credential is required");
        this.http = http; this.endpoint = endpoint; this.apiKey = apiKey;
    }
    @Override public ProviderResult execute(ProviderRequest request) throws ProviderException {
        try {
            URI target = endpoint.resolve("models/" + request.model() + ":generateContent");
            Map<String, Object> generationConfig = new LinkedHashMap<>();
            generationConfig.put("maxOutputTokens", request.maxOutputTokens());
            generationConfig.put("responseMimeType", "application/json");
            if (request.outputSchema() != null) generationConfig.put("responseJsonSchema", request.outputSchema());
            String body = JSON.writeValueAsString(Map.of(
                    "system_instruction", Map.of("parts", List.of(Map.of("text", request.instructions()))),
                    "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", request.input())))),
                    "generationConfig", generationConfig));
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(target).timeout(Duration.ofMinutes(5))
                    .header("x-goog-api-key", apiKey).header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw ProviderHttpErrors.from("Gemini", response.statusCode(), response.body());
            return parse(response.body());
        } catch (ProviderException exception) { throw exception;
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new ProviderException("Gemini provider request was interrupted", true, exception);
        } catch (Exception exception) { throw new ProviderException("Gemini provider request failed", true, exception); }
    }
    static ProviderResult parse(String body) throws Exception {
        JsonNode response = JSON.readTree(body); StringBuilder output = new StringBuilder();
        for (JsonNode candidate : response.path("candidates")) for (JsonNode part : candidate.path("content").path("parts")) if (part.hasNonNull("text")) output.append(part.path("text").asText());
        JsonNode usage = response.path("usageMetadata");
        return new ProviderResult(output.toString(), usage.path("promptTokenCount").asLong(), usage.path("candidatesTokenCount").asLong(), response.path("responseId").asText(null));
    }
}
