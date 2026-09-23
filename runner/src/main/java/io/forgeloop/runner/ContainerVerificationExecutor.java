package io.forgeloop.runner;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
                "--mount", "type=bind,src=" + dockerVisibleWorktree + ",dst=/source,readonly",
                "--workdir", "/workspace",
                "--tmpfs", "/tmp:rw,noexec,nosuid,size=128m",
                "--tmpfs", "/workspace:rw,exec,nosuid,size=2g",
                "--env", "HOME=/tmp/home",
                "--env", "XDG_CACHE_HOME=/tmp/cache",
                "--env", "MAVEN_CONFIG=/tmp/m2",
                "--env", "MAVEN_OPTS=-Dmaven.repo.local=/workspace/.m2/repository -Djansi.tmpdir=/workspace/.tmp",
                "--env", "npm_config_cache=/tmp/npm"));
        if (!allowNetwork) {
            dockerCommand.addAll(List.of("--network", "none"));
        }
        dockerCommand.add(image);
        // The fixed bootstrap copies the read-only source into an ephemeral filesystem. Policy argv is
        // forwarded as positional arguments and is never interpolated into shell source.
        dockerCommand.addAll(List.of("sh", "-c", "cp -a /source/. /workspace/ && mkdir -p /workspace/.tmp /workspace/.m2/repository && exec \"$@\"", "forgeloop-verify"));
        dockerCommand.addAll(command);

        Instant startedAt = Instant.now();
        Process process = new ProcessBuilder(dockerCommand).redirectErrorStream(true).start();
        // Drain concurrently: package managers can exceed the OS pipe buffer long before exit.
        // Capturing remains bounded, while excess bytes are discarded instead of blocking Docker.
        CompletableFuture<byte[]> outputFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return readBounded(process.getInputStream(), MAX_OUTPUT_BYTES);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        });
        boolean completed = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            process.waitFor();
        }
        byte[] output;
        try {
            output = outputFuture.join();
        } catch (CompletionException failure) {
            if (failure.getCause() instanceof UncheckedIOException ioFailure) throw ioFailure.getCause();
            throw failure;
        }
        return new VerificationResult(
                completed ? process.exitValue() : -1,
                !completed,
                new String(output, StandardCharsets.UTF_8),
                startedAt,
                Instant.now());
    }

    static byte[] readBounded(InputStream input, int limit) throws IOException {
        if (limit < 1) throw new IllegalArgumentException("Output limit must be positive");
        byte[] captured = new byte[limit];
        byte[] buffer = new byte[8192];
        int capturedBytes = 0;
        for (int read; (read = input.read(buffer)) != -1; ) {
            int copy = Math.min(read, limit - capturedBytes);
            if (copy > 0) {
                System.arraycopy(buffer, 0, captured, capturedBytes, copy);
                capturedBytes += copy;
            }
        }
        return java.util.Arrays.copyOf(captured, capturedBytes);
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
