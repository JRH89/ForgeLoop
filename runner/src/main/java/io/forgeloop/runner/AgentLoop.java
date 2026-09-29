package io.forgeloop.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Deterministic plan/act loop. Tool execution can happen only through the injected gateway. */
public final class AgentLoop {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int NORMAL_OUTPUT_TOKENS = 16_384;
    private static final int TRUNCATION_RETRY_TOKENS = 32_768;
    private static final Duration NORMAL_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration TRUNCATION_TIMEOUT = Duration.ofMinutes(10);
    private final ConversationExecutionService conversations;
    private final ProviderCostCalculator costs;
    private final RepositoryContextBuilder contexts;
    private final ResultShaper shaper;
    private final SpendGovernor spendGovernor;

    public AgentLoop() { this(new ConversationExecutionService(), new ProviderCostCalculator(), new RepositoryContextBuilder(), new ResultShaper(), new SpendGovernor()); }

    AgentLoop(ConversationExecutionService conversations, ProviderCostCalculator costs,
              RepositoryContextBuilder contexts, ResultShaper shaper) {
        this(conversations, costs, contexts, shaper, new SpendGovernor());
    }

    AgentLoop(ConversationExecutionService conversations, ProviderCostCalculator costs,
              RepositoryContextBuilder contexts, ResultShaper shaper, SpendGovernor spendGovernor) {
        this.conversations = conversations; this.costs = costs; this.contexts = contexts; this.shaper = shaper;
        this.spendGovernor = spendGovernor;
    }

