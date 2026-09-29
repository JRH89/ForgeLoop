package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToolGatewayTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temporaryDirectory;

    @Test
    void requiresRoleGrantBeforeRunningAndJournalsDenials() throws Exception {
        EchoTool tool = new EchoTool();
        ToolGateway gateway = gateway(tool, List.of(), ignored -> { });
        ToolOutcome result = gateway.invoke(call("read_file", "{}"), context(Set.of()));
        assertEquals(FailureCategory.PERMISSION, result.category());
        assertEquals(0, tool.calls);
        assertEquals(List.of("TOOL_REQUESTED", "TOOL_COMPLETED"), journal().records().stream().map(row -> row.path("type").asText()).toList());
    }

    @Test
    void validatesArgumentsBeforeInterceptorsOrExecution() throws Exception {
        EchoTool tool = new EchoTool();
        List<String> events = new ArrayList<>();
        ToolCallInterceptor interceptor = interceptor("audit", events, ToolCallInterceptor.Decision.allow());
        ToolOutcome result = gateway(tool, List.of(interceptor), ignored -> { }).invoke(call("read_file", "{\"extra\":true}"), context(Set.of("read_file")));
        assertEquals(FailureCategory.VALIDATION, result.category());
        assertEquals(0, tool.calls);
        assertEquals(List.of("after:audit"), events);
    }

    @Test
    void firstDenyStopsBeforeChainButEveryAfterHookSeesTheOutcome() throws Exception {
        EchoTool tool = new EchoTool();
        List<String> events = new ArrayList<>();
        var deny = interceptor("guard", events, ToolCallInterceptor.Decision.deny(FailureCategory.PERMISSION, "denied"));
        var later = interceptor("later", events, ToolCallInterceptor.Decision.allow());
        ToolOutcome result = gateway(tool, List.of(deny, later), ignored -> { }).invoke(call("read_file", "{\"value\":\"x\"}"), context(Set.of("read_file")));
        assertEquals(FailureCategory.PERMISSION, result.category());
        assertEquals(0, tool.calls);
        assertEquals(List.of("before:guard", "after:guard", "after:later"), events);
    }

    @Test
    void retriesTransientOutcomeExactlyOnceAndThenReturnsSuccess() throws Exception {
        EchoTool tool = new EchoTool(); tool.failures = 1;
        List<Duration> waits = new ArrayList<>();
        ToolOutcome result = gateway(tool, List.of(), waits::add).invoke(call("read_file", "{\"value\":\"ok\"}"), context(Set.of("read_file")));
        assertEquals(ToolStatus.OK, result.status());
        assertEquals(2, tool.calls);
        assertEquals(List.of(Duration.ofSeconds(2)), waits);
    }

    @Test
    void unexpectedIoRemainsAHarnessFailureInsteadOfAModelToolResult() throws Exception {
        EchoTool tool = new EchoTool(); tool.ioFailure = true;
        assertThrows(IOException.class, () -> gateway(tool, List.of(), ignored -> { })
                .invoke(call("read_file", "{\"value\":\"x\"}"), context(Set.of("read_file"))));
    }

    private ToolGateway gateway(EchoTool tool, List<ToolCallInterceptor> interceptors, ToolSleeper sleeper) throws Exception {
        return new ToolGateway(new ToolRegistry(List.of(tool)), journal(), interceptors, sleeper);
    }

    private StepJournal journal() throws IOException { return new StepJournal(temporaryDirectory, "task", "lease", Clock.systemUTC()); }

    private ToolContext context(Set<String> grants) throws IOException {
        Path worktree = temporaryDirectory.resolve("repo"); Files.createDirectories(worktree.resolve(".git"));
        return new ToolContext("task", "lease", "IMPLEMENTATION", worktree, grants, List.of("src/"), List.of(), 1, 1,
                60_000, new LoopCounters(0, 0, 0, 0, 0, 0), Map.of(), null);
    }

    private static ToolCall call(String name, String arguments) throws IOException { return new ToolCall("call-1", name, JSON.readTree(arguments)); }

    private static ToolCallInterceptor interceptor(String name, List<String> events, ToolCallInterceptor.Decision decision) {
        return new ToolCallInterceptor() {
            @Override public String name() { return name; }
            @Override public Decision before(ToolCall call, ToolContext context) { events.add("before:" + name); return decision; }
            @Override public ToolOutcome after(ToolCall call, ToolOutcome outcome, ToolContext context) { events.add("after:" + name); return outcome; }
        };
    }

    private static final class EchoTool implements LoopTool {
        private final ToolSpec spec = ToolSchemas.spec("read_file", "Test echo tool.", "{\"type\":\"object\",\"properties\":{\"value\":{\"type\":\"string\"}},\"required\":[\"value\"],\"additionalProperties\":false}");
        private int calls;
        private int failures;
        private boolean ioFailure;
        @Override public ToolSpec spec() { return spec; }
        @Override public void validateArguments(JsonNode args) throws LoopToolFailure {
            ToolArguments.fields(args, Set.of("value"), "value"); ToolArguments.string(args, "value", false);
        }
        @Override public ToolOutcome execute(ToolCall call, ToolContext context) throws IOException, LoopToolFailure {
            calls++;
            if (ioFailure) throw new IOException("disk failure");
            if (failures-- > 0) return ToolOutcome.failed(FailureCategory.TRANSIENT, "try again");
            return ToolOutcome.ok(call.arguments().path("value").asText());
        }
    }
}
