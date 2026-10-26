package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GuardedPatchWorkerTest {
    @TempDir Path repository;

    @Test void providerCanBeReplacedWithoutChangingWorkerLogic() throws Exception {
        initializeRepository();
        ProviderClient openAiCompatible = ignored -> new ProviderResult("{\"summary\":\"change\",\"changes\":[{\"path\":\"src/result.txt\",\"content\":\"done\",\"message\":\"write result\"}]}", 3, 2, "request-1");

        GuardedPatchResult result = new GuardedPatchWorker().execute(new ProviderExecutionPolicy("openai", "model", 2), openAiCompatible,
                "IMPLEMENTATION", "task", "spec", repository, List.of("src/"));

        assertEquals("done", Files.readString(repository.resolve("src/result.txt")));
        assertEquals("openai", result.usage().provider());
        assertFalse(result.commitSha().isBlank());
    }

    @Test void writingOutputAllowancePreservesDefaultAndValidatesOptIn() {
        assertEquals(8192, GuardedPatchWorker.patchOutputTokens(null));
        assertEquals(8192, GuardedPatchWorker.patchOutputTokens(""));
        assertEquals(16384, GuardedPatchWorker.patchOutputTokens("16384"));
        assertEquals(32768, GuardedPatchWorker.patchOutputTokens("32768"));
        for (String invalid : List.of("8191", "32769", "-1", "many")) {
            assertThrows(IllegalArgumentException.class, () -> GuardedPatchWorker.patchOutputTokens(invalid));
        }
    }

    @Test void fullFilePatchesHaveRoomAfterProviderReasoning() throws Exception {
        initializeRepository();
        ProviderClient provider = request -> {
            // Live reasoning-capable models can consume most of 8K before emitting file contents.
            assertEquals(16_384, request.maxOutputTokens());
            assertTrue(request.instructions().contains("summary must be nonempty and at most 500 characters"));
            return new ProviderResult("{\"summary\":\"change\",\"changes\":[{\"path\":\"src/result.txt\",\"content\":\"done\",\"message\":\"write result\"}]}", 3, 2, "request-budget");
        };
        new GuardedPatchWorker(16_384).execute(new ProviderExecutionPolicy("anthropic", "model", 1), provider,
                "IMPLEMENTATION", "task", "spec", repository, List.of("src/"));
        assertEquals("done", Files.readString(repository.resolve("src/result.txt")));
    }

    @Test void independentTestsMustExerciseProductionInterfaces() throws Exception {
        initializeRepository();
        ProviderClient provider = request -> {
            assertTrue(request.instructions().contains("Import and execute the real production modules"));
            return new ProviderResult("{\"summary\":\"test\",\"changes\":[{\"path\":\"tests/feature.js\",\"content\":\"import '../src/feature.js';\",\"message\":\"test public interface\"}]}", 1, 1, "test-interface");
        };
        new GuardedPatchWorker().execute(new ProviderExecutionPolicy("anthropic", "model", 1), provider,
                "INDEPENDENT_TEST", "test", "spec", repository, List.of("tests/"));
    }

    @Test void verboseCommitProseDoesNotDiscardOtherwiseValidFileChanges() throws Exception {
        initializeRepository();
        String output = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of(
                "summary", "details ".repeat(120), "changes", java.util.List.of(java.util.Map.of(
                        "path", "src/result.txt", "content", "exact contents\n", "message", "write result"))));
        assertThrows(IllegalArgumentException.class, () -> PatchPlan.parse(output));
        ProviderClient provider = request -> new ProviderResult(output, 1, 1, "verbose-metadata");
        new GuardedPatchWorker().execute(new ProviderExecutionPolicy("anthropic", "model", 1), provider,
                "IMPLEMENTATION", "task", "spec", repository, List.of("src/"));
        assertEquals("exact contents\n", Files.readString(repository.resolve("src/result.txt")));
    }

    @Test void metadataNormalizationNeverPermitsOutOfScopeCode() throws Exception {
        initializeRepository();
        String output = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of(
                "summary", "details ".repeat(120), "changes", java.util.List.of(java.util.Map.of(
                        "path", "tests/outside.js", "content", "forbidden", "message", "write"))));
        ProviderClient provider = request -> new ProviderResult(output, 1, 1, "scoped-metadata");
        assertThrows(GuardedPatchFailure.class, () -> new GuardedPatchWorker().execute(
                new ProviderExecutionPolicy("anthropic", "model", 1), provider,
                "IMPLEMENTATION", "task", "spec", repository, List.of("src/")));
        assertFalse(Files.exists(repository.resolve("tests/outside.js")));
    }

    @Test void normalizedMetadataStillRejectsBlankAndDoesNotSplitSurrogates() throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var changes = java.util.List.of(java.util.Map.of("path", "src/result.txt", "content", "unaltered", "message", "write"));
        assertThrows(IllegalArgumentException.class, () -> PatchPlan.parseForWorker(json.writeValueAsString(java.util.Map.of("summary", " ", "changes", changes))));
        assertThrows(IllegalArgumentException.class, () -> PatchPlan.parseForWorker(json.writeValueAsString(java.util.Map.of("summary", 12, "changes", changes))));
        var plan = PatchPlan.parseForWorker(json.writeValueAsString(java.util.Map.of("summary", "a".repeat(499) + "\uD83D\uDE00" + "rest", "changes", changes)));
        assertEquals(499, plan.summary().length());
        assertEquals("unaltered", plan.changes().getFirst().content());
    }

    @Test void malformedProviderOutputCannotModifyOrAdvanceWork() throws Exception {
        initializeRepository();
        ProviderClient malformed = ignored -> new ProviderResult("not-json", 1, 1, "request-2");

        assertThrows(GuardedPatchFailure.class, () -> new GuardedPatchWorker().execute(new ProviderExecutionPolicy("anthropic", "model", 1), malformed,
                "IMPLEMENTATION", "task", "spec", repository, List.of("src/")));
        assertFalse(Files.exists(repository.resolve("src/result.txt")));
    }

    @Test void unchangedRepairProducesAnIntegratableMarkerButImplementationStillFails() throws Exception {
        initializeRepository();
        ProviderClient provider = ignored -> new ProviderResult("""
                {"summary":"Existing contents already satisfy the repair","changes":[{"path":"README.md","content":"base","message":"retain correct contents"}]}
                """, 3, 2, "no-op-repair");
        String base = new GitWorktreeManager().headSha(repository);
        assertThrows(GuardedPatchFailure.class, () -> new GuardedPatchWorker().execute(
                new ProviderExecutionPolicy("anthropic", "model", 1), provider,
                "IMPLEMENTATION", "task", "spec", repository, List.of("README.md")));
        GuardedPatchResult result = new GuardedPatchWorker().execute(
                new ProviderExecutionPolicy("anthropic", "model", 1), provider,
                "REPAIR", "task", "spec", repository, List.of("README.md"));
        assertFalse(base.equals(result.commitSha()));
        assertTrue(new GitWorktreeManager().changedFilesInCommit(repository, base, result.commitSha()).isEmpty());
    }

    @Test void reportsTestBoundaryViolationsSeparatelyAndWritesNothing() throws Exception {
        initializeRepository();
        ProviderClient provider = ignored -> new ProviderResult("""
                {"summary":"change","changes":[{"path":"src/Feature.test.ts","content":"forbidden","message":"test file"}]}
                """, 1, 1, "boundary-request");

        GuardedPatchFailure failure = org.junit.jupiter.api.Assertions.assertThrows(GuardedPatchFailure.class,
                () -> new GuardedPatchWorker().execute(new ProviderExecutionPolicy("openai", "model", 1), provider,
                        "IMPLEMENTATION", "task", "spec", repository, List.of("src"), List.of("src"),
                        "boundary-lease", new WriteBoundary("NO_TESTS", List.of("**/*.test.ts"))));

        assertEquals("TEST_BOUNDARY_VIOLATION", failure.category());
        assertFalse(Files.exists(repository.resolve("src/Feature.test.ts")));
    }

    @Test void onlyTestFirstTasksReceiveBoundaryPromptInstructions() {
        assertTrue(GuardedPatchWorker.boundaryInstructions(new WriteBoundary("TESTS_ONLY", List.of("tests/**")))
                .contains("You may write only test files"));
        assertTrue(GuardedPatchWorker.boundaryInstructions(new WriteBoundary("NO_TESTS", List.of("tests/**")))
                .contains("You may not write test files"));
        assertEquals("", GuardedPatchWorker.boundaryInstructions(WriteBoundary.any()));
    }

    @Test void nonCodeRoleCannotReceiveRepositoryWriteCapability() throws Exception {
        initializeRepository();
        ProviderClient provider = ignored -> new ProviderResult("{}", 1, 1, "request");
        assertThrows(IllegalArgumentException.class, () -> new GuardedPatchWorker().execute(new ProviderExecutionPolicy("anthropic", "model", 1), provider,
                "PLANNER", "task", "spec", repository, List.of("src/")));
    }

    @Test void changedDependencyContextIsPrioritizedButCannotExpandWriteScope() throws Exception {
        initializeRepository();
        Files.createDirectories(repository.resolve("tests"));
        Files.createDirectories(repository.resolve("src"));
        Files.createDirectories(repository.resolve("docs"));
        Files.writeString(repository.resolve("tests/Acceptance.java"), "dependency acceptance contract");
        Files.writeString(repository.resolve("src/Feature.java"), "owned source file");
        Files.writeString(repository.resolve("docs/Overview.md"), "general repository context");
        AtomicReference<ProviderRequest> captured = new AtomicReference<>();
        ProviderClient provider = request -> {
            captured.set(request);
            return new ProviderResult("""
                    {"summary":"change test","changes":[{"path":"tests/Acceptance.java","content":"rewritten","message":"attempt out-of-scope write"}]}
                    """, 1, 1, "request-context");
        };

        assertThrows(GuardedPatchFailure.class, () -> new GuardedPatchWorker().execute(
                new ProviderExecutionPolicy("openai", "model", 1), provider, "IMPLEMENTATION", "task", "spec",
                repository, List.of("src/"), List.of("src/", "tests/Acceptance.java"), "context-lease"));

        String input = captured.get().input();
        assertTrue(input.indexOf("--- tests/Acceptance.java ---") < input.indexOf("--- docs/Overview.md ---"));
        assertEquals("dependency acceptance contract", Files.readString(repository.resolve("tests/Acceptance.java")));
    }

    @Test void normalizesAndBoundsProviderGeneratedCommitSubjects() {
        String message = GuardedPatchWorker.commitMessage("Long summary\n" + "x".repeat(500));
        assertEquals(200, message.length());
        assertFalse(message.contains("\n"));
    }

    private void initializeRepository() throws Exception {
        run("git", "init", repository.toString());
        run("git", "-C", repository.toString(), "config", "user.email", "runner@example.test");
        run("git", "-C", repository.toString(), "config", "user.name", "ForgeLoop Runner");
        Files.writeString(repository.resolve("README.md"), "base");
        run("git", "-C", repository.toString(), "add", ".");
        run("git", "-C", repository.toString(), "commit", "-m", "base");
    }

    private static void run(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        if (process.waitFor() != 0) throw new IllegalStateException(new String(process.getInputStream().readAllBytes()));
    }
}
