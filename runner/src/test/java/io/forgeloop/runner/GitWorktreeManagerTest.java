package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitWorktreeManagerTest {
    @TempDir Path temporaryDirectory;

    @Test
    void rejectsUnsafeTaskIdentifierBeforeInvokingGit() {
        assertThrows(IllegalArgumentException.class,
                () -> new GitWorktreeManager().create(temporaryDirectory, "main", "../escape", temporaryDirectory.resolve("worktrees")));
    }

    @Test
    void commitsChangesInsideAnActualGitWorktree() throws Exception {
        run("git", "init", temporaryDirectory.toString());
        run("git", "-C", temporaryDirectory.toString(), "config", "user.email", "runner@example.test");
        run("git", "-C", temporaryDirectory.toString(), "config", "user.name", "ForgeLoop Runner");
        Files.writeString(temporaryDirectory.resolve("README.md"), "base");
        run("git", "-C", temporaryDirectory.toString(), "add", "."); run("git", "-C", temporaryDirectory.toString(), "commit", "-m", "base");
        Path worktree = new GitWorktreeManager().create(temporaryDirectory, "HEAD", "task-1", temporaryDirectory.resolve("worktrees"));
        Files.writeString(worktree.resolve("README.md"), "changed");
        assertFalse(new GitWorktreeManager().commit(worktree, "feat: task change").isBlank());
    }
    private static void run(String... command) throws Exception { Process process = new ProcessBuilder(command).redirectErrorStream(true).start(); if (process.waitFor() != 0) throw new IllegalStateException(new String(process.getInputStream().readAllBytes())); }
}