    public LoopResult run(LoopSetup setup) {
        if (setup == null) throw new IllegalArgumentException("Agent loop setup is required");
        Meter meter = new Meter();
        Instant started = setup.clock().instant();
        try {
            Set<String> declaredTools = ToolGrants.forRole(setup.role(), !setup.gates().isEmpty());
            List<ToolSpec> toolSpecs = setup.toolRegistry().specifications(declaredTools);
            journalStarted(setup, declaredTools);
            reportEvent(setup, "LOOP_STARTED", "Agent loop started; tools=" + declaredTools.size());
            var preflightHold = EnforcementPreflight.check(setup.enforcementDescriptor(), setup.baseSha());
            if (preflightHold.isPresent()) {
                PolicyHold hold = preflightHold.get();
                return endPolicyHold(setup, meter, hold.holdClass(), hold.check(), hold.reason());
            }
            ToolGateway toolGateway = setup.toolGateway().withInterceptors(
                    ToolInterceptors.forDescriptor(setup.enforcementDescriptor()));
            String manifest = boundedManifest(contexts.manifest(setup.worktree(), priorities(setup)),
                    Math.min(24 * 1024, setup.budget().maxConversationBytes() / 3));
            String userMessage = initialMessage(setup, manifest);
            setup.journal().append("USER_MESSAGE", Map.of("content", userMessage));
            UserText initial = new UserText(userMessage);
            if (!append(meter, initial, setup.budget().maxConversationBytes()))
                return end(setup, meter, LoopOutcome.BUDGET_STOP, BudgetKind.CONTEXT, null, null, 0);
            List<ConversationItem> items = new ArrayList<>(List.of(initial));
            Map<String, ToolOutcome> gateOutcomes = new HashMap<>();
            int lastWriteStep = 0;
            int consecutiveNoToolTurns = 0;
            int turnNumber = 0;

            while (true) {
                if (setup.leaseLost().get()) return end(setup, meter, LoopOutcome.LEASE_LOST, null, null, null, 0);
                long remainingMillis = remainingMillis(setup, started);
                if (remainingMillis <= 0) return end(setup, meter, LoopOutcome.BUDGET_STOP, BudgetKind.WALL_TIME, null, null, 0);
                if (setup.budget().maxConversationBytes() - meter.counters.conversationBytes() < 4 * 1024)
                    return end(setup, meter, LoopOutcome.BUDGET_STOP, BudgetKind.CONTEXT, null, null, 0);

                PreparedTurn prepared = prepareTurn(setup, items, toolSpecs, meter, NORMAL_OUTPUT_TOKENS,
                        timeout(remainingMillis, NORMAL_TIMEOUT));
                if (prepared == null) return end(setup, meter, LoopOutcome.BUDGET_STOP, BudgetKind.TOKENS, null, null, 0);
                SpendGovernor.Decision reservation = spendGovernor.reserve(prepared.bound(),
                        setup.spendReservationClient(), setup.leaseLost());
                if (reservation.status() == SpendGovernor.Status.REFUSED)
                    return end(setup, meter, LoopOutcome.BUDGET_STOP, BudgetKind.MONEY, null, null, 0);
                if (reservation.status() == SpendGovernor.Status.LEASE_LOST)
                    return end(setup, meter, LoopOutcome.LEASE_LOST, null, null, null, 0);
                if (reservation.status() == SpendGovernor.Status.UNAVAILABLE)
                    return end(setup, meter, LoopOutcome.HARNESS_FAILURE, null, "LOOP_SPEND_RESERVATION_UNAVAILABLE", null, 0);
                turnNumber++;
                String correlationId = setup.leaseId() + "/turn-" + turnNumber;
                meter.turnRequested();
                LoopAttempt attempt = requestTurn(setup, prepared, reservation.reservedMicros(), turnNumber,
                        correlationId, false);
                if (attempt.failure != null) {
                    ProviderExecutionFailure failure = attempt.failure;
                    ProviderFailureEvidence evidence = ProviderFailureEvidence.from(setup.providerPolicy(), failure, correlationId);
                    setup.journal().append("TURN_FAILED", Map.of("turn", turnNumber, "correlationId", correlationId,
                            "attemptCount", failure.attemptCount(), "retryable", evidence.retryable(), "category", evidence.category()));
                    setup.reporter().providerAttempt(ProviderAttemptReport.failed(evidence));
                    return end(setup, meter, LoopOutcome.PROVIDER_FAILURE, null, evidence.category(), null, 0);
                }

                ConversationExecution execution = attempt.execution;
                ConversationTurn turn = execution.turn();
                ProviderCostEstimate cost = costs.fromTokens(setup.providerPolicy(), turn.inputTokens(), turn.outputTokens());
                ProviderUsageEvidence usage = ProviderUsageEvidence.fromTurn(setup.providerPolicy(), execution, cost, correlationId);
                meter.record(turn, cost, prepared.bound().serializedRequestBytes());
                journalTurnCompleted(setup, turnNumber, correlationId, execution);
                setup.reporter().providerAttempt(ProviderAttemptReport.succeeded(usage));

                if (turn.stopReason() == StopReason.MAX_TOKENS) {
                    boolean alreadyRetried = attempt.truncationRetry;
                    if (alreadyRetried) return end(setup, meter, LoopOutcome.BUDGET_STOP, BudgetKind.OUTPUT_LIMIT, null, null, 0);
                    long nowRemaining = remainingMillis(setup, started);
                    if (nowRemaining <= 0) return end(setup, meter, LoopOutcome.BUDGET_STOP, BudgetKind.WALL_TIME, null, null, 0);
                    PreparedTurn retryPrepared = prepareTurn(setup, items, toolSpecs, meter, TRUNCATION_RETRY_TOKENS,
                            timeout(nowRemaining, TRUNCATION_TIMEOUT));
                    if (retryPrepared == null)
                        return end(setup, meter, LoopOutcome.BUDGET_STOP, BudgetKind.TOKENS, null, null, 0);
                    SpendGovernor.Decision retryReservation = spendGovernor.reserve(retryPrepared.bound(),
                            setup.spendReservationClient(), setup.leaseLost());
                    if (retryReservation.status() == SpendGovernor.Status.REFUSED)
                        return end(setup, meter, LoopOutcome.BUDGET_STOP, BudgetKind.MONEY, null, null, 0);
                    if (retryReservation.status() == SpendGovernor.Status.LEASE_LOST)
                        return end(setup, meter, LoopOutcome.LEASE_LOST, null, null, null, 0);
                    if (retryReservation.status() == SpendGovernor.Status.UNAVAILABLE)
                        return end(setup, meter, LoopOutcome.HARNESS_FAILURE, null, "LOOP_SPEND_RESERVATION_UNAVAILABLE", null, 0);
                    turnNumber++;
                    String retryCorrelation = setup.leaseId() + "/turn-" + turnNumber;
                    meter.turnRequested();
                    LoopAttempt retry = requestTurn(setup, retryPrepared, retryReservation.reservedMicros(),
                            turnNumber, retryCorrelation, true);
                    if (retry.failure != null) {
                        ProviderFailureEvidence evidence = ProviderFailureEvidence.from(setup.providerPolicy(), retry.failure, retryCorrelation);
                        setup.journal().append("TURN_FAILED", Map.of("turn", turnNumber, "correlationId", retryCorrelation,
                                "attemptCount", retry.failure.attemptCount(), "retryable", evidence.retryable(), "category", evidence.category()));
                        setup.reporter().providerAttempt(ProviderAttemptReport.failed(evidence));
                        return end(setup, meter, LoopOutcome.PROVIDER_FAILURE, null, evidence.category(), null, 0);
                    }
                    ConversationExecution retried = retry.execution;
                    ConversationTurn retryTurn = retried.turn();
                    ProviderCostEstimate retryCost = costs.fromTokens(setup.providerPolicy(), retryTurn.inputTokens(), retryTurn.outputTokens());
                    ProviderUsageEvidence retryUsage = ProviderUsageEvidence.fromTurn(setup.providerPolicy(), retried, retryCost, retryCorrelation);
                    meter.record(retryTurn, retryCost, retryPrepared.bound().serializedRequestBytes());
                    journalTurnCompleted(setup, turnNumber, retryCorrelation, retried);
                    setup.reporter().providerAttempt(ProviderAttemptReport.succeeded(retryUsage));
                    if (retryTurn.stopReason() == StopReason.MAX_TOKENS)
                        return end(setup, meter, LoopOutcome.BUDGET_STOP, BudgetKind.OUTPUT_LIMIT, null, null, 0);
                    turn = retryTurn;
                }

                if (turn.stopReason() == StopReason.REFUSAL) return end(setup, meter, LoopOutcome.REFUSED, null, "PROVIDER_REFUSAL", null, 0);
                if (turn.stopReason() == StopReason.OTHER) return end(setup, meter, LoopOutcome.HARNESS_FAILURE, null, "LOOP_UNEXPECTED_STOP", null, 0);

                AssistantTurn assistant = turn.toAssistantTurn();
                if (!append(meter, assistant, setup.budget().maxConversationBytes()))
                    return end(setup, meter, LoopOutcome.BUDGET_STOP, BudgetKind.CONTEXT, null, null, 0);
                items.add(assistant);

                if (turn.toolCalls().isEmpty()) {
                    consecutiveNoToolTurns++;
                    if (consecutiveNoToolTurns >= 2) return end(setup, meter, LoopOutcome.DECLINED, null, "WORKER_DECLINED", null, 0);
                    UserText reminder = new UserText("Continue with the tools, or call finish when the change is complete.");
                    if (!append(meter, reminder, setup.budget().maxConversationBytes()))
                        return end(setup, meter, LoopOutcome.BUDGET_STOP, BudgetKind.CONTEXT, null, null, 0);
                    items.add(reminder);
                    continue;
                }
                consecutiveNoToolTurns = 0;

                List<ToolResultItem> toolResults = new ArrayList<>();
                BudgetKind turnBudgetStop = null;
                boolean finishSucceeded = false;
                boolean held = false;
                HoldClass holdClass = null;
                String holdRule = null;
                String holdReason = null;
                String changeSha = null;
                int changedFileCount = 0;
                int step = 0;
                for (ToolCall call : turn.toolCalls()) {
                    step++;
                    String notRunReason = null;
                    if (finishSucceeded) notRunReason = "finish already succeeded";
                    else if (held) notRunReason = "the task is held for a person";
                    else if (setup.leaseLost().get()) notRunReason = "runner lease was lost";
                    else if (remainingMillis(setup, started) <= 0) { notRunReason = "wall-time budget reached"; turnBudgetStop = BudgetKind.WALL_TIME; }
                    else if (!call.name().equals("finish") && meter.counters.toolCalls() >= setup.budget().maxToolCalls()) {
                        notRunReason = "tool-call budget reached"; turnBudgetStop = BudgetKind.TOOL_CALLS;
                    }
                    long wallLeft = remainingMillis(setup, started);
                    ToolContext context = new ToolContext(setup.taskId(), setup.leaseId(), setup.role(), setup.worktree(),
                            declaredTools, setup.ownedPrefixes(), setup.gates(), turnNumber, step, wallLeft,
                            meter.counters, gateOutcomes, notRunReason, setup.baseSha(), lastWriteStep);
                    ToolOutcome outcome;
                    long toolStartedNanos = System.nanoTime();
                    try {
                        outcome = toolGateway.invoke(call, context);
                    } catch (LoopHarnessFailure failure) {
                        return end(setup, meter, LoopOutcome.HARNESS_FAILURE, null, failure.category(), null, 0);
                    } catch (IOException | InterruptedException failure) {
                        if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
                        return end(setup, meter, LoopOutcome.HARNESS_FAILURE, null, "LOOP_HARNESS_FAILURE", null, 0);
                    } catch (RuntimeException failure) {
                        return end(setup, meter, LoopOutcome.HARNESS_FAILURE, null, "LOOP_HARNESS_FAILURE", null, 0);
                    }
                    if (notRunReason == null) {
                        if (!call.name().equals("finish")) meter.call();
                        if (("write_file".equals(call.name()) || "edit_file".equals(call.name())) && outcome.status() == ToolStatus.OK)
                            lastWriteStep = step;
                        if (call.name().equals("run_gate")) {
                            Map<String, Object> gateMeta = new LinkedHashMap<>(outcome.meta());
                            gateMeta.put("step", step);
                            outcome = new ToolOutcome(outcome.status(), outcome.category(), outcome.content(), gateMeta, outcome.postImages());
                            gateOutcomes.put(String.valueOf(outcome.meta().getOrDefault("gate", "")), outcome);
                        }
                        if ("HOLD".equals(outcome.meta().get("decision"))) {
                            held = true;
                            holdClass = HoldClass.valueOf(String.valueOf(outcome.meta().get("holdClass")));
                            holdRule = String.valueOf(outcome.meta().getOrDefault("holdRule", ""));
                            holdReason = String.valueOf(outcome.meta().getOrDefault("holdReason", "Enforcement held the task."));
                        }
                        if (call.name().equals("finish") && outcome.status() == ToolStatus.OK) {
                            finishSucceeded = true;
                            changeSha = String.valueOf(outcome.meta().getOrDefault("changeSha", ""));
                            changedFileCount = Integer.parseInt(String.valueOf(outcome.meta().getOrDefault("changedFiles", 0)));
                        }
                        if (meter.counters.toolCalls() >= setup.budget().maxToolCalls() && !finishSucceeded && turnBudgetStop == null)
                            turnBudgetStop = BudgetKind.TOOL_CALLS;
                    }
                    reportEvent(setup, "LOOP_TOOL_CALLED", toolEvent(call.name(), outcome,
                            Duration.ofNanos(Math.max(0, System.nanoTime() - toolStartedNanos)).toMillis(), meter.counters));
                    String shaped = shaper.shape(call.name(), outcome, ResultShaper.MAX_RESULT_BYTES);
                    toolResults.add(new ToolResultItem(call.id(), call.name(), shaped, outcome.status() == ToolStatus.FAILED));
                }
                ToolResults results = fitToolResults(setup, meter, toolResults);
                if (results == null) return end(setup, meter, LoopOutcome.BUDGET_STOP, BudgetKind.CONTEXT, null, null, 0);
                for (ToolResultItem item : results.results()) {
                    Map<String, Object> shapedRecord = new LinkedHashMap<>();
                    shapedRecord.put("turn", turnNumber);
                    shapedRecord.put("callId", item.callId());
                    shapedRecord.put("tool", item.toolName());
                    shapedRecord.put("content", item.content());
                    shapedRecord.put("contentBytes", item.content().getBytes(StandardCharsets.UTF_8).length);
                    shapedRecord.put("contentSha256", Hashing.sha256(item.content()));
                    setup.journal().append("TOOL_RESULT_SHAPED", shapedRecord);
                }
                meter.addConversationBytes(itemBytes(results));
                items.add(results);
                if (setup.leaseLost().get()) return end(setup, meter, LoopOutcome.LEASE_LOST, null, null, null, 0);
                if (held) return endPolicyHold(setup, meter, holdClass, holdRule, holdReason);
                if (finishSucceeded) return end(setup, meter, LoopOutcome.FINISHED, null, null, changeSha, changedFileCount);
                if (turnBudgetStop != null) return end(setup, meter, LoopOutcome.BUDGET_STOP, turnBudgetStop, null, null, 0);
            }
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            return endQuietly(setup, meter, LoopOutcome.HARNESS_FAILURE, null, "LOOP_HARNESS_FAILURE", null, 0);
        }
    }

