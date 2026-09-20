package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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

    private Path taskWorktree() throws Exception {
        Path worktree = temporaryDirectory.resolve("worktree");
        Files.createDirectories(worktree.resolve(".git"));
        return worktree;
    }
}
