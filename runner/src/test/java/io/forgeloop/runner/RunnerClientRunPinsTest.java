package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RunnerClientRunPinsTest {
    @Test void acknowledgesWithRunnerBuildPinsAndSubmitsAnsweredModelAndGatePins() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/graphql", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"data\":{}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            RunnerClient client = new RunnerClient(HttpClient.newHttpClient(), URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
            RunnerIdentity identity = new RunnerIdentity("runner-1", "credential");
            RunnerLease lease = new RunnerLease("lease-1", "nonce");

            client.acknowledgeLease(identity, lease.leaseId(), lease.nonce());
            var acknowledgement = new ObjectMapper().readTree(body.get());
            var build = RunnerBuild.current();
            assertTrue(acknowledgement.path("query").asText().contains("runnerJarSha256"));
            assertEquals(build.revision(), acknowledgement.path("variables").path("runnerRevision").asText());
            assertEquals(build.jarSha256(), acknowledgement.path("variables").path("runnerJarSha256").asText());

            client.recordProviderAttempt(identity, lease, new ProviderAttemptReport("openai", "alias", "a".repeat(64),
                    10, 5, 1, 20, true, "SUCCEEDED", false, "COMPLETED", "gpt-actual"));
            var attempt = new ObjectMapper().readTree(body.get());
            assertEquals("gpt-actual", attempt.path("variables").path("input").path("answeredModel").asText());

            Instant start = Instant.EPOCH;
            VerificationResult result = new VerificationResult(0, false, "passed", start, start.plusSeconds(1), true);
            VerificationEvidenceReport evidence = new VerificationEvidenceReport("CONTAINER", "unit", "node@sha256:" + "b".repeat(64),
                    List.of("npm", "test"), result, "artifact://evidence", null, null, "c".repeat(40), "sha256:" + "d".repeat(64));
            client.recordEvidence(identity, lease, evidence);
            var report = new ObjectMapper().readTree(body.get()).path("variables").path("input");
            assertEquals("c".repeat(40), report.path("targetSha").asText());
            assertEquals("sha256:" + "d".repeat(64), report.path("imageId").asText());
            assertTrue(report.path("outputTruncated").asBoolean());
        } finally {
            server.stop(0);
        }
    }
}