    private PreparedTurn prepareTurn(LoopSetup setup, List<ConversationItem> items, List<ToolSpec> specs, Meter meter,
                                     int outputCap, Duration timeout) {
        int outputTokens = outputCap;
        for (int recalculation = 0; recalculation < 8; recalculation++) {
            if (outputTokens < 128) return null;
            ConversationRequest request = new ConversationRequest(setup.providerPolicy().model(), LoopInstructions.text(),
                    List.copyOf(items), specs, outputTokens, timeout);
            String serialized = setup.conversationClient().serialize(request);
            TurnCostBound bound = TurnCostBound.calculate(serialized, meter.lastSerializedRequestBytes,
                    meter.lastInputTokens, outputTokens, setup.providerPolicy(), costs);
            long remainingOutput = (long) setup.budget().maxTokens() - meter.counters.tokens() - bound.inputTokenUpperBound();
            int allowedOutput = (int) Math.max(0, Math.min(outputTokens, remainingOutput));
            if (allowedOutput == outputTokens) return new PreparedTurn(request, serialized, bound);
            // Rebuild because max_output_tokens itself is part of the provider's serialized request.
            outputTokens = allowedOutput;
        }
        return null;
    }

    private LoopAttempt requestTurn(LoopSetup setup, PreparedTurn prepared, long reservedMicros,
                                    int turnNumber, String correlationId, boolean truncationRetry) throws IOException {
        ConversationRequest request = prepared.request();
        String requestHash = Hashing.sha256(prepared.serialized());
        setup.journal().append("TURN_REQUESTED", Map.of("turn", turnNumber, "correlationId", correlationId,
                "requestSha256", requestHash, "maxOutputTokens", request.maxOutputTokens(),
                "reservedMicros", reservedMicros, "truncationRetry", truncationRetry));
        try {
            return new LoopAttempt(conversations.converse(setup.conversationClient(), request, setup.providerPolicy().maxAttempts()), null, truncationRetry);
        } catch (ProviderExecutionFailure failure) {
            return new LoopAttempt(null, failure, truncationRetry);
        }
    }

