package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ConversationHttpContractTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void everyAdapterPostsExactlyItsSerializedRequestBytes() throws Exception {
        AtomicBoolean malformed = new AtomicBoolean();
        List<byte[]> received = new CopyOnWriteArrayList<>();
        HttpServer server = startServer(malformed, received);
        try {
            ConversationRequest request = request();
            for (ConversationClient client : clients(server.getAddress().getPort())) {
                String serialized = client.serialize(request);
                ConversationTurn turn = client.converse(request);
                assertArrayEquals(serialized.getBytes(StandardCharsets.UTF_8), received.removeFirst());
                assertEquals(StopReason.TOOL_USE, turn.stopReason());
                assertEquals("echo", turn.toolCalls().getFirst().name());
            }
        } finally { server.stop(0); }
    }

    @Test void malformedVendorToolArgumentsBecomeRetryableProviderFailures() throws Exception {
        AtomicBoolean malformed = new AtomicBoolean(true);
        List<byte[]> received = new CopyOnWriteArrayList<>();
        HttpServer server = startServer(malformed, received);
        try {
            ConversationRequest request = request();
            for (ConversationClient client : clients(server.getAddress().getPort())) {
                ProviderException failure = assertThrows(ProviderException.class, () -> client.converse(request));
                assertTrue(failure.retryable());
                assertFalse(failure.getMessage().contains("private tool text"));
            }
        } finally { server.stop(0); }
    }

    private static ConversationRequest request() throws Exception {
        return new ConversationRequest("model", "private instructions", List.of(new UserText("private prompt")),
                List.of(new ToolSpec("echo", "Echo text", JSON.readTree("{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}}}"))),
                256, Duration.ofSeconds(15));
    }

    private static List<ConversationClient> clients(int port) {
        HttpClient http = HttpClient.newHttpClient();
        String base = "http://127.0.0.1:" + port;
        return List.of(new AnthropicMessagesProviderClient(http, URI.create(base + "/v1/messages"), "test-key"),
                new OpenAiResponsesProviderClient(http, URI.create(base + "/v1/responses"), "test-key"),
                new OpenAiChatCompatibleProviderClient(http, URI.create(base + "/v1/chat/completions"), ""),
                new GeminiGenerateContentProviderClient(http, URI.create(base + "/v1beta/"), "test-key"));
    }

    private static HttpServer startServer(AtomicBoolean malformed, List<byte[]> received) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            received.add(exchange.getRequestBody().readAllBytes());
            String path = exchange.getRequestURI().getPath();
            String response = responseFor(path, malformed.get());
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("content-type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        return server;
    }

    private static String responseFor(String path, boolean malformed) {
        if (path.endsWith("/messages")) return "{\"id\":\"anthropic\",\"stop_reason\":\"tool_use\",\"content\":[{\"type\":\"tool_use\",\"id\":\"a1\",\"name\":\"echo\",\"input\":" + (malformed ? "[]" : "{\"text\":\"private tool text\"}") + "}]}";
        if (path.endsWith("/responses")) return "{\"id\":\"openai\",\"status\":\"completed\",\"output\":[{\"type\":\"function_call\",\"call_id\":\"o1\",\"name\":\"echo\",\"arguments\":\"" + (malformed ? "[]" : "{\\\"text\\\":\\\"private tool text\\\"}") + "\"}]}";
        if (path.endsWith("/chat/completions")) return "{\"id\":\"local\",\"choices\":[{\"finish_reason\":\"tool_calls\",\"message\":{\"content\":null,\"tool_calls\":[{\"id\":\"l1\",\"function\":{\"name\":\"echo\",\"arguments\":\"" + (malformed ? "[]" : "{\\\"text\\\":\\\"private tool text\\\"}") + "\"}}]}}]}";
        if (path.endsWith(":generateContent")) return "{\"responseId\":\"gemini\",\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"role\":\"model\",\"parts\":[{\"functionCall\":{\"id\":\"g1\",\"name\":\"echo\",\"args\":" + (malformed ? "[]" : "{\"text\":\"private tool text\"}") + "}}]}}]}";
        throw new IllegalArgumentException("Unexpected provider test path");
    }
}
