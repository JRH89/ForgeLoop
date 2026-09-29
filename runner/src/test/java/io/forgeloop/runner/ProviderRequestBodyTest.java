package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ProviderRequestBodyTest {
    @Test void extractedBodiesMatchTheExactBytesSentByEveryAdapterWithAndWithoutSchema() throws Exception {
        AtomicReference<byte[]> received = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            received.set(exchange.getRequestBody().readAllBytes());
            String response = responseFor(exchange.getRequestURI().getPath());
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            HttpClient http = HttpClient.newHttpClient();
            ProviderClientFactory factory = new ProviderClientFactory();
            for (String provider : List.of("anthropic", "openai", "gemini", "local")) {
                for (boolean withSchema : List.of(false, true)) {
                    ProviderRequest request = new ProviderRequest("model", "system rules", "user input", 1024,
                            withSchema ? StructuredOutputSchemas.plan() : null);
                    client(provider, port, http).execute(request);
                    assertArrayEquals(factory.requestBody(provider, request).getBytes(StandardCharsets.UTF_8), received.get(),
                            provider + " request serialization changed");
                }
            }
        } finally { server.stop(0); }
    }

    private static ProviderClient client(String provider, int port, HttpClient http) {
        String base = "http://127.0.0.1:" + port;
        return switch (provider) {
            case "anthropic" -> new AnthropicMessagesProviderClient(http, URI.create(base + "/v1/messages"), "test-key");
            case "openai" -> new OpenAiResponsesProviderClient(http, URI.create(base + "/v1/responses"), "test-key");
            case "gemini" -> new GeminiGenerateContentProviderClient(http, URI.create(base + "/v1beta/"), "test-key");
            case "local" -> new OpenAiChatCompatibleProviderClient(http, URI.create(base + "/v1/chat/completions"), "");
            default -> throw new IllegalArgumentException("Unsupported provider");
        };
    }

    private static String responseFor(String path) {
        if (path.endsWith("/messages")) return "{\"id\":\"a\",\"model\":\"model\",\"usage\":{\"input_tokens\":1,\"output_tokens\":1},\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}";
        if (path.endsWith("/responses")) return "{\"id\":\"o\",\"model\":\"model\",\"usage\":{\"input_tokens\":1,\"output_tokens\":1},\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":\"ok\"}]}]}";
        if (path.endsWith("/chat/completions")) return "{\"id\":\"l\",\"model\":\"model\",\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1},\"choices\":[{\"message\":{\"content\":\"ok\"}}]}";
        if (path.endsWith(":generateContent")) return "{\"responseId\":\"g\",\"modelVersion\":\"model\",\"usageMetadata\":{\"promptTokenCount\":1,\"candidatesTokenCount\":1},\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ok\"}]}}]}";
        throw new IllegalArgumentException("Unexpected provider path");
    }
}
