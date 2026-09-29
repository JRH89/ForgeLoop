package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the report mount and RED classification against Docker when a local daemon is available. */
class DockerTestCheckExecutionTest {
    private static final String IMAGE = "alpine:3.23";
    private static final String GATE_SCRIPT = "if [ -f /source/tests/NewTest.java ]; then "
            + "printf '%s' '<testsuite name=\"Suite\"><testcase name=\"existing\"/><testcase name=\"newBehavior\"><failure/></testcase></testsuite>' > /forgeloop/test-report/results.xml; exit 1; "
            + "else printf '%s' '<testsuite name=\"Suite\"><testcase name=\"existing\"/></testsuite>' > /forgeloop/test-report/results.xml; exit 0; fi";

    @TempDir Path temporaryDirectory;

    @Test
    void addedAssertionPassesAtParentAndFailsAtTestCommit() throws Exception {
        assumeTrue(dockerAvailable(), "A Docker daemon is required for this integration test");
        Path repository = temporaryDirectory.resolve("repository");
        Files.createDirectories(repository);
        run("git", "init", repository.toString());
        run("git", "-C", repository.toString(), "config", "user.email", "runner@example.test");
        run("git", "-C", repository.toString(), "config", "user.name", "ForgeLoop Runner");
        Files.writeString(repository.resolve("README.md"), "fixture\n");
        Files.writeString(repository.resolve("run-gate.sh"), GATE_SCRIPT);
        run("git", "-C", repository.toString(), "add", ".");
        run("git", "-C", repository.toString(), "commit", "-m", "base");

        GitWorktreeManager git = new GitWorktreeManager();
        String parentSha = git.headSha(repository);
        Path writer = git.create(repository, parentSha, "test-writer", temporaryDirectory.resolve("worktrees"));
        Files.createDirectories(writer.resolve("tests"));
        Files.writeString(writer.resolve("tests/NewTest.java"), "new behavior expectation\n");
        String targetSha = git.commit(writer, "test: add new behavior expectation");
        List<GitWorktreeManager.ChangedFile> changedFiles = git.changedFilesInCommit(repository, parentSha, targetSha);

        TestRunReport before = runGate(repository, git, parentSha, "before");
        TestRunReport after = runGate(repository, git, targetSha, "after");

        assertEquals(TestRunReport.Status.READ, before.status());
        assertEquals(TestRunReport.Outcome.PASSED, before.outcomes().get("Suite#existing"));
        assertFalse(before.outcomes().containsKey("Suite#newBehavior"));
        assertEquals(TestRunReport.Status.READ, after.status());
        assertEquals(TestRunReport.Outcome.PASSED, after.outcomes().get("Suite#existing"));
        assertEquals(TestRunReport.Outcome.FAILED, after.outcomes().get("Suite#newBehavior"));
        assertTrue(changedFiles.stream().anyMatch(file -> file.path().equals("tests/NewTest.java")));
    }

    private TestRunReport runGate(Path repository, GitWorktreeManager git, String sha, String label) throws Exception {
        String taskId = "check-" + label;
        Path workspace = temporaryDirectory.resolve("worktrees");
        Path worktree = git.create(repository, sha, taskId, workspace);
        Path evidence = temporaryDirectory.resolve("evidence").resolve(label);
        Files.createDirectories(evidence);
        try {
            VerificationResult result = new ContainerVerificationExecutor().execute(worktree, worktree,
                    evidence, evidence, IMAGE, List.of("sh", "/source/run-gate.sh"), Duration.ofSeconds(30), false, "JUNIT_XML");
            TestRunReport report = new JunitReportReader().read(evidence.resolve("test-report"));
            if (report.status() == TestRunReport.Status.MISSING) {
                throw new IllegalStateException("Docker did not produce the expected test report (exit "
                        + result.exitCode() + "): " + result.output());
            }
            return report;
        } finally {
            git.remove(repository, taskId, workspace);
        }
    }

    private static boolean dockerAvailable() {
        try {
            Process process = new ProcessBuilder("docker", "info", "--format", "{{.ServerVersion}}")
                    .redirectErrorStream(true).start();
            return process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception unavailable) {
            return false;
        }
    }

    private static void run(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0) throw new IllegalStateException(output);
    }
}
