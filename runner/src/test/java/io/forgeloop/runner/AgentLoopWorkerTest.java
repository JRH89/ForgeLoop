package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Offline dispatch coverage uses a real isolated Git worktree and scripted provider turns. */
class AgentLoopWorkerTest {
    @TempDir Path temporary;

    @Test void runnerSwitchAndRepositoryPolicyAreBothRequired() throws Exception {
        RunnerTask task = task();
        assertFalse(AgentLoopWorker.enabled(task, false, true));
        assertTrue(AgentLoopWorker.enabled(task, true, true));
        assertThrows(IllegalArgumentException.class, () -> AgentLoopWorker.enabled(task, true, false));
        assertFalse(AgentLoopWorker.enabled(new RunnerTask("task", "IMPLEMENTATION", "Title", "org/repo", "main", "issue-1", "Spec", "provider"), true, true));
    }

    @Test void writesAndCommitsWithLeaseRenewalAndPerTurnUsage() throws Exception {
        Path repository = repository();
        AtomicInteger renewals = new AtomicInteger(), usage = new AtomicInteger();
        ConversationClient provider = scripted(new AtomicInteger());
        LoopResult result = execute(repository, provider, () -> renewals.incrementAndGet(), usage);
        assertEquals(LoopOutcome.FINISHED, result.outcome());
        assertEquals("hello", Files.readString(repository.resolve("src/feature.txt")));
        assertEquals(new GitWorktreeManager().headSha(repository), result.changeSha());
        assertEquals(2, renewals.get());
        assertEquals(1, usage.get());
    }

    @Test void rejectedLeaseNeverCallsTheProviderOrPublishesACommit() throws Exception {
        Path repository = repository();
        String before = new GitWorktreeManager().headSha(repository);
        AtomicInteger calls = new AtomicInteger();
        LoopResult result = execute(repository, scripted(calls), () -> { throw new Exception("cancelled"); }, new AtomicInteger());
        assertEquals(LoopOutcome.LEASE_LOST, result.outcome());
        assertEquals(0, calls.get());
        assertEquals(before, new GitWorktreeManager().headSha(repository));
        assertNull(result.changeSha());
    }

    @Test void cancellationAtPublicationRevalidationDoesNotReturnACommitForDelivery() throws Exception {
        Path repository = repository();
        AtomicInteger renewals = new AtomicInteger();
        LoopResult result = execute(repository, scripted(new AtomicInteger()), () -> {
            if (renewals.incrementAndGet() == 2) throw new Exception("cancelled");
        }, new AtomicInteger());
        assertEquals(LoopOutcome.LEASE_LOST, result.outcome());
        assertNull(result.changeSha());
    }

    @Test void refusedSharedSpendReservationStopsBeforeTheProviderCall() throws Exception {
        Path repository = repository();
        AtomicInteger calls = new AtomicInteger(), reservations = new AtomicInteger();
        LoopReporter reporter = new LoopReporter() {
            public void providerAttempt(ProviderAttemptReport report) { fail("No provider call was authorized"); }
            public void event(String type, String message) { }
        };
        LoopResult result = new AgentLoopWorker().execute(task(), new ProviderExecutionPolicy("openai", "test-model", 1,
                java.math.BigDecimal.ONE, java.math.BigDecimal.ONE), scripted(calls), repository, "lease",
                new StepJournal(temporary.resolve("state"), "task", "lease", Clock.systemUTC()), reporter,
                micros -> { reservations.incrementAndGet(); return new SpendReservationGrant(false, 0); }, () -> { }, null);
        assertEquals(LoopOutcome.BUDGET_STOP, result.outcome());
        assertEquals(BudgetKind.MONEY, result.budgetKind());
        assertEquals(1, reservations.get());
        assertEquals(0, calls.get());
    }

    @Test void mapsStopsToServerAllowListedHoldReasons() {
        var counters = new LoopCounters(0, 0, 0, 0, 0, 0);
        assertEquals("BUDGET_EXHAUSTED", AgentLoopWorker.holdReason(new LoopResult(LoopOutcome.BUDGET_STOP, BudgetKind.MONEY, counters, null, null, 0)));
        assertEquals("LOOP_BUDGET_EXHAUSTED", AgentLoopWorker.holdReason(new LoopResult(LoopOutcome.BUDGET_STOP, BudgetKind.TOKENS, counters, null, null, 0)));
        assertEquals("ENFORCEMENT_PREREQUISITE_MISSING", AgentLoopWorker.holdReason(new LoopResult(LoopOutcome.POLICY_HOLD, null, counters, null, null, 0, HoldClass.PREREQUISITE_MISSING, "red-prerequisite")));
        assertEquals("WORKER_DECLINED", AgentLoopWorker.holdReason(new LoopResult(LoopOutcome.REFUSED, null, counters, null, null, 0)));
    }

    private LoopResult execute(Path tree, ConversationClient client, LeaseKeeper.Renewal renewal, AtomicInteger usage) throws Exception {
        LoopReporter reporter = new LoopReporter() {
            public void providerAttempt(ProviderAttemptReport report) { usage.incrementAndGet(); }
            public void event(String type, String message) { }
        };
        return new AgentLoopWorker().execute(task(), new ProviderExecutionPolicy("openai", "test-model", 1), client,
                tree, "lease", new StepJournal(temporary.resolve("state"), "task", "lease", Clock.systemUTC()),
                reporter, null, renewal, null);
    }

    private RunnerTask task() {
        return new RunnerTask("task", "IMPLEMENTATION", "Implement feature", "org/repo", "main", "issue-1", "Add a feature", "provider", 1,
                List.of("src"), List.of(), null, null, null, List.of(), null, null, "main", "main", List.of(), List.of(), "ANY", List.of(),
                null, List.of(), false, null, null, null, false,
                new RunnerLoopConfiguration(new LoopBudget(10, 100000, 600, 65536), List.of(), 1000000, 0));
    }

    private ConversationClient scripted(AtomicInteger calls) {
        return new ConversationClient() {
            public String serialize(ConversationRequest request) { return "{}"; }
            public ConversationTurn converse(ConversationRequest request) {
                calls.incrementAndGet();
                ObjectMapper json = new ObjectMapper();
                return new ConversationTurn("", List.of(
                        new ToolCall("write", "write_file", json.valueToTree(Map.of("path", "src/feature.txt", "content", "hello"))),
                        new ToolCall("finish", "finish", json.valueToTree(Map.of("summary", "Add feature")))),
                        StopReason.TOOL_USE, 10, 5, "request", json.createObjectNode(), "{}");
            }
        };
    }

    private Path repository() throws Exception {
        Path repository = Files.createDirectory(temporary.resolve("repo"));
        git(repository, "init", "-b", "main");
        git(repository, "config", "user.name", "Test");
        git(repository, "config", "user.email", "test@example.invalid");
        Files.writeString(repository.resolve("README.md"), "Example");
        git(repository, "add", "."); git(repository, "commit", "-m", "Initial");
        git(repository, "update-ref", "refs/remotes/origin/main", "HEAD");
        return repository;
    }

    private static void git(Path repository, String... arguments) throws Exception {
        var command = new java.util.ArrayList<>(List.of("git", "-C", repository.toString()));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertEquals(0, process.waitFor(), output);
    }
}
