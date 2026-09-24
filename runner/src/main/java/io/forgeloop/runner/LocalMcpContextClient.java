package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Invokes explicitly configured context tools over runner-local MCP stdio. */
public final class LocalMcpContextClient {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_CONTEXT_BYTES = 32 * 1024;
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(15);
    private final Set<String> allowedCommands;

    public LocalMcpContextClient() {
        this(parseAllowedCommands(System.getenv("FORGELOOP_MCP_ALLOWED_COMMANDS")));
    }

    LocalMcpContextClient(Set<String> allowedCommands) {
        this.allowedCommands = Set.copyOf(allowedCommands);
    }

    public String collect(List<LocalMcpConfiguration> configurations, Path worktree) throws Exception {
        if (configurations.isEmpty()) return "";
        List<String> results = new ArrayList<>();
        for (LocalMcpConfiguration configuration : configurations) results.add(invoke(configuration, worktree));
        return "\n\nRunner-local MCP context:\n" + String.join("\n\n", results);
    }

    String invoke(LocalMcpConfiguration configuration, Path worktree) throws Exception {
        if (!allowedCommands.contains(configuration.command())) {
            throw new IllegalStateException("Local MCP command is not runner-allowlisted: " + configuration.name());
        }
        List<String> command = new ArrayList<>();
        command.add(configuration.command());
        configuration.arguments().stream()
                .map(argument -> argument.replace("${WORKTREE}", worktree.toString()))
                .forEach(command::add);
        Process process = new ProcessBuilder(command).directory(worktree.toFile())
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            request(writer, 1, "initialize", Map.of("protocolVersion", "2025-06-18", "capabilities", Map.of(),
                    "clientInfo", Map.of("name", "forgeloop-runner", "version", "0.1.0")));
            readResponse(reader, 1);
            notification(writer, "notifications/initialized");
            JsonNode arguments = JSON.readTree(configuration.toolArguments());
            request(writer, 2, "tools/call", Map.of("name", configuration.contextTool(), "arguments", arguments));
            JsonNode response = readResponse(reader, 2);
            if (response.path("result").path("isError").asBoolean(false)) {
                throw new IllegalStateException("Local MCP context tool reported an error: " + configuration.name());
            }
            String context = extractText(response.path("result").path("content"));
            if (context.getBytes(StandardCharsets.UTF_8).length > MAX_CONTEXT_BYTES) {
                throw new IllegalStateException("Local MCP context exceeded the 32 KiB limit: " + configuration.name());
            }
            return configuration.name() + ":\n" + context;
        } finally {
            process.destroy();
            if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
        }
    }

    private static String extractText(JsonNode content) {
        List<String> values = new ArrayList<>();
        if (content.isArray()) for (JsonNode item : content) {
            if ("text".equals(item.path("type").asText()) && item.path("text").isTextual()) values.add(item.path("text").asText());
        }
        if (values.isEmpty()) throw new IllegalStateException("Local MCP context tool returned no text content");
        return String.join("\n", values);
    }

    private static void request(BufferedWriter writer, int id, String method, Object parameters) throws Exception {
        writer.write(JSON.writeValueAsString(Map.of("jsonrpc", "2.0", "id", id, "method", method, "params", parameters)));
        writer.newLine(); writer.flush();
    }

    private static void notification(BufferedWriter writer, String method) throws Exception {
        writer.write(JSON.writeValueAsString(Map.of("jsonrpc", "2.0", "method", method)));
        writer.newLine(); writer.flush();
    }

    private static JsonNode readResponse(BufferedReader reader, int id) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> line = executor.submit(reader::readLine);
            String value = line.get(RESPONSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (value == null) throw new IllegalStateException("Local MCP process closed unexpectedly");
            JsonNode response = JSON.readTree(value);
            if (response.path("id").asInt(-1) != id || response.has("error")) {
                throw new IllegalStateException("Local MCP response failed validation");
            }
            return response;
        } finally { executor.shutdownNow(); }
    }

    private static Set<String> parseAllowedCommands(String configured) {
        if (configured == null || configured.isBlank()) return Set.of();
        Set<String> commands = new HashSet<>();
        Arrays.stream(configured.split(",")).map(String::trim).filter(value -> !value.isEmpty()).forEach(commands::add);
        return commands;
    }
}