    private ToolResults fitToolResults(LoopSetup setup, Meter meter, List<ToolResultItem> rawResults) throws IOException {
        long remaining = setup.budget().maxConversationBytes() - meter.counters.conversationBytes();
        int count = rawResults.size();
        if (remaining < count * 1024L) return null;
        List<ToolResultItem> shaped = new ArrayList<>();
        long left = remaining;
        for (int index = 0; index < count; index++) {
            int remainingItems = count - index;
            int allowance = (int) Math.max(1024, Math.min(ResultShaper.MAX_RESULT_BYTES, left / remainingItems));
            ToolResultItem raw = rawResults.get(index);
            // Raw tool results already include their category prefix, so reshape them as plain text.
            ToolOutcome asOutcome = ToolOutcome.ok(raw.content());
            String content = shaper.shape(raw.toolName(), asOutcome, allowance);
            shaped.add(new ToolResultItem(raw.callId(), raw.toolName(), content, raw.error()));
            left -= content.getBytes(StandardCharsets.UTF_8).length;
        }
        ToolResults result = new ToolResults(shaped);
        if (itemBytes(result) <= remaining) return result;
        // JSON field and call-id overhead also counts against the append-only conversation budget.
        List<ToolResultItem> reduced = shaped.stream().map(item -> new ToolResultItem(item.callId(), item.toolName(),
                shaper.shape(item.toolName(), ToolOutcome.ok(item.content()),
                        Math.max(1, item.content().getBytes(StandardCharsets.UTF_8).length / 2)), item.error())).toList();
        result = new ToolResults(reduced);
        return itemBytes(result) <= remaining ? result : null;
    }

