package io.forgeloop.runner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Runs a verification command in a disposable Docker container.
 *
 * <p>The task worktree is mounted read-only. This prevents a verification step from modifying source
 * material after implementation has finished. Network access is disabled by default and must be
 * deliberately enabled by the policy that creates the request.</p>
 */
public final class ContainerVerificationExecutor {
    private static final int MAX_OUTPUT_BYTES = 64 * 1024;
    private static final Duration MAX_TIMEOUT = Duration.ofHours(1);
    private static final Pattern IMAGE_REFERENCE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/@:-]{0,254}");

    public VerificationResult execute(
            Path worktree,
            Path dockerVisibleWorktree,
            String image,
            List<String> command,
            Duration timeout,
            boolean allowNetwork) throws IOException, InterruptedException {
        validate(worktree, dockerVisibleWorktree, image, command, timeout);

        List<String> dockerCommand = new ArrayList<>(List.of(
                "docker", "run", "--rm", "--init", "--read-only",
                "--mount", "type=bind,src=" + dockerVisibleWorktree + ",dst=/workspace,readonly",
                "--workdir", "/workspace",
                "--tmpfs", "/tmp:rw,noexec,nosuid,size=64m"));
        if (!allowNetwork) {
            dockerCommand.addAll(List.of("--network", "none"));
        }
        dockerCommand.add(image);
        dockerCommand.addAll(command);

        Instant startedAt = Instant.now();
        Process process = new ProcessBuilder(dockerCommand).redirectErrorStream(true).start();
        boolean completed = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            process.waitFor();
        }
        byte[] output = process.getInputStream().readNBytes(MAX_OUTPUT_BYTES);
        return new VerificationResult(
                completed ? process.exitValue() : -1,
                !completed,
                new String(output, StandardCharsets.UTF_8),
                startedAt,
                Instant.now());
    }

    private void validate(Path worktree, Path dockerVisibleWorktree, String image, List<String> command, Duration timeout) throws IOException {
        if (worktree == null || !Files.exists(worktree.resolve(".git"))) {
            throw new IllegalArgumentException("Verification must run in a task Git worktree");
        }
        if (dockerVisibleWorktree == null || !dockerVisibleWorktree.isAbsolute()) {
            throw new IllegalArgumentException("Docker-visible task worktree path is required");
        }
        if (image == null || !IMAGE_REFERENCE.matcher(image).matches()) {
            throw new IllegalArgumentException("Container image reference is invalid");
        }
        if (command == null || command.isEmpty() || command.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Verification command is incomplete");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero() || timeout.compareTo(MAX_TIMEOUT) > 0) {
            throw new IllegalArgumentException("Verification timeout is outside policy bounds");
        }
    }
}
