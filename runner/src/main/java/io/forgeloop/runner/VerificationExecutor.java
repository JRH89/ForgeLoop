package io.forgeloop.runner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Runs a policy-selected verification command without a shell inside one task worktree. */
public final class VerificationExecutor {
    private static final int MAX_OUTPUT_BYTES = 64 * 1024;

    public VerificationResult execute(Path worktree, List<String> command, Duration timeout) throws IOException, InterruptedException {
        if (!Files.exists(worktree.resolve(".git"))) throw new IllegalArgumentException("Verification must run in a task Git worktree");
        if (command == null || command.isEmpty() || command.stream().anyMatch(value -> value == null || value.isBlank())) throw new IllegalArgumentException("Verification command is incomplete");
        if (timeout == null || timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofHours(1)) > 0) throw new IllegalArgumentException("Verification timeout is outside policy bounds");
        Instant startedAt = Instant.now();
        Process process = new ProcessBuilder(command).directory(worktree.toFile()).redirectErrorStream(true).start();
        boolean completed = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
        if (!completed) process.destroyForcibly();
        byte[] output = process.getInputStream().readNBytes(MAX_OUTPUT_BYTES);
        return new VerificationResult(completed ? process.exitValue() : -1, !completed, new String(output, StandardCharsets.UTF_8), startedAt, Instant.now());
    }
}
