package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RunnerClientAvailableTasksTest {
    @Test
    void parsesAndRequestsOptionalRedPrerequisiteForAvailableTasks() throws Exception {
        AtomicReference<String> query = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/graphql", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            query.set(new ObjectMapper().readTree(request).path("query").asText());
            byte[] response = response().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            RunnerClient client = new RunnerClient(HttpClient.newHttpClient(),
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
            List<RunnerTask> tasks = client.availableTasks(new RunnerIdentity("runner-1", "credential"));

            assertTrue(query.get().contains("redPrerequisite{testTaskId targetSha evidenceDigest}"));
            assertTrue(query.get().contains("runRecord"));
            assertTrue(query.get().contains("budget{maxToolCalls maxTokens maxWallSeconds maxConversationBytes}"));
            assertTrue(query.get().contains("enforcement{protectedPaths allowWorkflowChanges finishGate}"));
            assertEquals(20, tasks.getFirst().agentLoop().budget().maxToolCalls());
            assertEquals(1_000_000, tasks.getFirst().agentLoop().budgetMicros());
            assertNull(tasks.get(1).agentLoop());
            assertEquals(new RunnerRedPrerequisite("test-writer", "a".repeat(40), "c".repeat(64)),
                    tasks.getFirst().redPrerequisite());
            assertEquals(new RunnerLoopEnforcement(List.of("src/generated/**"), true, "verify"),
                    tasks.getFirst().loopEnforcement());
            assertTrue(tasks.getFirst().runRecord());
            assertEquals(false, tasks.get(1).runRecord());
            assertNull(tasks.get(1).redPrerequisite());
            assertNull(tasks.get(1).loopEnforcement());
            assertNull(tasks.get(2).loopEnforcement().protectedPaths());
            assertEquals(HoldClass.RULE_INPUT_MISSING, EnforcementPreflight.check(
                    EnforcementDescriptor.of(tasks.get(2), List.of()), "a".repeat(40)).orElseThrow().holdClass());
            assertEquals("", tasks.get(3).loopEnforcement().finishGate());
            assertEquals(HoldClass.RULE_INPUT_MISSING, EnforcementPreflight.check(
                    EnforcementDescriptor.of(tasks.get(3), List.of()), "a".repeat(40)).orElseThrow().holdClass());
        } finally {
            server.stop(0);
        }
    }

    @Test void rejectsMalformedNonNullLoopBudgetsRatherThanFallingBackToSingleCall() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/graphql", exchange -> {
            byte[] response = response().replace("\"maxToolCalls\":20", "\"maxToolCalls\":\"20\"").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            RunnerClient client = new RunnerClient(HttpClient.newHttpClient(), URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
            assertThrows(IllegalArgumentException.class, () -> client.availableTasks(new RunnerIdentity("runner", "credential")));
        } finally { server.stop(0); }
    }

    private static String response() {
        return "{\"data\":{\"availableRunnerTasks\":["
                + task("implementation", "{\"testTaskId\":\"test-writer\",\"targetSha\":\"" + "a".repeat(40)
                        + "\",\"evidenceDigest\":\"" + "c".repeat(64) + "\"}",
                        "{\"enforcement\":{\"protectedPaths\":[\"src/generated/**\"],\"allowWorkflowChanges\":true,\"finishGate\":\"verify\"}}")
                + "," + task("ordinary", "null", "null")
                + "," + task("malformed-policy", "null", "{\"enforcement\":{\"protectedPaths\":\"invalid\",\"allowWorkflowChanges\":\"false\",\"finishGate\":42}}")
                + "," + task("missing-finish-gate", "null", "{\"enforcement\":{\"protectedPaths\":[],\"allowWorkflowChanges\":false}}") + "]}}";
    }

    private static String task(String id, String prerequisite, String agentLoop) {
        if (!"null".equals(agentLoop)) agentLoop = "{\"budget\":{\"maxToolCalls\":20,\"maxTokens\":100000,\"maxWallSeconds\":600,\"maxConversationBytes\":65536},\"gates\":[]," + agentLoop.substring(1);
        return "{\"id\":\"" + id + "\",\"role\":\"IMPLEMENTATION\",\"title\":\"Implement\","
                + "\"repository\":\"org/repo\",\"baseBranch\":\"main\",\"executionBaseRef\":\"main\","
                + "\"sourceRef\":\"issue-1\",\"specification\":\"spec\",\"requiredCapability\":\"provider\","
                + "\"budgetUsd\":1,\"budgetMicros\":1000000,\"spentCostMicros\":0,\"ownedPaths\":[\"src\"],\"dependencyChangeShas\":[],"
                + "\"acceptanceCriteria\":[],\"mcpConfigurations\":[],\"runRecord\":" + ("implementation".equals(id) ? "true" : "false") + ",\"writeBoundary\":\"NO_TESTS\","
                + "\"testPathGlobs\":[\"**/*Test.java\"],\"expectedTests\":[],\"expectedTestsOverflow\":false,"
                + "\"redPrerequisite\":" + prerequisite + ",\"agentLoop\":" + agentLoop + "}";
    }
}
