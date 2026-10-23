package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Exercises authenticated wire requests and fail-closed lifecycle acknowledgements. */
class RunnerClientLoopLifecycleTest {
    @Test void sendsLeaseBoundRenewHoldAndCategorizedFailureMutations() throws Exception {
        List<JsonNode> requests = new ArrayList<>();
        HttpServer server = server(requests, false);
        server.start();
        try {
            RunnerClient client = client(server);
            RunnerIdentity identity = new RunnerIdentity("runner", "credential");
            RunnerLease lease = new RunnerLease("lease", "nonce");
            client.renewLease(identity, lease);
            client.holdLease(identity, lease, "LOOP_BUDGET_EXHAUSTED", "Agent loop stopped");
            client.failLease(identity, lease, "LOOP_HARNESS_FAILURE");
            assertEquals(3, requests.size());
            for (JsonNode request : requests) {
                assertEquals("lease", request.path("variables").path("leaseId").asText());
                assertEquals("runner", request.path("variables").path("runnerId").asText());
                assertEquals("credential", request.path("variables").path("credential").asText());
                assertEquals("nonce", request.path("variables").path("nonce").asText());
            }
            assertEquals("LOOP_BUDGET_EXHAUSTED", requests.get(1).path("variables").path("reason").asText());
            assertEquals("LOOP_HARNESS_FAILURE", requests.get(2).path("variables").path("category").asText());
            assertFalse(requests.get(2).path("variables").path("passed").asBoolean(true));
        } finally { server.stop(0); }
    }

    @Test void rejectsAnAcknowledgementForAnotherLease() throws Exception {
        HttpServer server = server(new ArrayList<>(), true);
        server.start();
        try {
            assertThrows(ControlPlaneFailure.class, () -> client(server).renewLease(
                    new RunnerIdentity("runner", "credential"), new RunnerLease("lease", "nonce")));
        } finally { server.stop(0); }
    }

    private static RunnerClient client(HttpServer server) {
        return new RunnerClient(HttpClient.newHttpClient(), URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
    }
    private static HttpServer server(List<JsonNode> requests, boolean mismatch) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/graphql", exchange -> {
            JsonNode request = new ObjectMapper().readTree(exchange.getRequestBody());
            requests.add(request);
            String query = request.path("query").asText();
            String operation = query.contains("renewTaskLease") ? "renewTaskLease" : query.contains("holdTaskLease") ? "holdTaskLease" : "completeTaskLease";
            byte[] response = ("{\"data\":{\"" + operation + "\":{\"id\":\"" + (mismatch ? "other" : "lease") + "\",\"completed\":true}}}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        return server;
    }
}