    private void journalStarted(LoopSetup setup, Set<String> tools) throws IOException {
        List<ToolSpec> specs = setup.toolRegistry().specifications(tools);
        String toolsHash = Hashing.sha256(JSON.writeValueAsString(specs));
        String instructions = LoopInstructions.text();
        Map<String, Object> start = new LinkedHashMap<>();
        start.put("taskId", setup.taskId()); start.put("repository", setup.repository());
        start.put("executionRole", setup.role()); start.put("title", setup.title());
        start.put("provider", setup.providerPolicy().provider()); start.put("model", setup.providerPolicy().model());
        start.put("maxAttempts", setup.providerPolicy().maxAttempts());
        start.put("adapterId", setup.conversationClient().adapterId());
        start.put("serializerVersion", setup.conversationClient().serializerVersion());
        start.put("baseSha", setup.baseSha()); start.put("instructions", instructions);
        start.put("instructionsSha256", Hashing.sha256(instructions));
        start.put("toolsSha256", toolsHash); start.put("tools", specs); start.put("budget", setup.budget());
        start.put("gates", setup.gates());
        start.put("enforcement", setup.enforcementDescriptor().journalValue());
        start.put("startedAt", setup.clock().instant().toString()); start.put("runnerVersion", "0.1.0");
        setup.journal().append("LOOP_STARTED", start);
    }

