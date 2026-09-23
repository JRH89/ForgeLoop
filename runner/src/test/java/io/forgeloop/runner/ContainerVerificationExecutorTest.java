package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

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

    private Path taskWorktree() throws Exception {
        Path worktree = temporaryDirectory.resolve("worktree");
        Files.createDirectories(worktree.resolve(".git"));
        return worktree;
    }
}
