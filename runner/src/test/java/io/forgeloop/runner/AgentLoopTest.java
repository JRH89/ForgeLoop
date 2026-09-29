package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentLoopTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temporaryDirectory;

    @Test
    void finishCommitsOwnedWorkAndNoLaterToolCallRuns() throws Exception {
        Path repo = gitRepository("finish");
        ScriptedClient client = new ScriptedClient(
                turn(List.of(call("write_file", "{\"path\":\"src/Feature.java\",\"content\":\"class Feature {}\"}"),
                        call("finish", "{\"summary\":\"Add the feature\"}"),
                        call("read_file", "{\"path\":\"README.md\",\"startLine\":1,\"maxLines\":10}")), StopReason.TOOL_USE));

        RecordingReporter reporter = new RecordingReporter();
        LoopResult result = run("finish", repo, client, reporter, new AtomicBoolean(), standardBudget(1, 50_000, 65_536),
                new ProviderExecutionPolicy("openai", "test-model", 1), "small specification");

        assertEquals(LoopOutcome.FINISHED, result.outcome());
        assertEquals(1, result.counters().toolCalls(), "finish is exempt from tool-call accounting");
        assertEquals(1, result.changedFiles());
        assertFalse(result.changeSha().isBlank());
        assertEquals("class Feature {}", Files.readString(repo.resolve("src/Feature.java")));
        List<JsonNode> records = new StepJournal(temporaryDirectory.resolve("finish-state"), "task-1", "lease-1", Clock.systemUTC()).records();
        assertEquals(3, records.stream().filter(row -> row.path("type").asText().equals("TOOL_REQUESTED")).count());
        assertTrue(records.stream().filter(row -> row.path("type").asText().equals("TOOL_COMPLETED"))
                .map(row -> row.path("outcome").path("content").asText()).anyMatch(text -> text.contains("finish already succeeded")));
        JsonNode started = records.stream().filter(row -> row.path("type").asText().equals("LOOP_STARTED")).findFirst().orElseThrow();
        assertEquals("JRH89/agent-loop-test", started.path("repository").asText());
        assertEquals(1, started.path("maxAttempts").asInt());
        assertEquals("ScriptedClient", started.path("adapterId").asText());
        assertEquals(1, started.path("serializerVersion").asInt());
        assertEquals(LoopInstructions.text(), started.path("instructions").asText());
        assertTrue(records.stream().anyMatch(row -> row.path("type").asText().equals("TOOL_RESULT_SHAPED")
                && row.path("contentSha256").asText().length() == 64));
        assertTrue(reporter.events.stream().anyMatch(event -> event.startsWith("LOOP_TOOL_CALLED; tool=write_file")));
        assertFalse(reporter.events.stream().filter(event -> event.startsWith("LOOP_TOOL_CALLED"))
                .anyMatch(event -> event.contains("Feature.java")), "progress events must not disclose paths");
        assertTrue(client.requests.size() == 1);
    }

    @Test
    void givesOneNoToolReminderThenDeclinesOnTheSecondConsecutiveTurn() throws Exception {
        Path repo = gitRepository("decline");
        ScriptedClient client = new ScriptedClient(turn(List.of(), StopReason.END_TURN), turn(List.of(), StopReason.END_TURN));
        LoopResult result = run("decline", repo, client, new RecordingReporter(), new AtomicBoolean(), standardBudget(10, 50_000, 65_536),
                new ProviderExecutionPolicy("openai", "test-model", 1), "small specification");
        assertEquals(LoopOutcome.DECLINED, result.outcome());
        assertEquals(2, client.requests.size());
        ConversationRequest second = client.requests.get(1);
        assertTrue(second.items().stream().anyMatch(item -> item instanceof UserText user && user.text().contains("Continue with the tools")));
    }

    @Test
    void finishesWhenToolLimitIsReachedButStopsRemainingCallsAndDoesNotSpendAfterFinish() throws Exception {
        Path repo = gitRepository("tool-budget");
        ScriptedClient client = new ScriptedClient(turn(List.of(
                call("write_file", "{\"path\":\"src/Feature.java\",\"content\":\"ready\"}"),
                call("finish", "{\"summary\":\"Complete\"}"),
                call("read_file", "{\"path\":\"README.md\",\"startLine\":1,\"maxLines\":5}")), StopReason.TOOL_USE));
        LoopResult result = run("tool-budget", repo, client, new RecordingReporter(), new AtomicBoolean(), standardBudget(1, 50_000, 65_536),
                new ProviderExecutionPolicy("openai", "test-model", 1), "small specification");
        assertEquals(LoopOutcome.FINISHED, result.outcome());
        assertEquals(1, result.counters().toolCalls());
        assertFalse(Files.readString(repo.resolve("README.md")).contains("unused"));
        assertEquals(3, new StepJournal(temporaryDirectory.resolve("tool-budget-state"), "task-1", "lease-1", Clock.systemUTC())
                .records().stream().filter(row -> row.path("type").asText().equals("TOOL_COMPLETED")).count());
    }

    @Test
    void reachesToolCallBudgetAndJournalsEveryRemainingCallAsNotExecuted() throws Exception {
        Path repo = gitRepository("tool-budget-stop");
        ScriptedClient client = new ScriptedClient(turn(List.of(
                call("write_file", "{\"path\":\"src/First.txt\",\"content\":\"first\"}"),
                call("write_file", "{\"path\":\"src/Second.txt\",\"content\":\"second\"}")), StopReason.TOOL_USE));
        LoopResult result = run("tool-budget-stop", repo, client, new RecordingReporter(), new AtomicBoolean(), standardBudget(1, 50_000, 65_536),
                new ProviderExecutionPolicy("openai", "test-model", 1), "small specification");
        assertEquals(LoopOutcome.BUDGET_STOP, result.outcome());
        assertEquals(BudgetKind.TOOL_CALLS, result.budgetKind());
        assertTrue(Files.exists(repo.resolve("src/First.txt")));
        assertFalse(Files.exists(repo.resolve("src/Second.txt")));
        assertEquals(2, new StepJournal(temporaryDirectory.resolve("tool-budget-stop-state"), "task-1", "lease-1", Clock.systemUTC())
                .records().stream().filter(row -> row.path("type").asText().equals("TOOL_COMPLETED")).count());
    }

    @Test
    void refusesBeforeCallingProviderWhenMoneyEstimateExceedsBudget() throws Exception {
        Path repo = gitRepository("money");
        ScriptedClient client = new ScriptedClient();
        ProviderExecutionPolicy priced = new ProviderExecutionPolicy("openai", "test-model", 1,
                new java.math.BigDecimal("1"), new java.math.BigDecimal("1"));
        LoopResult result = run("money", repo, client, new RecordingReporter(), new AtomicBoolean(), standardBudget(10, 50_000, 65_536),
                priced, "small specification", 100, 0, Clock.systemUTC());
        assertEquals(LoopOutcome.BUDGET_STOP, result.outcome());
        assertEquals(BudgetKind.MONEY, result.budgetKind());
        assertTrue(client.requests.isEmpty());
    }

    @Test
    void prechecksTokenGrowthBeforeAnotherProviderTurn() throws Exception {
        Path repo = gitRepository("tokens");
        ScriptedClient client = new ScriptedClient(turn(List.of(call("write_file", "{\"path\":\"src/Feature.java\",\"content\":\"ready\"}")),
                StopReason.TOOL_USE, 4_500, 4_000));
        LoopResult result = run("tokens", repo, client, new RecordingReporter(), new AtomicBoolean(), standardBudget(10, 10_000, 65_536),
                new ProviderExecutionPolicy("openai", "test-model", 1), "small specification");
        assertEquals(LoopOutcome.BUDGET_STOP, result.outcome());
        assertEquals(BudgetKind.TOKENS, result.budgetKind());
        assertEquals(1, client.requests.size());
        assertTrue(Files.exists(repo.resolve("src/Feature.java")));
    }

    @Test
    void clampsToolsAtWallBudgetAndDoesNotRunGateOrWriteAfterDeadline() throws Exception {
        Path repo = gitRepository("wall");
        MutableClock clock = new MutableClock(Instant.parse("2026-09-28T00:00:00Z"));
        ScriptedClient client = new ScriptedClient(turn(List.of(call("write_file", "{\"path\":\"src/Feature.java\",\"content\":\"ready\"}")), StopReason.TOOL_USE));
        client.afterResponse = () -> clock.advance(Duration.ofSeconds(60));
        LoopResult result = run("wall", repo, client, new RecordingReporter(), new AtomicBoolean(), standardBudget(10, 50_000, 65_536),
                new ProviderExecutionPolicy("openai", "test-model", 1), "small specification", 0, 0, clock);
        assertEquals(LoopOutcome.BUDGET_STOP, result.outcome());
        assertEquals(BudgetKind.WALL_TIME, result.budgetKind());
        assertFalse(Files.exists(repo.resolve("src/Feature.java")));
    }

    @Test
    void retriesOneTruncatedOutputWithAUniqueCorrelationThenStopsOnSecondTruncation() throws Exception {
        Path repo = gitRepository("truncation");
        ScriptedClient client = new ScriptedClient(turn(List.of(), StopReason.MAX_TOKENS, 1_000, 1_000),
                turn(List.of(), StopReason.MAX_TOKENS, 1_000, 1_000));
        LoopResult result = run("truncation", repo, client, new RecordingReporter(), new AtomicBoolean(), standardBudget(10, 50_000, 65_536),
                new ProviderExecutionPolicy("openai", "test-model", 1), "small specification");
        assertEquals(LoopOutcome.BUDGET_STOP, result.outcome());
        assertEquals(BudgetKind.OUTPUT_LIMIT, result.budgetKind());
        assertEquals(2, client.requests.size());
        List<String> correlations = new StepJournal(temporaryDirectory.resolve("truncation-state"), "task-1", "lease-1", Clock.systemUTC())
                .records().stream().filter(row -> row.path("type").asText().equals("TURN_REQUESTED"))
                .map(row -> row.path("correlationId").asText()).toList();
        assertEquals(List.of("lease-1/turn-1", "lease-1/turn-2"), correlations);
        assertNotEquals(client.outputCaps.get(0), client.outputCaps.get(1));
    }

    @Test
    void refusalProviderFailureLeaseLossAndOversizedContextHaveDistinctStops() throws Exception {
        Path refusalRepo = gitRepository("refusal");
        LoopResult refused = run("refusal", refusalRepo, new ScriptedClient(turn(List.of(), StopReason.REFUSAL)), new RecordingReporter(), new AtomicBoolean(),
                standardBudget(10, 50_000, 65_536), new ProviderExecutionPolicy("openai", "test-model", 1), "small specification");
        assertEquals(LoopOutcome.REFUSED, refused.outcome());

        Path failureRepo = gitRepository("provider-failure");
        ScriptedClient failing = new ScriptedClient(new ProviderException("unavailable", true));
        RecordingReporter reporter = new RecordingReporter();
        LoopResult providerFailure = run("provider-failure", failureRepo, failing, reporter, new AtomicBoolean(), standardBudget(10, 50_000, 65_536),
                new ProviderExecutionPolicy("openai", "test-model", 1), "small specification");
        assertEquals(LoopOutcome.PROVIDER_FAILURE, providerFailure.outcome());
        assertEquals("FAILED", reporter.reports.getFirst().outcome());

        Path lostRepo = gitRepository("lease-lost");
        ScriptedClient unused = new ScriptedClient();
        LoopResult lost = run("lease-lost", lostRepo, unused, new RecordingReporter(), new AtomicBoolean(true), standardBudget(10, 50_000, 65_536),
                new ProviderExecutionPolicy("openai", "test-model", 1), "small specification");
        assertEquals(LoopOutcome.LEASE_LOST, lost.outcome());
        assertTrue(unused.requests.isEmpty());

        Path midTurnRepo = gitRepository("lease-lost-mid-turn");
        AtomicBoolean lostDuringTurn = new AtomicBoolean();
        ScriptedClient midTurn = new ScriptedClient(turn(List.of(call("write_file", "{\"path\":\"src/Feature.java\",\"content\":\"should not write\"}")), StopReason.TOOL_USE));
        midTurn.afterResponse = () -> lostDuringTurn.set(true);
        LoopResult midTurnResult = run("lease-lost-mid-turn", midTurnRepo, midTurn, new RecordingReporter(), lostDuringTurn,
                standardBudget(10, 50_000, 65_536), new ProviderExecutionPolicy("openai", "test-model", 1), "small specification");
        assertEquals(LoopOutcome.LEASE_LOST, midTurnResult.outcome());
        assertFalse(Files.exists(midTurnRepo.resolve("src/Feature.java")));

        Path largeRepo = gitRepository("context");
        ScriptedClient noCall = new ScriptedClient();
        LoopResult context = run("context", largeRepo, noCall, new RecordingReporter(), new AtomicBoolean(), standardBudget(10, 50_000, 65_536),
                new ProviderExecutionPolicy("openai", "test-model", 1), "x".repeat(80_000));
        assertEquals(LoopOutcome.BUDGET_STOP, context.outcome());
        assertEquals(BudgetKind.CONTEXT, context.budgetKind());
        assertTrue(noCall.requests.isEmpty());
    }

    @Test
    void journalFailureIsAHarnessStopAndNeverAProviderToolResult() throws Exception {
        Path repo = gitRepository("journal-failure");
        String baseSha = command("git", "-C", repo.toString(), "rev-parse", "HEAD");
        ScriptedClient client = new ScriptedClient(turn(List.of(call("read_file", "{\"path\":\"README.md\",\"startLine\":1,\"maxLines\":1}")), StopReason.TOOL_USE));
        LoopJournal unavailable = (type, fields) -> { throw new IOException("journal is read-only"); };
        ToolRegistry registry = ToolRegistry.standard(new GitWorktreeManager(), null);
        ToolGateway gateway = new ToolGateway(registry, unavailable, List.of());
        LoopSetup setup = new LoopSetup("task-1", "JRH89/agent-loop-test", "lease-1", "IMPLEMENTATION", "Implement a small feature", "small specification",
                List.of("src/"), List.of(), repo, baseSha, new ProviderExecutionPolicy("openai", "test-model", 1), client,
                standardBudget(10, 50_000, 65_536), List.of(), gateway, registry, unavailable, Clock.systemUTC(), new RecordingReporter(),
                new AtomicBoolean(), 0, 0);
        LoopResult result = new AgentLoop().run(setup);
        assertEquals(LoopOutcome.HARNESS_FAILURE, result.outcome());
        assertEquals("LOOP_HARNESS_FAILURE", result.category());
        assertTrue(client.requests.isEmpty());
    }

    @Test
    void invalidEnforcementConfigHoldsBeforeProviderSpendAndIsFingerprinted() throws Exception {
        Path repo = gitRepository("preflight-hold");
        String baseSha = command("git", "-C", repo.toString(), "rev-parse", "HEAD");
        ScriptedClient client = new ScriptedClient();
        RecordingReporter reporter = new RecordingReporter();
        StepJournal journal = new StepJournal(temporaryDirectory.resolve("preflight-hold-state"), "task-1", "lease-1", Clock.systemUTC());
        ToolRegistry registry = ToolRegistry.standard(new GitWorktreeManager(), null);
        ToolGateway gateway = new ToolGateway(registry, journal, List.of());
        EnforcementDescriptor invalid = EnforcementDescriptor.fromDispatched("ANY", List.of("**/*Test.java"),
                null, List.of(), false, null, List.of());
        LoopSetup setup = new LoopSetup("task-1", "JRH89/agent-loop-test", "lease-1", "IMPLEMENTATION", "Implement a small feature", "small specification",
                List.of("src/"), List.of(), repo, baseSha, new ProviderExecutionPolicy("openai", "test-model", 1), client,
                standardBudget(10, 50_000, 65_536), List.of(), gateway, registry, journal, Clock.systemUTC(), reporter,
                new AtomicBoolean(), 0, 0, invalid);

        LoopResult result = new AgentLoop().run(setup);

        assertEquals(LoopOutcome.POLICY_HOLD, result.outcome());
        assertEquals(HoldClass.RULE_INPUT_MISSING, result.holdClass());
        assertEquals("enforcement-config", result.holdRule());
        assertTrue(client.requests.isEmpty());
        assertTrue(reporter.reports.isEmpty());
        List<JsonNode> records = journal.records();
        JsonNode started = records.stream().filter(row -> row.path("type").asText().equals("LOOP_STARTED")).findFirst().orElseThrow();
        assertEquals(invalid.sha256(), started.path("enforcement").path("sha256").asText());
        assertTrue(records.stream().anyMatch(row -> row.path("type").asText().equals("LOOP_ENDED")
                && row.path("outcome").asText().equals("POLICY_HOLD") && row.path("holdClass").asText().equals("RULE_INPUT_MISSING")));
        assertTrue(records.stream().noneMatch(row -> row.path("type").asText().equals("TURN_REQUESTED")));
    }

    @Test
    void missingCurrentRedProofHoldsBeforeTheFirstProviderTurn() throws Exception {
        Path repo = gitRepository("red-prerequisite-hold");
        String baseSha = command("git", "-C", repo.toString(), "rev-parse", "HEAD");
        ScriptedClient client = new ScriptedClient();
        RecordingReporter reporter = new RecordingReporter();
        StepJournal journal = new StepJournal(temporaryDirectory.resolve("red-prerequisite-hold-state"), "task-1", "lease-1", Clock.systemUTC());
        ToolRegistry registry = ToolRegistry.standard(new GitWorktreeManager(), null);
        ToolGateway gateway = new ToolGateway(registry, journal, List.of());
        EnforcementDescriptor enforcement = EnforcementDescriptor.fromDispatched("NO_TESTS", List.of("**/*Test.java"),
                new RunnerRedPrerequisite("test-task", null, null), List.of(), false, null, List.of());
        LoopSetup setup = new LoopSetup("task-1", "JRH89/agent-loop-test", "lease-1", "IMPLEMENTATION", "Implement a small feature", "small specification",
                List.of("src/"), List.of(), repo, baseSha, new ProviderExecutionPolicy("openai", "test-model", 1), client,
                standardBudget(10, 50_000, 65_536), List.of(), gateway, registry, journal, Clock.systemUTC(), reporter,
                new AtomicBoolean(), 0, 0, enforcement);

        LoopResult result = new AgentLoop().run(setup);

        assertEquals(LoopOutcome.POLICY_HOLD, result.outcome());
        assertEquals(HoldClass.PREREQUISITE_MISSING, result.holdClass());
        assertEquals("red-prerequisite", result.holdRule());
        assertTrue(client.requests.isEmpty());
        assertTrue(reporter.reports.isEmpty());
        List<JsonNode> records = journal.records();
        assertTrue(records.stream().anyMatch(row -> row.path("type").asText().equals("LOOP_ENDED")
                && row.path("outcome").asText().equals("POLICY_HOLD")
                && row.path("holdClass").asText().equals("PREREQUISITE_MISSING")));
        assertTrue(records.stream().noneMatch(row -> row.path("type").asText().equals("TURN_REQUESTED")));
    }

    @Test
    void holdAtFinishLeavesForbiddenChangeUncommittedAndSkipsRemainingCalls() throws Exception {
        Path repo = gitRepository("boundary-hold");
        String baseSha = command("git", "-C", repo.toString(), "rev-parse", "HEAD");
        Files.createDirectories(repo.resolve(".GitHub/Workflows"));
        Files.writeString(repo.resolve(".GitHub/Workflows/injected.yml"), "name: injected\n");
        ScriptedClient client = new ScriptedClient(turn(List.of(
                call("finish", "{\"summary\":\"try to finish\"}"),
                call("write_file", "{\"path\":\"src/AfterHold.java\",\"content\":\"must not run\"}")), StopReason.TOOL_USE));
        RecordingReporter reporter = new RecordingReporter();

        LoopResult result = run("boundary-hold", repo, client, reporter, new AtomicBoolean(),
                standardBudget(10, 50_000, 65_536), new ProviderExecutionPolicy("openai", "test-model", 1), "small specification");

        assertEquals(LoopOutcome.POLICY_HOLD, result.outcome());
        assertEquals(HoldClass.BOUNDARY_BREACHED, result.holdClass());
        assertEquals("protected-paths", result.holdRule());
        assertEquals(baseSha, command("git", "-C", repo.toString(), "rev-parse", "HEAD"));
        assertTrue(Files.exists(repo.resolve(".GitHub/Workflows/injected.yml")));
        assertFalse(Files.exists(repo.resolve("src/AfterHold.java")));
        StepJournal journal = new StepJournal(temporaryDirectory.resolve("boundary-hold-state"), "task-1", "lease-1", Clock.systemUTC());
        assertTrue(journal.records().stream().anyMatch(row -> row.path("type").asText().equals("TOOL_COMPLETED")
                && row.path("decision").asText().equals("HOLD") && row.path("holdClass").asText().equals("BOUNDARY_BREACHED")));
    }

    private LoopResult run(String directory, Path repo, ScriptedClient client, RecordingReporter reporter, AtomicBoolean lost,
                           LoopBudget budget, ProviderExecutionPolicy policy, String specification) throws Exception {
        return run(directory, repo, client, reporter, lost, budget, policy, specification, 0, 0, Clock.systemUTC());
    }

    private LoopResult run(String directory, Path repo, ScriptedClient client, RecordingReporter reporter, AtomicBoolean lost,
                           LoopBudget budget, ProviderExecutionPolicy policy, String specification,
                           long budgetMicros, long spentCostMicros, Clock clock) throws Exception {
        String baseSha = command("git", "-C", repo.toString(), "rev-parse", "HEAD");
        StepJournal journal = new StepJournal(temporaryDirectory.resolve(directory + "-state"), "task-1", "lease-1", clock);
        GitWorktreeManager git = new GitWorktreeManager();
        ToolRegistry registry = ToolRegistry.standard(git, (gate, worktree, timeout) -> new VerificationResult(0, false, "", clock.instant(), clock.instant()));
        ToolGateway gateway = new ToolGateway(registry, journal, List.of());
        LoopSetup setup = new LoopSetup("task-1", "JRH89/agent-loop-test", "lease-1", "IMPLEMENTATION", "Implement a small feature", specification,
                List.of("src/"), List.of(), repo, baseSha, policy, client, budget, List.of(), gateway, registry, journal,
                clock, reporter, lost, budgetMicros, spentCostMicros);
        return new AgentLoop().run(setup);
    }

    private Path gitRepository(String name) throws Exception {
        Path repo = temporaryDirectory.resolve(name + "-repo"); Files.createDirectories(repo);
        runCommand("git", "init", repo.toString());
        runCommand("git", "-C", repo.toString(), "config", "user.email", "runner@example.test");
        runCommand("git", "-C", repo.toString(), "config", "user.name", "ForgeLoop Test");
        Files.writeString(repo.resolve("README.md"), "base\n");
        runCommand("git", "-C", repo.toString(), "add", ".");
        runCommand("git", "-C", repo.toString(), "commit", "-m", "base");
        return repo;
    }

    private static LoopBudget standardBudget(int calls, int tokens, int conversationBytes) { return new LoopBudget(calls, tokens, 60, conversationBytes); }

    private static ToolCall call(String name, String args) throws IOException { return new ToolCall("call-" + name + "-" + args.hashCode(), name, JSON.readTree(args)); }

    private static ConversationTurn turn(List<ToolCall> calls, StopReason reason) { return turn(calls, reason, 100, 100); }
    private static ConversationTurn turn(List<ToolCall> calls, StopReason reason, long input, long output) {
        return new ConversationTurn("model text", calls, reason, input, output, null, JSON.createObjectNode(), "{\"response\":true}");
    }

    private static void runCommand(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        byte[] output = process.getInputStream().readAllBytes();
        if (process.waitFor() != 0) throw new IllegalStateException(new String(output));
    }

    private static String command(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        byte[] output = process.getInputStream().readAllBytes();
        if (process.waitFor() != 0) throw new IllegalStateException(new String(output));
        return new String(output).strip();
    }

    private static final class ScriptedClient implements ConversationClient {
        private final Deque<Object> scripted = new ArrayDeque<>();
        private final List<ConversationRequest> requests = new ArrayList<>();
        private final List<Integer> outputCaps = new ArrayList<>();
        private Runnable afterResponse = () -> { };
        ScriptedClient(Object... responses) { scripted.addAll(List.of(responses)); }
        @Override public String serialize(ConversationRequest request) {
            return request.model() + "\n" + request.instructions() + "\n" + request.items() + "\n" + request.tools()
                    + "\n" + request.maxOutputTokens() + "\n" + request.timeout().toMillis();
        }
        @Override public ConversationTurn converse(ConversationRequest request) throws ProviderException {
            requests.add(request); outputCaps.add(request.maxOutputTokens());
            Object next = scripted.pollFirst();
            if (next instanceof ProviderException failure) throw failure;
            if (!(next instanceof ConversationTurn response)) throw new ProviderException("No scripted response", false);
            afterResponse.run();
            return response;
        }
    }

    private static final class RecordingReporter implements LoopReporter {
        private final List<ProviderAttemptReport> reports = new ArrayList<>();
        private final List<String> events = new ArrayList<>();
        @Override public void providerAttempt(ProviderAttemptReport report) { reports.add(report); }
        @Override public void event(String type, String message) { events.add(type + "; " + message); }
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;
        MutableClock(Instant initial) { instant = new AtomicReference<>(initial); }
        void advance(Duration duration) { instant.updateAndGet(current -> current.plus(duration)); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant.get(); }
    }
}
