package io.forgeloop.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Objects;

/** Sole authority boundary for invoking loop tools. */
public final class ToolGateway {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ToolRegistry registry;
    private final LoopJournal journal;
    private final List<ToolCallInterceptor> interceptors;
    private final ToolSleeper sleeper;

    public ToolGateway(ToolRegistry registry, LoopJournal journal, List<ToolCallInterceptor> interceptors) {
        this(registry, journal, interceptors, duration -> Thread.sleep(duration.toMillis()));
    }

    /** Keeps policy after-hooks last so caller hooks cannot undo redaction or credential filtering. */
    public ToolGateway withInterceptors(List<ToolCallInterceptor> policyInterceptors) {
        if (policyInterceptors == null) throw new IllegalArgumentException("Policy interceptors are required");
        List<ToolCallInterceptor> combined = new ArrayList<>(interceptors);
        combined.addAll(policyInterceptors);
        return new ToolGateway(registry, journal, combined, sleeper);
    }

    ToolGateway(ToolRegistry registry, LoopJournal journal, List<ToolCallInterceptor> interceptors, ToolSleeper sleeper) {
        if (registry == null || journal == null || interceptors == null || sleeper == null)
            throw new IllegalArgumentException("Tool gateway dependencies are required");
        this.registry = registry;
        this.journal = journal;
        this.interceptors = List.copyOf(interceptors);
        this.sleeper = sleeper;
    }