    private static String toolEvent(String name, ToolOutcome outcome, long durationMillis, LoopCounters counters) {
        String category = outcome.category() == null ? "" : outcome.category().name();
        int bytes = outcome.content().getBytes(StandardCharsets.UTF_8).length;
        String decision = String.valueOf(outcome.meta().getOrDefault("decision", outcome.status() == ToolStatus.OK ? "ALLOW" : "DENY"));
        String rule = String.valueOf(outcome.meta().getOrDefault("interceptor", outcome.meta().getOrDefault("holdRule", "")));
        return "tool=" + name + "; status=" + outcome.status() + "; category=" + category
                + ("ALLOW".equals(decision) ? "" : "; decision=" + decision + (rule.isBlank() ? "" : "; rule=" + rule))
                + "; bytes=" + bytes + "; durationMs=" + durationMillis + "; turns=" + counters.turns()
                + "; toolCalls=" + counters.toolCalls() + "; tokens=" + counters.tokens();
    }

    private static void reportEvent(LoopSetup setup, String type, String message) {
        try { setup.reporter().event(type, message); }
        catch (Exception ignored) { /* Local durable records remain authoritative; telemetry is best-effort. */ }
    }

    private void journalTurnCompleted(LoopSetup setup, int turn, String correlationId, ConversationExecution execution) throws IOException {
        ConversationTurn response = execution.turn();
        Map<String, Object> completed = new LinkedHashMap<>();
        completed.put("turn", turn); completed.put("correlationId", correlationId);
        completed.put("attemptCount", execution.attemptCount()); completed.put("stopReason", response.stopReason().name());
        completed.put("inputTokens", response.inputTokens()); completed.put("outputTokens", response.outputTokens());
        completed.put("providerRequestId", response.providerRequestId() == null ? "" : response.providerRequestId()); completed.put("text", response.text());
        completed.put("toolCalls", response.toolCalls()); completed.put("replay", response.replay());
        completed.put("responseBody", response.responseBody());
        setup.journal().append("TURN_COMPLETED", completed);
    }

