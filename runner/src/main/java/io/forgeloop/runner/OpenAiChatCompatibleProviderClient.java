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

/** Chat-completions adapter for a runner-local OpenAI-compatible server. */
public final class OpenAiChatCompatibleProviderClient implements ProviderClient {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http; private final URI endpoint; private final String apiKey;
    public OpenAiChatCompatibleProviderClient(HttpClient http, URI endpoint, String apiKey) {
        if (http == null || endpoint == null) throw new IllegalArgumentException("Local provider endpoint is required");
        this.http = http; this.endpoint = endpoint; this.apiKey = apiKey == null ? "" : apiKey;
    }
    @Override public ProviderResult execute(ProviderRequest request) throws ProviderException {
        try {
            String body = JSON.writeValueAsString(Map.of("model", request.model(), "max_tokens", request.maxOutputTokens(),
                    "messages", List.of(Map.of("role", "system", "content", request.instructions()), Map.of("role", "user", "content", request.input()))));
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint).timeout(Duration.ofMinutes(5)).header("content-type", "application/json");
            if (!apiKey.isBlank()) builder.header("authorization", "Bearer " + apiKey);
            HttpResponse<String> response = http.send(builder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new ProviderException("Local provider request failed with HTTP " + response.statusCode(), response.statusCode() == 429 || response.statusCode() >= 500);
            return parse(response.body());
        } catch (ProviderException exception) { throw exception;
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new ProviderException("Local provider request was interrupted", true, exception);
        } catch (Exception exception) { throw new ProviderException("Local provider request failed", true, exception); }
    }
    static ProviderResult parse(String body) throws Exception {
        JsonNode response = JSON.readTree(body); StringBuilder output = new StringBuilder();
        for (JsonNode choice : response.path("choices")) if (choice.path("message").hasNonNull("content")) output.append(choice.path("message").path("content").asText());
        JsonNode usage = response.path("usage");
        return new ProviderResult(output.toString(), usage.path("prompt_tokens").asLong(), usage.path("completion_tokens").asLong(), response.path("id").asText(null));
    }
}
