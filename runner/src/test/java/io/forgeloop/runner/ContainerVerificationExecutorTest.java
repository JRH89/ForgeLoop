package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContainerVerificationExecutorTest {
    @TempDir Path temporaryDirectory;

    @Test
    void rejectsInvalidImageBeforeStartingDocker() throws Exception {
        Path worktree = taskWorktree();

        assertThrows(IllegalArgumentException.class,
                () -> new ContainerVerificationExecutor().execute(
                        worktree, worktree, "node:22; rm -rf /", List.of("node", "--version"), Duration.ofSeconds(1), false));
    }

    @Test
    void rejectsNonWorktreeBeforeStartingDocker() {
        assertThrows(IllegalArgumentException.class,
                () -> new ContainerVerificationExecutor().execute(
                        temporaryDirectory, temporaryDirectory, "node:22-alpine", List.of("node", "--version"), Duration.ofSeconds(1), false));
    }

    @Test
    void drainsVerboseProcessOutputWhileRetainingOnlyTheConfiguredPrefix() throws Exception {
        byte[] verboseOutput = new byte[256 * 1024];
        for (int index = 0; index < verboseOutput.length; index++) verboseOutput[index] = (byte) (index % 127);

        byte[] captured = ContainerVerificationExecutor.readBounded(new ByteArrayInputStream(verboseOutput), 64 * 1024);

        assertArrayEquals(java.util.Arrays.copyOf(verboseOutput, 64 * 1024), captured);
    }

    @Test
    void flagsWhetherBoundedOutputWasTruncated() throws Exception {
        var truncated = ContainerVerificationExecutor.readBoundedWithTruncation(
                new ByteArrayInputStream("abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8)), 3);
        var complete = ContainerVerificationExecutor.readBoundedWithTruncation(
                new ByteArrayInputStream("abc".getBytes(java.nio.charset.StandardCharsets.UTF_8)), 3);

        assertEquals("abc", new String(truncated.content(), java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(truncated.truncated());
        assertFalse(complete.truncated());
    }

    @Test
    void leavesOrdinaryVerificationDockerArgvUnchangedWithoutReports() {
        Path repo = Path.of("/repo");
        Path evidence = Path.of("/evidence");
        List<String> command = new ContainerVerificationExecutor().buildDockerCommand(repo, evidence,
                "node@sha256:" + "a".repeat(64), List.of("npm", "test"), false, null);

        assertEquals(List.of("docker", "run", "--rm", "--init", "--read-only",
                "--mount", "type=bind,src=" + repo + ",dst=/source,readonly",
                "--mount", "type=bind,src=" + evidence.resolve("test-results") + ",dst=/workspace/test-results",
                "--mount", "type=bind,src=" + evidence.resolve("playwright-report") + ",dst=/workspace/playwright-report",
                "--workdir", "/workspace", "--tmpfs", "/tmp:rw,noexec,nosuid,size=128m",
                "--tmpfs", "/workspace:rw,exec,nosuid,size=2g", "--env", "HOME=/tmp/home",
                "--env", "XDG_CACHE_HOME=/tmp/cache", "--env", "MAVEN_CONFIG=/tmp/m2",
                "--env", "MAVEN_OPTS=-Dmaven.repo.local=/workspace/.m2/repository -Djansi.tmpdir=/workspace/.tmp",
                "--env", "npm_config_cache=/tmp/npm", "--network", "none", "node@sha256:" + "a".repeat(64),
                "sh", "-c", "cp -a /source/. /workspace/ && mkdir -p /workspace/.tmp /workspace/.m2/repository && exec \"$@\"",
                "forgeloop-verify", "npm", "test"), command);
        assertFalse(command.contains("type=bind,src=" + evidence.resolve("test-report") + ",dst=/forgeloop/test-report"));
    }

    @Test
    void mountsDeclaredJunitReportsOutsideWritableWorkspace() {
        Path evidence = Path.of("/evidence");
        List<String> command = new ContainerVerificationExecutor().buildDockerCommand(Path.of("/repo"), evidence,
                "node@sha256:" + "a".repeat(64), List.of("pytest", "--junitxml=/forgeloop/test-report/pytest.xml"), false, "JUNIT_XML");

        assertTrue(command.contains("type=bind,src=" + evidence.resolve("test-report") + ",dst=/forgeloop/test-report"));
        assertTrue(command.contains("--network"));
    }

    private Path taskWorktree() throws Exception {
        Path worktree = temporaryDirectory.resolve("worktree");
        Files.createDirectories(worktree.resolve(".git"));
        return worktree;
    }
}
