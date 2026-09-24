package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalMcpContextClientTest {
    @TempDir Path worktree;

    @Test
    void invokesAnAllowlistedStdioServerAndReturnsTextContext() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toString();
        LocalMcpConfiguration configuration = new LocalMcpConfiguration(
                "repository-context", java,
                List.of("-cp", System.getProperty("java.class.path"), FixtureServer.class.getName()),
                "repository.context", "{\"scope\":\"task\"}", 1);

        String result = new LocalMcpContextClient(Set.of(java)).collect(List.of(configuration), worktree);

        assertEquals("\n\nRunner-local MCP context:\nrepository-context:\nverified local context", result);
    }

    @Test
    void refusesCommandsNotApprovedByTheRunnerOperator() {
        LocalMcpConfiguration configuration = new LocalMcpConfiguration(
                "blocked", "unapproved-command", List.of(), "context", "{}", 1);

        assertThrows(IllegalStateException.class,
                () -> new LocalMcpContextClient(Set.of()).collect(List.of(configuration), worktree));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    /** Minimal JSON-lines MCP peer used to prove the real stdio transport without network dependencies. */
    public static final class FixtureServer {
        private static final ObjectMapper JSON = new ObjectMapper();

        public static void main(String[] arguments) throws Exception {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            JsonNode initialize = JSON.readTree(reader.readLine());
            System.out.println("{\"jsonrpc\":\"2.0\",\"id\":" + initialize.path("id").asInt()
                    + ",\"result\":{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},\"serverInfo\":{\"name\":\"fixture\",\"version\":\"1\"}}}");
            reader.readLine();
            JsonNode call = JSON.readTree(reader.readLine());
            System.out.println("{\"jsonrpc\":\"2.0\",\"id\":" + call.path("id").asInt()
                    + ",\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"verified local context\"}]}}");
        }
    }
}
