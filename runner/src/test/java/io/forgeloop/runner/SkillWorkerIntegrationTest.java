package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Scripted providers exercise real stage parsers and Git boundaries without accounts or model charges. */
class SkillWorkerIntegrationTest {
    @TempDir Path temporary;
    private final ProviderExecutionPolicy policy = new ProviderExecutionPolicy("openai", "model", 1);
    private final StageSkillResolver resolver = new StageSkillResolver(StageSkillResolver.PROFILE, Set.of("org/repo"));

    @Test void plannerStillReturnsTheValidatedTaskGraph() throws Exception {
        AtomicReference<ProviderRequest> captured = new AtomicReference<>();
        String output = """
                {"acceptanceCriteria":["works"],"tasks":[
                  {"key":"code","role":"IMPLEMENTATION","title":"Implement","requiredCapability":"provider","dependencies":[],"ownedPaths":["src"],"attemptBudget":1,"budgetMicros":1000},
                  {"key":"integrate","role":"INTEGRATION","title":"Integrate","requiredCapability":"git","dependencies":["code"],"ownedPaths":[],"attemptBudget":1,"budgetMicros":0}]}
                """;
        var result = new PlannerWorker().execute(policy, provider("PLANNER", output, captured), task("PLANNER"), "src exists", "lease");
        assertEquals(2, result.plan().tasks().size());
        assertTrue(captured.get().instructions().contains("# Structured planning"));
        assertTrue(captured.get().instructions().contains("Return JSON only with exact fields"));
        assertFalse(captured.get().instructions().contains("# Independent review"));
    }

    @Test void implementationAndIndependentTestSkillsKeepMechanicalWriteBoundaries() throws Exception {
        Path repo = repository();
        AtomicReference<ProviderRequest> captured = new AtomicReference<>();
        String change = "{\"summary\":\"Implement\",\"changes\":[{\"path\":\"src/value.txt\",\"content\":\"new\",\"message\":\"Implement feature\"}]}";
        GuardedPatchResult result = new GuardedPatchWorker().execute(policy, provider("BACKEND", change, captured), "BACKEND", "Implement", "works", repo,
                List.of("src"), List.of("src"), "lease", new WriteBoundary("NO_TESTS", List.of("**/*.test.txt")));
        assertEquals("new", Files.readString(repo.resolve("src/value.txt")));
        assertTrue(result.commitSha().matches("[0-9a-f]{40,64}"));
        assertTrue(captured.get().instructions().contains("# Scoped implementation"));
        assertTrue(captured.get().instructions().contains("You may not write test files"));
        String invalid = "{\"summary\":\"Wrong scope\",\"changes\":[{\"path\":\"src/value.txt\",\"content\":\"test writer changed production\",\"message\":\"Wrong\"}]}";
        GuardedPatchFailure failure = assertThrows(GuardedPatchFailure.class, () -> new GuardedPatchWorker().execute(policy,
                provider("INDEPENDENT_TEST", invalid, captured), "INDEPENDENT_TEST", "Tests", "works", repo,
                List.of("src", "tests"), List.of("tests"), "test-lease", new WriteBoundary("TESTS_ONLY", List.of("**/*.test.txt"))));
        assertEquals("TEST_BOUNDARY_VIOLATION", failure.category());
        assertEquals("new", Files.readString(repo.resolve("src/value.txt")));
        assertTrue(captured.get().instructions().contains("# Independent test design"));
        assertTrue(captured.get().instructions().contains("Import and execute the real production modules"));
        assertTrue(captured.get().instructions().contains("test-local replacement"));
    }

    @Test void reviewKeepsCanonicalCriteriaAndStaticBeforeExecutionContract() throws Exception {
        AtomicReference<ProviderRequest> captured = new AtomicReference<>();
        String output = "{\"approved\":true,\"summary\":\"Source is suitable; execution pending\",\"criteria\":[{\"statement\":\"works\",\"status\":\"PASS\",\"evidence\":\"The implementation supports it; independent verification is pending.\"}]}";
        ReviewResult result = new ReviewWorker().execute(policy, provider("REVIEW", output, captured), task("REVIEW"), "diff", "lease", "source");
        assertTrue(result.approved());
        assertEquals("works", result.criteria().getFirst().statement());
        assertTrue(captured.get().instructions().contains("# Independent review"));
        assertTrue(captured.get().instructions().contains("BEFORE mandatory execution verification"));
        assertFalse(captured.get().instructions().contains("# Scoped implementation"));
    }

    @Test void autonomousProposalsAndInteractiveDraftsUseSeparateProceduresAndOriginalSchemas() throws Exception {
        AtomicReference<ProviderRequest> captured = new AtomicReference<>();
        var grant = new RepositoryIssueProposalGrant("proposal", "org/repo", "a".repeat(40), "HIGH", "Reject expired tokens",
                "Expired tokens pass", "Unauthorized access", "Missing expiry check", List.of("src/Auth.java"), List.of("Reject expired tokens"));
        var proposal = new RepositoryIssueProposalWorker().execute(policy, provider("ISSUE_SPECIFICATION",
                "{\"title\":\"Reject expired tokens\",\"body\":\"Add expiry validation\",\"acceptanceCriteria\":[\"Reject expired tokens\"]}", captured), grant);
        assertEquals("Reject expired tokens", proposal.specification().title());
        assertTrue(captured.get().instructions().contains("# Autonomous specification"));
        assertFalse(captured.get().instructions().contains("# Interactive issue refinement"));
        var chat = new IssueChatTurnGrant("chat", "org/repo", "", "", List.of(), List.of(new IssueChatTurnGrant.Message("USER", "Validate token expiry")));
        var draft = new IssueChatWorker().execute(policy, provider("AI_CHAT",
                "{\"assistantMessage\":\"Draft prepared\",\"title\":\"Validate expiry\",\"body\":\"Reject expired tokens\",\"acceptanceCriteria\":[\"Reject expired tokens\"]}", captured), chat);
        assertEquals("Validate expiry", draft.specification().title());
        assertTrue(captured.get().instructions().contains("# Interactive issue refinement"));
        assertTrue(captured.get().instructions().contains("A human will review the draft"));
        assertFalse(captured.get().instructions().contains("# Autonomous specification"));
    }

    private ProviderClient provider(String role, String output, AtomicReference<ProviderRequest> captured) {
        return new SkillProviderClient(request -> { captured.set(request); return new ProviderResult(output, 10, 5, "request"); }, resolver.resolveRole(role, "org/repo"));
    }
    private static RunnerTask task(String role) {
        return new RunnerTask("task", role, "Task", "org/repo", "main", "issue-1", "works", "provider", 1,
                List.of(), List.of(), null, null, null, List.of(), null, null, "main", "main", List.of("works"));
    }
    private Path repository() throws Exception {
        Path repo = Files.createDirectory(temporary.resolve("repository"));
        git(repo, "init"); git(repo, "config", "user.name", "Test"); git(repo, "config", "user.email", "test@example.invalid");
        Files.createDirectory(repo.resolve("src")); Files.createDirectory(repo.resolve("tests"));
        Files.writeString(repo.resolve("src/value.txt"), "old"); Files.writeString(repo.resolve("tests/value.test.txt"), "original test");
        git(repo, "add", "."); git(repo, "commit", "-m", "Initial");
        return repo;
    }
    private static void git(Path repo, String... arguments) throws Exception {
        var command = new ArrayList<>(List.of("git", "-C", repo.toString())); command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes()); assertEquals(0, process.waitFor(), output);
    }
}
