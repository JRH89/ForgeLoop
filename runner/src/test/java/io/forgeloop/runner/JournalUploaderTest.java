package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JournalUploaderTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temporary;

    @Test void uploadsOnlyCurrentLeaseAsRedactedGzipAndKeepsSourceApiKeyAssignments() throws Exception {
        List<byte[]> uploads = new CopyOnWriteArrayList<>();
        AtomicInteger uploadCount = new AtomicInteger();
        AtomicInteger eventCount = new AtomicInteger();
        HttpServer server = server(uploads, uploadCount, eventCount, new AtomicBoolean(false));
        try {
            StepJournal prior = new StepJournal(temporary, "task-1", "old-lease", Clock.systemUTC());
            prior.append("WORKER_STARTED", Map.of("taskId", "task-1"));
            prior.append("CALL_COMPLETED", Map.of("responseBody", "old lease must not upload"));
            StepJournal current = new StepJournal(temporary, "task-1", "current-lease", Clock.systemUTC());
            current.append("WORKER_STARTED", Map.of("taskId", "task-1"));
            current.append("CALL_REQUESTED", Map.of("input", "apiKey: source-code-value"));
            current.append("CALL_COMPLETED", Map.of("responseBody", "ghp_abcdefghijklmnopqrstuvwxyz123456"));
            current.append("WORKER_ENDED", Map.of("category", "COMPLETED"));
            RunnerClient client = new RunnerClient(HttpClient.newHttpClient(), baseUri(server));
            RunnerTask task = new RunnerTask("task-1", "IMPLEMENTATION", "Task", "org/repo", "main", "issue-1", "spec", "provider", true);
            RunnerIdentity identity = new RunnerIdentity("runner-1", "credential");
            RunnerLease lease = new RunnerLease("current-lease", "nonce");

            new JournalUploader(1024).upload(task, identity, lease, client, current, reporter(client, identity, lease));

            assertEquals(1, uploadCount.get());
            assertEquals(1, uploads.size());
            List<String> lines = gunzipLines(uploads.getFirst());
            assertEquals(4, lines.size());
            assertFalse(String.join("\n", lines).contains("old lease must not upload"));
            JsonNode redacted = JSON.readTree(lines.get(2));
            assertTrue(redacted.path("redacted").asBoolean());
            assertEquals("current-lease", redacted.path("leaseId").asText());
            assertEquals("CALL_COMPLETED", redacted.path("type").asText());
            assertTrue(redacted.path("line").asText().contains("[REDACTED]"));
            assertFalse(redacted.path("line").asText().contains("ghp_"));
            assertEquals("apiKey: source-code-value", JSON.readTree(lines.get(1)).path("input").asText());
            assertEquals(0, eventCount.get());
            try (var paths = Files.walk(temporary.resolve("journals"))) {
                assertEquals(2, paths.filter(Files::isRegularFile).count() + 1,
                        "journal uploader must not create separate upload-state files");
            }
        } finally { server.stop(0); }
    }

    @Test void segmentsWholeLinesWithinCompressedLimitAndOmitsOneOversizedLine() throws Exception {
        byte[] random = new byte[2_000];
        new java.security.SecureRandom().nextBytes(random);
        String large = Base64.getEncoder().encodeToString(random);
        List<String> lines = List.of(
                "{\"seq\":1,\"leaseId\":\"lease\",\"type\":\"WORKER_STARTED\"}",
                "{\"seq\":2,\"leaseId\":\"lease\",\"type\":\"CALL_COMPLETED\",\"payload\":\"" + large + "\"}",
                "{\"seq\":3,\"leaseId\":\"lease\",\"type\":\"WORKER_ENDED\"}");

        List<JournalUploader.JournalSegment> segments = JournalUploader.segments(lines, "lease", 256);

        assertEquals(3, segments.stream().mapToLong(segment -> segment.lastSequence() - segment.firstSequence() + 1).sum());
        for (JournalUploader.JournalSegment segment : segments) {
            assertTrue(segment.compressed().length <= 256);
            List<String> uploadedLines = gunzipLines(segment.compressed());
            for (String line : uploadedLines) assertTrue(JSON.readTree(line).isObject());
        }
        String all = segments.stream().flatMap(segment -> {
            try { return gunzipLines(segment.compressed()).stream(); }
            catch (Exception failure) { throw new RuntimeException(failure); }
        }).reduce("", (left, right) -> left + right + "\n");
        assertTrue(all.contains("\"omitted\":true"));
        assertFalse(all.contains(large));
    }

    @Test void retriesFailedUploadOnceThenReportsOnlyFailureMetadata() throws Exception {
        List<byte[]> uploads = new CopyOnWriteArrayList<>();
        AtomicInteger uploadCount = new AtomicInteger();
        AtomicInteger eventCount = new AtomicInteger();
        HttpServer server = server(uploads, uploadCount, eventCount, new AtomicBoolean(true));
        try {
            StepJournal journal = new StepJournal(temporary, "task-fail", "lease-fail", Clock.systemUTC());
            journal.append("WORKER_STARTED", Map.of("taskId", "task-fail"));
            RunnerClient client = new RunnerClient(HttpClient.newHttpClient(), baseUri(server));
            RunnerIdentity identity = new RunnerIdentity("runner-1", "credential");
            RunnerLease lease = new RunnerLease("lease-fail", "nonce");
            RunnerTask task = new RunnerTask("task-fail", "IMPLEMENTATION", "Task", "org/repo", "main", "issue-1", "spec", "provider", true);

            new JournalUploader(1024).upload(task, identity, lease, client, journal, reporter(client, identity, lease));

            assertEquals(2, uploadCount.get());
            assertEquals(1, eventCount.get());
        } finally { server.stop(0); }
    }

    @Test void switchOffUploadsNothing() throws Exception {
        AtomicInteger uploads = new AtomicInteger();
        HttpServer server = server(new ArrayList<>(), uploads, new AtomicInteger(), new AtomicBoolean(false));
        try {
            StepJournal journal = new StepJournal(temporary, "task-off", "lease-off", Clock.systemUTC());
            journal.append("WORKER_STARTED", Map.of("taskId", "task-off"));
            RunnerClient client = new RunnerClient(HttpClient.newHttpClient(), baseUri(server));
            RunnerTask task = new RunnerTask("task-off", "IMPLEMENTATION", "Task", "org/repo", "main", "issue-1", "spec", "provider", false);
            new JournalUploader(1024).upload(task, new RunnerIdentity("runner", "credential"),
                    new RunnerLease("lease-off", "nonce"), client, journal,
                    reporter(client, new RunnerIdentity("runner", "credential"), new RunnerLease("lease-off", "nonce")));
            assertEquals(0, uploads.get());
        } finally { server.stop(0); }
    }

    private static HttpServer server(List<byte[]> uploads, AtomicInteger uploadCount, AtomicInteger eventCount,
                                     AtomicBoolean failUpload) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/runner/artifacts", exchange -> {
            uploadCount.incrementAndGet();
            byte[] content = exchange.getRequestBody().readAllBytes();
            if (failUpload.get()) {
                exchange.sendResponseHeaders(503, -1);
                exchange.close();
                return;
            }
            uploads.add(content);
            String digest = exchange.getRequestHeaders().getFirst("X-ForgeLoop-Artifact-Sha256");
            String response = "{\"reference\":\"artifact://org/run/task/lease/journal\",\"sha256\":\"" + digest
                    + "\",\"sizeBytes\":" + content.length + ",\"retainUntil\":\"2026-10-23T00:00:00Z\"}";
            send(exchange, 200, response);
        });
        server.createContext("/api/runner/events", exchange -> {
            eventCount.incrementAndGet();
            send(exchange, 200, "{}");
        });
        server.start();
        return server;
    }

    private static void send(com.sun.net.httpserver.HttpExchange exchange, int status, String response) throws java.io.IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }

    private static RunnerEventReporter reporter(RunnerClient client, RunnerIdentity identity, RunnerLease lease) {
        return new RunnerEventReporter(client, identity, lease);
    }

    private static URI baseUri(HttpServer server) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private static List<String> gunzipLines(byte[] content) throws Exception {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(content))) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
    }
}
