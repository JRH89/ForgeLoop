package io.forgeloop.runner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
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

    /** Removes only a task worktree located underneath the runner-controlled workspace root. */
    public void remove(Path repository, String taskId, Path workspaceRoot) throws IOException, InterruptedException {
        if (!taskId.matches("[A-Za-z0-9_-]{1,80}")) throw new IllegalArgumentException("Task identifier is unsafe");
        if (!Files.isDirectory(repository.resolve(".git"))) throw new IllegalArgumentException("Repository must be a local Git worktree");
        Path root = workspaceRoot.toAbsolutePath().normalize();
        Path worktree = root.resolve(taskId).normalize();
        if (!worktree.startsWith(root)) throw new IllegalArgumentException("Worktree escapes runner workspace");
        if (!Files.exists(worktree)) throw new IllegalArgumentException("Task worktree does not exist");
        run(repository, List.of("git", "worktree", "remove", "--force", worktree.toString()));
    }

    private void run(Path repository, List<String> command) throws IOException, InterruptedException {
        List<String> safeCommand = new ArrayList<>();
        safeCommand.add("git");
        safeCommand.add("-c");
        safeCommand.add("safe.directory=" + repository.toAbsolutePath().normalize());
        safeCommand.addAll(command.subList(1, command.size()));
        Process process = new ProcessBuilder(safeCommand).directory(repository.toFile()).redirectErrorStream(true).start();
        if (!process.waitFor(COMMAND_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IllegalStateException("Git worktree command timed out"); }
        String output = new String(process.getInputStream().readNBytes(4096), java.nio.charset.StandardCharsets.UTF_8).strip();
        if (process.exitValue() != 0) throw new IllegalStateException("Git worktree command failed: " + output);
    }
}
