package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RunnerClientSpendReservationTest {
    @Test
    void sendsAuthenticatedReservationAndParsesTheControlPlaneGrant() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = server(body, "{\"data\":{\"reserveTaskSpend\":{\"granted\":true,\"reservedMicros\":500}}}");
        server.start();
        try {
            RunnerClient client = new RunnerClient(HttpClient.newHttpClient(), baseUri(server));

            SpendReservationGrant grant = client.reserveSpend(new RunnerIdentity("runner-1", "credential-secret"),
                    new RunnerLease("lease-2", "nonce-secret"), 500);

            assertEquals(new SpendReservationGrant(true, 500), grant);
            var request = new ObjectMapper().readTree(body.get());
            assertTrue(request.path("query").asText().contains("reserveTaskSpend"));
            assertEquals("runner-1", request.path("variables").path("runnerId").asText());
            assertEquals("credential-secret", request.path("variables").path("credential").asText());
            assertEquals("nonce-secret", request.path("variables").path("nonce").asText());
            assertEquals(500, request.path("variables").path("micros").asLong());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void refusesMalformedOrMismatchedGrantResponses() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = server(body, "{\"data\":{\"reserveTaskSpend\":{\"granted\":true,\"reservedMicros\":499}}}");
        server.start();
        try {
            RunnerClient client = new RunnerClient(HttpClient.newHttpClient(), baseUri(server));

            ControlPlaneFailure failure = assertThrows(ControlPlaneFailure.class,
                    () -> client.reserveSpend(new RunnerIdentity("runner-1", "credential"),
                            new RunnerLease("lease-2", "nonce"), 500));

            assertTrue(failure.retryable());
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer server(AtomicReference<String> body, String responseText) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/graphql", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = responseText.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        return server;
    }

    private static URI baseUri(HttpServer server) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }
}
