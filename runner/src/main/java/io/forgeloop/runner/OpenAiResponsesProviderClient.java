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
import java.util.Map;

/** OpenAI Responses API adapter. The API key is read only from the runner environment at construction time. */
public final class OpenAiResponsesProviderClient implements ProviderClient {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http;
    private final URI endpoint;
    private final String apiKey;

    public OpenAiResponsesProviderClient(HttpClient http, URI endpoint, String apiKey) {
        if (http == null || endpoint == null || apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("OpenAI runner credential is required");
        this.http = http; this.endpoint = endpoint; this.apiKey = apiKey;
    }

    @Override public ProviderResult execute(ProviderRequest request) throws ProviderException {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", request.model());
            payload.put("instructions", request.instructions());
            payload.put("input", request.input());
            payload.put("max_output_tokens", request.maxOutputTokens());
            payload.put("store", false);
            if (request.outputSchema() != null) {
                payload.put("text", Map.of("format", Map.of("type", "json_schema", "name", "forgeloop_output",
                        "strict", true, "schema", request.outputSchema())));
            }
            String body = JSON.writeValueAsString(payload);
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(endpoint).timeout(Duration.ofMinutes(5))
                    .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw ProviderHttpErrors.from("OpenAI", response.statusCode(), response.body());
            return parse(response.body());
        } catch (ProviderException exception) { throw exception;
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new ProviderException("OpenAI provider request was interrupted", true, exception);
        } catch (Exception exception) { throw new ProviderException("OpenAI provider request failed", true, exception); }
    }

    static ProviderResult parse(String body) throws Exception {
        JsonNode response = JSON.readTree(body); StringBuilder output = new StringBuilder();
        for (JsonNode item : response.path("output")) for (JsonNode content : item.path("content")) if ("output_text".equals(content.path("type").asText())) output.append(content.path("text").asText());
        JsonNode usage = response.path("usage");
        return new ProviderResult(output.toString(), usage.path("input_tokens").asLong(), usage.path("output_tokens").asLong(), response.path("id").asText(null));
    }
}
