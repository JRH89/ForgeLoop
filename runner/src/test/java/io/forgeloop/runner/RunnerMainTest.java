package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunnerMainTest {
    @TempDir Path temporaryDirectory;

    @Test
    void parsesExplicitRegistrationArguments() {
        RunnerConfig config = RunnerMain.configFrom(new String[]{
                "register", "http://localhost:8090", "registration-token", "build-node", "git,docker"
        });

        assertEquals("build-node", config.name());
        assertEquals(2, config.capabilities().size());
    }

    @Test
    void rejectsIncompleteRegistrationArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> RunnerMain.configFrom(new String[]{"register", "http://localhost:8090", "token", "node"}));
    }

    @Test
    void additionalMcpContextPreservesTestFirstContractFields() {
        RunnerTask task = new RunnerTask("task", "IMPLEMENTATION", "Implement", "org/repository", "main", "issue-1",
                "spec", "provider", 1, List.of("src"), List.of(), null, null, null, List.of(), null, null,
                "main", "main", List.of(), List.of(), "NO_TESTS", List.of("**/*.test.ts"), "JUNIT_XML");

        RunnerTask contextual = RunnerMain.withAdditionalContext(task, "\nextra context");

        assertEquals("NO_TESTS", contextual.writeBoundary());
        assertEquals(List.of("**/*.test.ts"), contextual.testPathGlobs());
        assertEquals("JUNIT_XML", contextual.testReportFormat());
    }

    @Test
    void preparedWorktreeStartsFromTheServerSelectedExecutionBaseRef() throws Exception {
        Path repository = temporaryDirectory.resolve("repository");
        Files.createDirectories(repository);
        run("git", "init", repository.toString());
        run("git", "-C", repository.toString(), "config", "user.email", "runner@example.test");
        run("git", "-C", repository.toString(), "config", "user.name", "ForgeLoop Runner");
        Files.writeString(repository.resolve("README.md"), "base");
        run("git", "-C", repository.toString(), "add", ".");
        run("git", "-C", repository.toString(), "commit", "-m", "base");
        GitWorktreeManager git = new GitWorktreeManager();
        String baseSha = git.headSha(repository);
        Path predecessor = git.create(repository, baseSha, "predecessor", temporaryDirectory.resolve("worktrees"));
        Files.writeString(predecessor.resolve("tests.txt"), "committed predecessor");
        String predecessorSha = git.commit(predecessor, "test: add contract");
        RunnerTask task = new RunnerTask("dependent", "IMPLEMENTATION", "Implement", "org/repository", "main",
                null, "spec", "provider", 0, List.of("src/"), List.of(predecessorSha), null, null, null,
                List.of(), null, null, "main", predecessorSha, List.of(), List.of());

        Path worktree = RunnerMain.createExecutionWorktree(repository, task, temporaryDirectory.resolve("worktrees"));

        assertEquals(predecessorSha, git.headSha(worktree));
        assertEquals("committed predecessor", Files.readString(worktree.resolve("tests.txt")));
    }

    private static void run(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0) throw new IllegalStateException(output);
    }

}
