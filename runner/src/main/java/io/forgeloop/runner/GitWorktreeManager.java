package io.forgeloop.runner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Creates a task-scoped detached Git worktree below a runner-controlled directory. */
public final class GitWorktreeManager {
    private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(2);

    public Path create(Path repository, String baseRef, String taskId, Path workspaceRoot) throws IOException, InterruptedException {
        if (!taskId.matches("[A-Za-z0-9_-]{1,80}")) throw new IllegalArgumentException("Task identifier is unsafe");
        if (!Files.isDirectory(repository.resolve(".git"))) throw new IllegalArgumentException("Repository must be a local Git worktree");
        Path root = workspaceRoot.toAbsolutePath().normalize();
        Path worktree = root.resolve(taskId).normalize();
        if (!worktree.startsWith(root)) throw new IllegalArgumentException("Worktree escapes runner workspace");
        if (Files.exists(worktree)) throw new IllegalStateException("Task worktree already exists");
        Files.createDirectories(root);
        run(repository, List.of("git", "worktree", "add", "--detach", worktree.toString(), baseRef));
        return worktree;
    }

    private void run(Path repository, List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).directory(repository.toFile()).redirectErrorStream(true).start();
        if (!process.waitFor(COMMAND_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IllegalStateException("Git worktree command timed out"); }
        if (process.exitValue() != 0) throw new IllegalStateException("Git worktree command failed");
    }
}
