package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

/** Exercises replay through a complete production worker, not only provider parser helpers. */
class ReplayWorkerTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PLAN = "{\"acceptanceCriteria\":[\"done\"],\"tasks\":["
            + "{\"key\":\"implementation\",\"role\":\"IMPLEMENTATION\",\"title\":\"Implement\","
            + "\"requiredCapability\":\"provider\",\"dependencies\":[],\"ownedPaths\":[\"src\"],"
            + "\"attemptBudget\":1,\"budgetMicros\":0},"
            + "{\"key\":\"integration\",\"role\":\"INTEGRATION\",\"title\":\"Integrate\","
            + "\"requiredCapability\":\"git\",\"dependencies\":[\"implementation\"],\"ownedPaths\":[],"
            + "\"attemptBudget\":1,\"budgetMicros\":0}]}";

    @TempDir Path temporary;

    @Test
    void plannerWorkerProducesTheSameValidatedPlanFromRecordedProviderReplay() throws Exception {
        ProviderExecutionPolicy policy = new ProviderExecutionPolicy("openai", "gpt-test", 2);
        RunnerTask task = new RunnerTask("task-1", "PLANNER", "Plan", "org/repository", "main", null,
                "specification", "provider");
        StepJournal journal = new StepJournal(temporary, "planner", "lease-planner", Clock.systemUTC());
        String responseBody = JSON.writeValueAsString(Map.of(
                "id", "resp-planner", "model", "gpt-test", "usage", Map.of("input_tokens", 12, "output_tokens", 8),
                "output", List.of(Map.of("type", "message", "content", List.of(Map.of("type", "output_text", "text", PLAN))))));
        ProviderClient recorded = new RecordingProviderClient(request -> {
            try { return OpenAiResponsesProviderClient.parse(responseBody); }
            catch (Exception invalid) { throw new ProviderException("Test response did not parse", false, invalid); }
        }, policy, journal);

        PlannerResult original = new PlannerWorker().execute(policy, recorded, task, "src/Main.java", "planner-run");
        ReplayProviderClient replay = new ReplayProviderClient("openai", JournalFile.open(journal.path()));
        PlannerResult reproduced = new PlannerWorker().execute(policy, replay, task, "src/Main.java", "planner-run");

        assertEquals(original.plan(), reproduced.plan());
        assertEquals(original.plan().acceptanceCriteria(), reproduced.plan().acceptanceCriteria());
        assertEquals(original.plan().tasks(), reproduced.plan().tasks());
        assertEquals("gpt-test", reproduced.usage().model());
    }

    @Test
    void plannerWorkerReplayRejectsChangedInputsWithoutConsumingTheRecordedCall() throws Exception {
        ProviderExecutionPolicy policy = new ProviderExecutionPolicy("openai", "gpt-test", 1);
        RunnerTask task = new RunnerTask("task-2", "PLANNER", "Plan", "org/repository", "main", null,
                "original spec", "provider");
        StepJournal journal = new StepJournal(temporary, "planner-divergence", "lease-divergence", Clock.systemUTC());
        String responseBody = JSON.writeValueAsString(Map.of(
                "id", "resp-planner", "model", "gpt-test", "usage", Map.of("input_tokens", 12, "output_tokens", 8),
                "output", List.of(Map.of("type", "message", "content", List.of(Map.of("type", "output_text", "text", PLAN))))));
        ProviderClient recorded = new RecordingProviderClient(request -> {
            try { return OpenAiResponsesProviderClient.parse(responseBody); }
            catch (Exception invalid) { throw new ProviderException("Test response did not parse", false, invalid); }
        }, policy, journal);
        new PlannerWorker().execute(policy, recorded, task, "src/Main.java", "planner-run");

        ReplayProviderClient replay = new ReplayProviderClient("openai", JournalFile.open(journal.path()));
        RunnerTask changedTask = new RunnerTask("task-2", "PLANNER", "Plan", "org/repository", "main", null,
                "changed spec", "provider");
        ProviderExecutionFailure divergence = assertThrows(ProviderExecutionFailure.class,
                () -> new PlannerWorker().execute(policy, replay, changedTask, "src/Main.java", "planner-run"));
        assertInstanceOf(ReplayDivergence.class, divergence.providerFailure());

        PlannerResult recovered = new PlannerWorker().execute(policy, replay, task, "src/Main.java", "planner-run");
        assertEquals("implementation", recovered.plan().tasks().getFirst().key());
    }

    @Test
    void guardedPatchWorkerReplaysTheSameValidatedPatchAgainstTheSameBase() throws Exception {
        Path originalRepository = gitRepository("patch-original");
        Path replayRepository = temporary.resolve("patch-replay");
        runCommand("git", "clone", "--quiet", originalRepository.toString(), replayRepository.toString());
        ProviderExecutionPolicy policy = new ProviderExecutionPolicy("openai", "gpt-test", 1);
        String response = responseBody("{\"summary\":\"Add the result file\",\"changes\":["
                + "{\"path\":\"src/result.txt\",\"content\":\"replayed result\",\"message\":\"write result\"}]}");
        StepJournal recordedJournal = new StepJournal(temporary.resolve("patch-recorded-state"), "patch-task", "patch-lease", Clock.systemUTC());
        GuardedPatchWorker worker = new GuardedPatchWorker();

        GuardedPatchResult original = worker.execute(policy, new RecordingProviderClient(parsedOpenAi(response), policy, recordedJournal),
                "IMPLEMENTATION", "Add a result", "Write the requested result file", originalRepository,
                List.of("src"), List.of(), "patch-run", WriteBoundary.any(), recordedJournal);
        StepJournal replayJournal = new StepJournal(temporary.resolve("patch-replay-state"), "patch-task", "patch-lease", Clock.systemUTC());
        GuardedPatchResult replayed = worker.execute(policy,
                new ReplayProviderClient("openai", JournalFile.open(recordedJournal.path())),
                "IMPLEMENTATION", "Add a result", "Write the requested result file", replayRepository,
                List.of("src"), List.of(), "patch-run", WriteBoundary.any(), replayJournal);

        assertEquals("replayed result", Files.readString(originalRepository.resolve("src/result.txt")));
        assertEquals(Files.readString(originalRepository.resolve("src/result.txt")),
                Files.readString(replayRepository.resolve("src/result.txt")));
        assertEquals(original.usage().model(), replayed.usage().model());
        assertFalse(original.commitSha().isBlank());
        assertFalse(replayed.commitSha().isBlank());
    }

    @Test
    void reviewWorkerReplaysTheSameStrictDecisionAndEvidence() throws Exception {
        ProviderExecutionPolicy policy = new ProviderExecutionPolicy("openai", "gpt-test", 1);
        RunnerTask task = new RunnerTask("review-task", "REVIEW", "Review change", "acme/repository", "main", "issue-7",
                "Keep authorization enforced", "provider", 10, List.of(), List.of("a".repeat(40)), null, null,
                null, List.of(), null, null, "main", "main", List.of("Authorization is enforced"));
        String response = responseBody("{\"approved\":true,\"summary\":\"Authorization remains enforced\","
                + "\"criteria\":[{\"statement\":\"Authorization is enforced\",\"status\":\"PASS\","
                + "\"evidence\":\"The guard remains in the service\"}]}");
        StepJournal journal = new StepJournal(temporary, "review-task", "review-lease", Clock.systemUTC());
        ReviewWorker worker = new ReviewWorker();

        ReviewResult original = worker.execute(policy, new RecordingProviderClient(parsedOpenAi(response), policy, journal),
                task, "diff --git a/src/Auth.java b/src/Auth.java", "review-run");
        ReviewResult replayed = worker.execute(policy,
                new ReplayProviderClient("openai", JournalFile.open(journal.path())),
                task, "diff --git a/src/Auth.java b/src/Auth.java", "review-run");

        assertEquals(original.approved(), replayed.approved());
        assertEquals(original.summary(), replayed.summary());
        assertEquals(original.criteria(), replayed.criteria());
        assertEquals(original.usage().model(), replayed.usage().model());
    }

    @Test
    void agentLoopReplaysRecordedToolCallsAndProducesTheSameRepositoryChange() throws Exception {
        Path originalRepository = gitRepository("loop-original");
        Path replayRepository = temporary.resolve("loop-replay");
        runCommand("git", "clone", "--quiet", originalRepository.toString(), replayRepository.toString());
        String response = JSON.writeValueAsString(Map.of(
                "id", "resp-loop", "model", "gpt-test", "status", "completed",
                "usage", Map.of("input_tokens", 20, "output_tokens", 10),
                "output", List.of(
                        Map.of("type", "function_call", "call_id", "write-call", "name", "write_file",
                                "arguments", "{\"path\":\"src/Feature.java\",\"content\":\"class Feature {}\"}"),
                        Map.of("type", "function_call", "call_id", "finish-call", "name", "finish",
                                "arguments", "{\"summary\":\"Add a feature\"}"))));
        StepJournal recordedJournal = new StepJournal(temporary.resolve("loop-recorded-state"), "loop-task", "loop-lease", Clock.systemUTC());
        LoopResult original = runLoop(originalRepository, recordedJournal, openAiConversation(response));
        StepJournal replayJournal = new StepJournal(temporary.resolve("loop-replay-state"), "loop-task", "loop-lease", Clock.systemUTC());
        LoopResult replayed = runLoop(replayRepository, replayJournal,
                new ReplayConversationClient("openai", JournalFile.open(recordedJournal.path())));

        assertEquals(LoopOutcome.FINISHED, original.outcome());
        assertEquals(original.outcome(), replayed.outcome());
        assertEquals(original.changedFiles(), replayed.changedFiles());
        assertEquals("class Feature {}", Files.readString(originalRepository.resolve("src/Feature.java")));
        assertEquals(Files.readString(originalRepository.resolve("src/Feature.java")),
                Files.readString(replayRepository.resolve("src/Feature.java")));
    }

    private static ProviderClient parsedOpenAi(String response) {
        return ignored -> {
            try { return new ProviderClientFactory().parse("openai", response); }
            catch (Exception invalid) { throw new ProviderException("Offline replay response was invalid", false, invalid); }
        };
    }

    private static String responseBody(String text) throws Exception {
        return JSON.writeValueAsString(Map.of("id", "resp-worker", "model", "gpt-test", "status", "completed",
                "usage", Map.of("input_tokens", 12, "output_tokens", 8),
                "output", List.of(Map.of("type", "message", "content", List.of(Map.of("type", "output_text", "text", text))))));
    }

    private ConversationClient openAiConversation(String response) {
        ProviderClientFactory codecs = new ProviderClientFactory();
        return new ConversationClient() {
            @Override public String serialize(ConversationRequest request) { return codecs.conversationBody("openai", request); }
            @Override public ConversationTurn converse(ConversationRequest request) throws ProviderException {
                try { return codecs.parseConversation("openai", response, request); }
                catch (Exception invalid) { throw new ProviderException("Offline replay response was invalid", false, invalid); }
            }
        };
    }

    private LoopResult runLoop(Path repository, StepJournal journal, ConversationClient client) throws Exception {
        String baseSha = runCommandText("git", "-C", repository.toString(), "rev-parse", "HEAD");
        GitWorktreeManager git = new GitWorktreeManager();
        ToolRegistry registry = ToolRegistry.standard(git,
                (gate, worktree, timeout) -> new VerificationResult(0, false, "", Clock.systemUTC().instant(), Clock.systemUTC().instant()));
        ToolGateway gateway = new ToolGateway(registry, journal, List.of());
        LoopSetup setup = new LoopSetup("loop-task", "acme/repository", "loop-lease", "IMPLEMENTATION", "Implement a feature",
                "Create src/Feature.java", List.of("src/"), List.of(), repository, baseSha,
                new ProviderExecutionPolicy("openai", "gpt-test", 1), client, new LoopBudget(5, 10_000, 60, 65_536),
                List.of(), gateway, registry, journal, Clock.systemUTC(), new LoopReporter() {
                    @Override public void providerAttempt(ProviderAttemptReport report) { }
                    @Override public void event(String type, String message) { }
                }, new AtomicBoolean(), 0, 0);
        return new AgentLoop().run(setup);
    }

    private Path gitRepository(String name) throws Exception {
        Path repository = temporary.resolve(name);
        Files.createDirectories(repository);
        runCommand("git", "init", repository.toString());
        runCommand("git", "-C", repository.toString(), "config", "user.email", "runner@example.test");
        runCommand("git", "-C", repository.toString(), "config", "user.name", "ForgeLoop Test");
        Files.writeString(repository.resolve("README.md"), "base\n");
        runCommand("git", "-C", repository.toString(), "add", ".");
        runCommand("git", "-C", repository.toString(), "commit", "-m", "base");
        return repository;
    }

    private static void runCommand(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        byte[] output = process.getInputStream().readAllBytes();
        if (process.waitFor() != 0) throw new IllegalStateException(new String(output));
    }

    private static String runCommandText(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        byte[] output = process.getInputStream().readAllBytes();
        if (process.waitFor() != 0) throw new IllegalStateException(new String(output));
        return new String(output).strip();
    }
}