    private LoopResult end(LoopSetup setup, Meter meter, LoopOutcome outcome, BudgetKind budgetKind,
                           String category, String sha, int changedFiles) {
        LoopResult result = new LoopResult(outcome, budgetKind, meter.counters, sha, category, changedFiles);
        try {
            setup.journal().append("LOOP_ENDED", Map.of("outcome", outcome.name(), "budgetKind", budgetKind == null ? "" : budgetKind.name(),
                    "category", category == null ? "" : category, "changeSha", sha == null ? "" : sha,
                    "changedFiles", changedFiles, "counters", meter.counters));
        } catch (IOException journalFailure) {
            return new LoopResult(LoopOutcome.HARNESS_FAILURE, null, meter.counters, null, "LOOP_HARNESS_FAILURE", 0);
        }
        try { setup.reporter().event("LOOP_ENDED", outcome.name() + (budgetKind == null ? "" : "; budget=" + budgetKind)); }
        catch (Exception ignored) { /* Metadata event delivery is best-effort and cannot change a terminal result. */ }
        return result;
    }

    private LoopResult endPolicyHold(LoopSetup setup, Meter meter, HoldClass holdClass, String holdRule, String reason) {
        LoopResult result = new LoopResult(LoopOutcome.POLICY_HOLD, null, meter.counters, null, null, 0,
                holdClass, holdRule == null || holdRule.isBlank() ? "enforcement-config" : holdRule);
        try {
            Map<String, Object> ended = new LinkedHashMap<>();
            ended.put("outcome", LoopOutcome.POLICY_HOLD.name());
            ended.put("holdClass", holdClass.name());
            ended.put("holdRule", result.holdRule());
            if (reason != null && !reason.isBlank()) ended.put("reason", reason);
            ended.put("counters", meter.counters);
            setup.journal().append("LOOP_ENDED", ended);
        } catch (IOException journalFailure) {
            return new LoopResult(LoopOutcome.HARNESS_FAILURE, null, meter.counters, null, "LOOP_HARNESS_FAILURE", 0);
        }
        try { setup.reporter().event("LOOP_ENDED", "POLICY_HOLD; hold=" + holdClass.name()); }
        catch (Exception ignored) { /* Local durable records remain authoritative; telemetry is best-effort. */ }
        return result;
    }

    private LoopResult endQuietly(LoopSetup setup, Meter meter, LoopOutcome outcome, BudgetKind budgetKind,
                                  String category, String sha, int changedFiles) {
        try { return end(setup, meter, outcome, budgetKind, category, sha, changedFiles); }
        catch (RuntimeException ignored) { return new LoopResult(outcome, budgetKind, meter.counters, sha, category, changedFiles); }
    }

    private static List<String> priorities(LoopSetup setup) {
        List<String> ordered = new ArrayList<>(setup.changedFiles());
        ordered.addAll(setup.ownedPrefixes());
        return List.copyOf(ordered);
    }

