package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Anthropic Messages API adapter; its key remains in the runner's environment and is never serialized as telemetry. */
public final class AnthropicMessagesProviderClient implements ProviderClient {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http; private final URI endpoint; private final String apiKey;

    public AnthropicMessagesProviderClient(HttpClient http, URI endpoint, String apiKey) {
        if (http == null || endpoint == null || apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("Anthropic runner credential is required");
        this.http = http; this.endpoint = endpoint; this.apiKey = apiKey;
    }

    @Override public ProviderResult execute(ProviderRequest request) throws ProviderException {
        try {
            String body = JSON.writeValueAsString(Map.of("model", request.model(), "system", request.instructions(), "max_tokens", request.maxOutputTokens(),
                    "messages", List.of(Map.of("role", "user", "content", request.input()))));
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(endpoint).timeout(Duration.ofMinutes(5))
                    .header("x-api-key", apiKey).header("anthropic-version", "2023-06-01").header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new ProviderException("Anthropic provider request failed with HTTP " + response.statusCode(), response.statusCode() == 429 || response.statusCode() >= 500);
            return parse(response.body());
        } catch (ProviderException exception) { throw exception;
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new ProviderException("Anthropic provider request was interrupted", true, exception);
        } catch (Exception exception) { throw new ProviderException("Anthropic provider request failed", true, exception); }
    }

    static ProviderResult parse(String body) throws Exception {
        JsonNode response = JSON.readTree(body); StringBuilder output = new StringBuilder();
        for (JsonNode content : response.path("content")) if ("text".equals(content.path("type").asText())) output.append(content.path("text").asText());
        JsonNode usage = response.path("usage");
        return new ProviderResult(output.toString(), usage.path("input_tokens").asLong(), usage.path("output_tokens").asLong(), response.path("id").asText(null));
    }
}
