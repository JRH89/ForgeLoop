package io.forgeloop.runner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

/** Creates a task-scoped detached Git worktree below a runner-controlled directory. */
public final class GitWorktreeManager {
    private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(2);
    private static final List<String> FORGELOOP_IDENTITY = List.of("-c", "user.name=ForgeLoop", "-c", "user.email=runner@forgeloop.invalid");

    public Path create(Path repository, String baseRef, String taskId, Path workspaceRoot) throws IOException, InterruptedException {
        if (!taskId.matches("[A-Za-z0-9_-]{1,80}")) throw new IllegalArgumentException("Task identifier is unsafe");
        if (!Files.exists(repository.resolve(".git"))) throw new IllegalArgumentException("Repository must be a local Git worktree");
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
        if (!Files.exists(repository.resolve(".git"))) throw new IllegalArgumentException("Repository must be a local Git worktree");
        Path root = workspaceRoot.toAbsolutePath().normalize();
        Path worktree = root.resolve(taskId).normalize();
        if (!worktree.startsWith(root)) throw new IllegalArgumentException("Worktree escapes runner workspace");
        if (!Files.exists(worktree)) throw new IllegalArgumentException("Task worktree does not exist");
        run(repository, List.of("git", "worktree", "remove", "--force", worktree.toString()));
    }

    /** Commits an already policy-validated worktree without invoking a shell. */
    public String commit(Path worktree, String message) throws IOException, InterruptedException {
        if (!Files.exists(worktree.resolve(".git")) || message == null || message.isBlank() || message.length() > 200) throw new IllegalArgumentException("Git commit request is invalid");
        run(worktree, List.of("git", "add", "--all"));
        run(worktree, gitWithIdentity("commit", "--no-verify", "-m", message));
        return output(worktree, List.of("git", "rev-parse", "HEAD"));
    }

    /** Integrates only server-declared commit identities, without invoking a shell. */
    public String integrate(Path worktree, List<String> commitShas) throws IOException, InterruptedException {
        if (!Files.exists(worktree.resolve(".git")) || commitShas == null || commitShas.isEmpty()
                || commitShas.stream().anyMatch(sha -> sha == null || !sha.matches("[0-9a-f]{40,64}"))) {
            throw new IllegalArgumentException("Git integration request is invalid");
        }
        try {
            for (String sha : commitShas) run(worktree, gitWithIdentity("cherry-pick", sha));
        } catch (RuntimeException | IOException | InterruptedException failure) {
            try { run(worktree, List.of("git", "cherry-pick", "--abort")); } catch (Exception ignored) { /* Preserve the original conflict. */ }
            throw failure;
        }
        return output(worktree, List.of("git", "rev-parse", "HEAD"));
    }
    /** Pins a detached task commit so cleanup cannot make it unreachable before integration. */
    public void pinTaskCommit(Path worktree,String taskId,String sha)throws IOException,InterruptedException{if(taskId==null||!taskId.matches("[A-Za-z0-9_-]{1,80}")||sha==null||!sha.matches("[0-9a-f]{40,64}"))throw new IllegalArgumentException("Task commit pin is invalid");run(worktree,List.of("git","update-ref","refs/forgeloop/tasks/"+taskId,sha));}

    /** Pushes one exact integrated commit using an environment-only short-lived GitHub credential. */
    public void pushIntegrated(Path worktree, String repository, String branch, String expectedHeadSha, String sha, String token) throws IOException, InterruptedException {
        if (!Files.exists(worktree.resolve(".git")) || repository == null || !repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
                || branch == null || !branch.matches("forgeloop/[A-Za-z0-9_-]{1,80}")
                || (expectedHeadSha != null && !expectedHeadSha.matches("[0-9a-f]{40,64}"))
                || sha == null || !sha.matches("[0-9a-f]{40,64}") || token == null || token.isBlank()) {
            throw new IllegalArgumentException("GitHub push request is invalid");
        }
        // An empty expected value asserts that the branch does not exist. Retries must match the previously recorded head.
        String lease = "--force-with-lease=refs/heads/" + branch + ":" + (expectedHeadSha == null ? "" : expectedHeadSha);
        ProcessBuilder builder = new ProcessBuilder("git", "-c", "safe.directory=" + worktree.toAbsolutePath().normalize(), "push",
                lease, "https://github.com/" + repository + ".git", sha + ":refs/heads/" + branch)
                .directory(worktree.toFile()).redirectErrorStream(true);
        String authorization = Base64.getEncoder().encodeToString(("x-access-token:" + token).getBytes(StandardCharsets.UTF_8));
        builder.environment().put("GIT_CONFIG_COUNT", "1"); builder.environment().put("GIT_CONFIG_KEY_0", "http.https://github.com/.extraheader"); builder.environment().put("GIT_CONFIG_VALUE_0", "AUTHORIZATION: basic " + authorization);
        Process process = builder.start();
        if (!process.waitFor(COMMAND_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IllegalStateException("GitHub push timed out"); }
        String output = new String(process.getInputStream().readNBytes(4096), StandardCharsets.UTF_8).strip();
        if (process.exitValue() != 0) throw new IllegalStateException("GitHub push failed: " + output.replaceAll("gh[opsu]_[A-Za-z0-9_]+", "[REDACTED]"));
    }

    /** Returns a bounded textual diff for a read-only review worker. */
    public String boundedDiff(Path worktree, String baseRef) throws IOException, InterruptedException {
        if (!Files.exists(worktree.resolve(".git")) || baseRef == null || baseRef.isBlank()) throw new IllegalArgumentException("Review diff request is invalid");
        return output(worktree, List.of("git", "diff", "--no-ext-diff", "--unified=3", baseRef + "...HEAD"), 48 * 1024);
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
    private String output(Path repository, List<String> command) throws IOException, InterruptedException { return output(repository,command,4096); }
    private String output(Path repository, List<String> command, int limit) throws IOException, InterruptedException {
        List<String> safe = new ArrayList<>(List.of("git", "-c", "safe.directory=" + repository.toAbsolutePath().normalize())); safe.addAll(command.subList(1, command.size()));
        Process process = new ProcessBuilder(safe).directory(repository.toFile()).redirectErrorStream(true).start();
        if (!process.waitFor(COMMAND_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IllegalStateException("Git command timed out"); }
        String value = new String(process.getInputStream().readNBytes(limit), java.nio.charset.StandardCharsets.UTF_8).strip(); if (process.exitValue() != 0) throw new IllegalStateException("Git command failed: " + value); return value;
    }
    private static List<String> gitWithIdentity(String... arguments) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(FORGELOOP_IDENTITY);
        command.addAll(List.of(arguments));
        return command;
    }
}
