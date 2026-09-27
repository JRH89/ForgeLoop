package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** Unauthenticated public price lookup; no provider credential or repository data leaves this process. */
public final class ModelPriceCatalog {
    private static final URI FEED = URI.create("https://raw.githubusercontent.com/BerriAI/litellm/main/model_prices_and_context_window.json");
    private static final int MAX_BYTES = 5_000_000;
    private static final Duration FRESH_FOR = Duration.ofHours(24);
    private static final Map<String, String> PROVIDERS = Map.of("anthropic", "anthropic", "openai", "openai", "gemini", "gemini");
    private final HttpClient client;
    private final URI feed;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper();
    private JsonNode snapshot;
    private Instant fetchedAt;

    public ModelPriceCatalog() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build(), FEED, Clock.systemUTC());
    }

    ModelPriceCatalog(HttpClient client, URI feed, Clock clock) {
        this.client = client; this.feed = feed; this.clock = clock;
    }

    public synchronized Optional<Quote> find(String provider, String model) throws IOException, InterruptedException {
        if (!PROVIDERS.containsKey(provider) || model == null || !model.matches("[A-Za-z0-9_.:-]{1,200}")) return Optional.empty();
        if (snapshot == null || !clock.instant().isBefore(fetchedAt.plus(FRESH_FOR))) refresh();
        String key = provider.equals("gemini") ? "gemini/" + model : model;
        JsonNode entry = snapshot.path(key);
        // Exact provider and model matching prevents accidentally using reseller or Vertex rates.
        if (!PROVIDERS.get(provider).equals(entry.path("litellm_provider").asText())
                || !"chat".equals(entry.path("mode").asText())) return Optional.empty();
        JsonNode input = entry.path("input_cost_per_token"), output = entry.path("output_cost_per_token");
        if (!input.isNumber() || !output.isNumber() || input.decimalValue().signum() <= 0 || output.decimalValue().signum() <= 0) return Optional.empty();
        String source = entry.path("source").asText("");
        if (!officialSource(provider, source)) return Optional.empty();
        BigDecimal million = BigDecimal.valueOf(1_000_000);
        return Optional.of(new Quote(input.decimalValue().multiply(million), output.decimalValue().multiply(million), source, fetchedAt.toString()));
    }

    private void refresh() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(feed).timeout(Duration.ofSeconds(10)).GET().build();
        HttpResponse<java.io.InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (var stream = response.body()) {
            if (response.statusCode() != 200) throw new IOException("Price catalog is unavailable (HTTP " + response.statusCode() + ")");
            byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new IOException("Price catalog exceeds size limit");
            JsonNode parsed = json.readTree(bytes);
            if (parsed == null || !parsed.isObject()) throw new IOException("Price catalog is invalid");
            snapshot = parsed;
            fetchedAt = clock.instant();
        }
    }

    private static boolean officialSource(String provider, String raw) {
        try {
            URI uri = URI.create(raw);
            if (!"https".equals(uri.getScheme())) return false;
            return switch (provider) {
                case "anthropic" -> "platform.claude.com".equals(uri.getHost()) || "docs.anthropic.com".equals(uri.getHost());
                case "openai" -> "developers.openai.com".equals(uri.getHost()) || "platform.openai.com".equals(uri.getHost()) || "openai.com".equals(uri.getHost());
                case "gemini" -> "ai.google.dev".equals(uri.getHost());
                default -> false;
            };
        } catch (IllegalArgumentException invalid) { return false; }
    }

    /** A base text-token quote, not an invoice or a quote for cache, region, or long-context surcharges. */
    public record Quote(BigDecimal inputUsdPerMillion, BigDecimal outputUsdPerMillion, String source, String checkedAt) { }
}
