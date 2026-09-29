package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/** Executes only a task-declared gate through an injected runner-local executor; it records no official evidence. */
final class RunGateTool implements LoopTool {
    private static final ToolSpec SPEC = ToolSchemas.spec("run_gate", "Run one declared gate as an advisory check; this does not submit official evidence.",
            "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}},\"required\":[\"name\"],\"additionalProperties\":false}");
    private final RunGateExecutor executor;
    RunGateTool(RunGateExecutor executor) { this.executor = executor; }
    @Override public ToolSpec spec() { return SPEC; }
    @Override public void validateArguments(JsonNode args) throws LoopToolFailure { ToolArguments.fields(args, Set.of("name"), "name"); ToolArguments.string(args, "name", false); }
    @Override public ToolOutcome execute(ToolCall call, ToolContext context) throws LoopToolFailure, IOException, InterruptedException {
        String name = ToolArguments.string(call.arguments(), "name", false);
        LoopGate gate = context.gates().stream().filter(candidate -> candidate.name().equals(name)).findFirst()
                .orElseThrow(() -> new LoopToolFailure(FailureCategory.VALIDATION, "No gate named " + name + " is available"));
        if (context.remainingWallMillis() < 30_000) throw new LoopToolFailure(FailureCategory.BUSINESS_RULE, "At least 30 seconds of loop time must remain before running a gate");
        if (executor == null) throw new LoopToolFailure(FailureCategory.TRANSIENT, "Gate execution is unavailable on this runner");
        VerificationResult result;
        Duration timeout = Duration.ofMillis(Math.min(context.remainingWallMillis(), Duration.ofSeconds(gate.timeoutSeconds()).toMillis()));
        try { result = executor.execute(gate, context.worktree(), timeout); }
        catch (IOException unavailable) { throw new LoopToolFailure(FailureCategory.TRANSIENT, "Gate execution could not start"); }
        String output = result.output() == null ? "" : result.output();
        String content = "exitCode=" + result.exitCode() + ", timedOut=" + result.timedOut() + "\n" + output;
        return ToolOutcome.ok(content, Map.of("gate", gate.name(), "exitCode", result.exitCode(), "timedOut", result.timedOut(),
                "outputSha256", Hashing.sha256(output), "startedAt", result.startedAt().toString(), "finishedAt", result.finishedAt().toString()), java.util.List.of());
    }
}