    /** Journals, authorizes, validates, intercepts, executes, and records exactly one call. */
    public ToolOutcome invoke(ToolCall call, ToolContext context) throws IOException, InterruptedException, LoopHarnessFailure {
        if (call == null || context == null) throw new IllegalArgumentException("Tool invocation is invalid");
        long startedNanos = System.nanoTime();
        Map<String, Object> requested = new LinkedHashMap<>();
        requested.put("turn", context.turn()); requested.put("step", context.step());
        requested.put("callId", call.id()); requested.put("tool", call.name()); requested.put("arguments", call.arguments());
        journal.append("TOOL_REQUESTED", requested);

        ToolOutcome outcome;
        String decisionName = "none";
        String decisionResult = "allow";
        String decision = "ALLOW";
        String redirectTool = null;
        HoldClass holdClass = null;
        String holdReason = null;
        String holdRule = null;
        List<String> rewrittenBy = new ArrayList<>();
        LoopTool tool = registry.find(call.name());
        boolean roleAllows = ToolGrants.forRole(context.role(), !context.gates().isEmpty()).contains(call.name());
        if (context.notExecutedReason() != null) {
            outcome = ToolOutcome.failed(FailureCategory.BUSINESS_RULE, "Not executed: " + context.notExecutedReason());
            decisionResult = "not-executed";
            decisionName = "loop-budget-or-state";
            decision = "DENY";
        } else if (!roleAllows || !context.declaredTools().contains(call.name())) {
            outcome = ToolOutcome.failed(FailureCategory.PERMISSION, "Tool " + call.name() + " is not available to this worker");
            decisionResult = "grant-denied";
            decisionName = "tool-grant";
            decision = "DENY";
        } else if (tool == null) {
            outcome = ToolOutcome.failed(FailureCategory.PERMISSION, "Tool " + call.name() + " is not registered");
            decisionResult = "registry-denied";
            decisionName = "tool-registry";
            decision = "DENY";
        } else {
            try {
                tool.validateArguments(call.arguments());
                ToolCallInterceptor.Decision intercepted = null;
                for (ToolCallInterceptor interceptor : interceptors) {
                    ToolCallInterceptor.Decision current;
                    try {
                        current = interceptor.before(call, context);
                    } catch (Exception ruleFailure) {
                        if (ruleFailure instanceof InterruptedException) Thread.currentThread().interrupt();
                        current = ToolCallInterceptor.Decision.hold(HoldClass.RULE_FAILED, "An enforcement rule failed closed.");
                    }
                    if (current == null) current = ToolCallInterceptor.Decision.hold(HoldClass.RULE_FAILED,
                            "An enforcement rule returned no decision.");
                    if (current.kind() != ToolCallInterceptor.Decision.Kind.ALLOW) {
                        intercepted = current;
                        decisionName = interceptor.name();
                        decisionResult = current.kind().name().toLowerCase(java.util.Locale.ROOT);
                        decision = current.kind().name();
                        redirectTool = current.toolName();
                        holdClass = current.holdClass();
                        holdReason = current.kind() == ToolCallInterceptor.Decision.Kind.HOLD ? current.message() : null;
                        holdRule = current.kind() == ToolCallInterceptor.Decision.Kind.HOLD ? interceptor.name() : null;
                        break;
                    }
                }
                if (intercepted == null) outcome = executeWithOneTransientRetry(tool, call, context);
                else {
                    String message = intercepted.kind() == ToolCallInterceptor.Decision.Kind.HOLD
                            ? "Not executed: this task is now held for a person (" + intercepted.holdClass().name() + ")."
                            : intercepted.message();
                    if (intercepted.kind() == ToolCallInterceptor.Decision.Kind.REDIRECT) {
                        message = "Suggested tool " + intercepted.toolName() + ": " + message;
                    }
                    outcome = intercepted.kind() == ToolCallInterceptor.Decision.Kind.HOLD
                            ? ToolOutcome.held(intercepted.holdClass(), decisionName, holdReason)
                            : ToolOutcome.failed(intercepted.category(), message);
                }
            } catch (LoopToolFailure expected) {
                outcome = ToolOutcome.failed(expected.category(), expected.getMessage());
            }
        }

        for (ToolCallInterceptor interceptor : interceptors) {
            ToolOutcome beforeAfter = outcome;
            ToolOutcome after;
            try {
                after = interceptor.after(call, beforeAfter, context);
            } catch (Exception ruleFailure) {
                if (ruleFailure instanceof InterruptedException) Thread.currentThread().interrupt();
                after = null;
            }
            if (after == null || after.status() != beforeAfter.status() || after.category() != beforeAfter.category()
                    || !Objects.equals(after.postImages(), beforeAfter.postImages())) {
                decision = "HOLD";
                decisionResult = "hold";
                decisionName = interceptor.name();
                holdClass = HoldClass.RULE_FAILED;
                holdReason = "An enforcement after-hook failed or changed immutable tool output.";
                holdRule = interceptor.name();
                outcome = ToolOutcome.held(holdClass, holdRule, holdReason);
            } else {
                if (!Objects.equals(after.content(), beforeAfter.content())) rewrittenBy.add(interceptor.name());
                outcome = after;
            }
        }
        Map<String, Object> outcomeMeta = new LinkedHashMap<>(outcome.meta());
        outcomeMeta.put("decision", decision);
        if (!"none".equals(decisionName)) outcomeMeta.put("interceptor", decisionName);
        if (redirectTool != null) outcomeMeta.put("redirectTool", redirectTool);
        if (holdClass != null) {
            outcomeMeta.put("holdClass", holdClass.name());
            outcomeMeta.put("holdRule", holdRule == null ? "" : holdRule);
            outcomeMeta.put("holdReason", holdReason == null ? "" : holdReason);
        }
        outcome = new ToolOutcome(outcome.status(), outcome.category(), outcome.content(), outcomeMeta, outcome.postImages());
        Map<String, Object> completed = new LinkedHashMap<>();
        completed.put("turn", context.turn()); completed.put("step", context.step());
        completed.put("callId", call.id()); completed.put("tool", call.name());
        completed.put("decision", decision); completed.put("decisionDetail", decisionResult);
        completed.put("interceptor", decisionName);
        if (redirectTool != null) completed.put("redirectTool", redirectTool);
        completed.put("content", outcome.content());
        completed.put("rewrittenBy", List.copyOf(rewrittenBy));
        if (holdClass != null) {
            completed.put("holdClass", holdClass.name());
            completed.put("holdRule", holdRule == null ? "" : holdRule);
            completed.put("holdReason", holdReason == null ? "" : holdReason);
        }
        completed.put("durationMs", Duration.ofNanos(Math.max(0, System.nanoTime() - startedNanos)).toMillis());
        completed.put("outcome", JSON.valueToTree(outcome));
        journal.append("TOOL_COMPLETED", completed);
        return outcome;
    }

    private ToolOutcome executeWithOneTransientRetry(LoopTool tool, ToolCall call, ToolContext context)
            throws IOException, InterruptedException, LoopHarnessFailure {
        ToolOutcome outcome = invokeTool(tool, call, context);
        if (outcome.status() != ToolStatus.FAILED || outcome.category() != FailureCategory.TRANSIENT) return outcome;
        sleeper.sleep(Duration.ofSeconds(2));
        return invokeTool(tool, call, context);
    }

    private static ToolOutcome invokeTool(LoopTool tool, ToolCall call, ToolContext context)
            throws IOException, InterruptedException, LoopHarnessFailure {
        try { return tool.execute(call, context); }
        catch (LoopToolFailure expected) { return ToolOutcome.failed(expected.category(), expected.getMessage()); }
    }
}
