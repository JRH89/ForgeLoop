package io.forgeloop.runner;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Direct, bounded native commands: no shell, password input, or unbounded output buffer. */
final class DesktopCommandExecutor {
    private static final int OUTPUT_LIMIT = 8192;

    private DesktopCommandExecutor() { }

    static DesktopPrerequisites.CommandResult run(List<String> command, Duration timeout,
                                                  BooleanSupplier cancelled)
            throws IOException, InterruptedException {
        if (timeout.isZero() || timeout.isNegative()) throw new IOException("Command timed out");
        if (cancelled.getAsBoolean()) throw new CancellationException();
        Process process = builder(command).redirectErrorStream(true).start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        // Drain while waiting, even after reaching the display limit, so a full pipe cannot deadlock startup.
        Thread reader = Thread.ofVirtual().start(() -> {
            try (var stream = process.getInputStream()) {
                byte[] buffer = new byte[2048];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    synchronized (output) {
                        output.write(buffer, 0, Math.min(count, OUTPUT_LIMIT - output.size()));
                    }
                }
            } catch (IOException closed) {
                // Killing a cancelled/timed-out command closes its stream; raw native errors stay private.
            }
        });
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            process.getOutputStream().close();
            while (true) {
                if (cancelled.getAsBoolean()) throw new CancellationException();
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new IOException("Command timed out");
                if (process.waitFor(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(200)), TimeUnit.NANOSECONDS)) break;
            }
            if (cancelled.getAsBoolean()) throw new CancellationException();
            reader.join(1000);
            synchronized (output) {
                return new DesktopPrerequisites.CommandResult(process.exitValue(), output.toString(StandardCharsets.UTF_8));
            }
        } finally {
            // Do not kill descendants: cancelling startup stops the wait, not an already launched Docker engine.
            if (process.isAlive()) process.destroyForcibly();
            reader.interrupt();
        }
    }

    /** GUI launchers outlive this action; discarding pipes prevents them blocking the runner. */
    static void launch(List<String> command) throws IOException {
        Process process = builder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        process.getOutputStream().close();
    }

    private static ProcessBuilder builder(List<String> command) {
        if (command.isEmpty()) throw new IllegalArgumentException("A native command is required");
        // Windows' legacy Java command-line serialization does not preserve embedded double quotes.
        // None of these fixed native commands require them; fail closed rather than merge arguments.
        if (com.sun.jna.Platform.isWindows() && command.stream().anyMatch(argument -> argument.indexOf('"') >= 0)) {
            throw new IllegalArgumentException("A native command argument contains unsupported quoting. Start Docker manually instead.");
        }
        List<String> resolved = new ArrayList<>(command);
        if (!Path.of(command.getFirst()).isAbsolute()) resolved.set(0, DesktopToolPaths.executable(command.getFirst()));
        ProcessBuilder builder = new ProcessBuilder(resolved);
        builder.environment().put("PATH", DesktopToolPaths.searchPath());
        // Native service/daemon diagnostics are classified by stable English phrases, never shown raw.
        builder.environment().put("LC_ALL", "C");
        return builder;
    }
}