    private String initialMessage(LoopSetup setup, String manifest) {
        StringBuilder text = new StringBuilder();
        text.append("Task: ").append(setup.title()).append('\n')
                .append("Execution role: ").append(setup.role()).append('\n')
                .append("Owned paths: ").append(String.join(", ", setup.ownedPrefixes())).append('\n')
                .append("Changed paths in the task chain: ").append(setup.changedFiles().isEmpty() ? "(none)" : String.join(", ", setup.changedFiles())).append('\n')
                .append("Available gates: ").append(setup.gates().isEmpty() ? "(none)" : setup.gates().stream().map(LoopGate::name).reduce((a, b) -> a + ", " + b).orElse("(none)"))
                .append("\n\n<task-specification>\n").append(setup.specification()).append("\n</task-specification>\n\n")
                .append(manifest);
        return text.toString();
    }

    private static String boundedManifest(String manifest, int maxBytes) {
        String[] lines = manifest.split("\n", -1);
        StringBuilder bounded = new StringBuilder();
        int limit = Math.max(256, maxBytes);
        int byteCount = 0;
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index] + (index < lines.length - 1 ? "\n" : "");
            int lineBytes = line.getBytes(StandardCharsets.UTF_8).length;
            if (byteCount + lineBytes > limit) {
                bounded.append("[manifest truncated; use list_files to continue]\n");
                break;
            }
            bounded.append(line);
            byteCount += lineBytes;
        }
        return bounded.toString();
    }

    private static boolean append(Meter meter, ConversationItem item, int maxBytes) throws IOException {
        long size = itemBytes(item);
        if (meter.counters.conversationBytes() + size > maxBytes) return false;
        meter.addConversationBytes(size);
        return true;
    }

    private static long itemBytes(ConversationItem item) throws IOException { return JSON.writeValueAsBytes(item).length; }

    private static long remainingMillis(LoopSetup setup, Instant started) {
        long elapsed = Math.max(0, Duration.between(started, setup.clock().instant()).toMillis());
        return Math.max(0, Duration.ofSeconds(setup.budget().maxWallSeconds()).toMillis() - elapsed);
    }

    private static Duration timeout(long remainingMillis, Duration cap) {
        long millis = Math.min(remainingMillis, cap.toMillis());
        if (millis < 1) return Duration.ofMillis(1);
        return Duration.ofMillis(millis);
    }

    private record PreparedTurn(ConversationRequest request, String serialized, TurnCostBound bound) { }
    private record LoopAttempt(ConversationExecution execution, ProviderExecutionFailure failure, boolean truncationRetry) { }

    private static final class Meter {
        private LoopCounters counters = new LoopCounters(0, 0, 0, 0, 0, 0);
        private long lastInputTokens;
        private long lastSerializedRequestBytes;
        void turnRequested() { counters = new LoopCounters(counters.turns() + 1, counters.toolCalls(), counters.inputTokens(), counters.outputTokens(), counters.conversationBytes(), counters.knownCostMicros()); }
        void record(ConversationTurn turn, ProviderCostEstimate cost, long serializedRequestBytes) {
            counters = new LoopCounters(counters.turns(), counters.toolCalls(),
                    Math.addExact(counters.inputTokens(), turn.inputTokens()), Math.addExact(counters.outputTokens(), turn.outputTokens()),
                    counters.conversationBytes(), Math.addExact(counters.knownCostMicros(), cost.known() ? cost.estimatedCostMicros() : 0));
            lastInputTokens = turn.inputTokens();
            lastSerializedRequestBytes = serializedRequestBytes;
        }
        void call() { counters = new LoopCounters(counters.turns(), counters.toolCalls() + 1, counters.inputTokens(), counters.outputTokens(), counters.conversationBytes(), counters.knownCostMicros()); }
        void addConversationBytes(long bytes) { counters = new LoopCounters(counters.turns(), counters.toolCalls(), counters.inputTokens(), counters.outputTokens(), Math.addExact(counters.conversationBytes(), bytes), counters.knownCostMicros()); }
    }
}
