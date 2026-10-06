package io.forgeloop.runner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/** Only harmless child Java fixtures run here; no Docker or platform services are launched. */
class DesktopCommandExecutorTest {
    @TempDir Path directory;

    @Test void drainsLargeOutputWithoutPipeDeadlockAndRetainsOnlyDisplayLimit() throws Exception {
        var result = DesktopCommandExecutor.run(command("flood"), Duration.ofSeconds(10), () -> false);

        assertEquals(0, result.exitCode());
        assertEquals(8192, result.output().length());
        assertEquals("x".repeat(8192), result.output());
    }

    @Test void mergesStandardErrorAndRetainsNativeExitCode() throws Exception {
        var result = DesktopCommandExecutor.run(command("error"), Duration.ofSeconds(10), () -> false);

        assertEquals(17, result.exitCode());
        assertEquals("diagnostic from stderr", result.output());
    }

    @Test void preservesArgumentsContainingSpacesAndShellMetacharacters() throws Exception {
        List<String> arguments = List.of("white space", "semi;colon", "$(do-not-execute)",
                "`whoami`", "%PATH%", "&& never", "line\nbreak");
        List<String> invocation = command("echo");
        invocation.addAll(arguments);

        var result = DesktopCommandExecutor.run(invocation, Duration.ofSeconds(10), () -> false);

        assertEquals(0, result.exitCode());
        String expected = arguments.stream().map(argument -> Base64.getEncoder()
                .encodeToString(argument.getBytes(StandardCharsets.UTF_8))).reduce("", (text, encoded) -> text + encoded + "\n");
        assertEquals(expected, result.output());
    }

    @Test void embeddedQuotesArePreservedOnUnixOrRejectedBeforeLaunchOnWindows() throws Exception {
        String argument = "private\"native argument";
        if (DesktopDockerStartup.Platform.fromOsName(System.getProperty("os.name", ""))
                == DesktopDockerStartup.Platform.WINDOWS) {
            Path pid = directory.resolve("ambiguous-quote.pid");
            try {
                Exception failure = assertThrows(Exception.class, () -> DesktopCommandExecutor.run(
                        command("wait", pid.toString(), argument), Duration.ofSeconds(2), () -> false));
                assertTrue(failure instanceof IOException || failure instanceof IllegalArgumentException);
                assertFalse(failure.getMessage().contains(argument));
                assertFalse(Files.exists(pid), "Ambiguous Windows argument must be rejected before a process is created");
            } finally {
                stopFixture(pid);
            }
        } else {
            var result = DesktopCommandExecutor.run(command("echo", argument), Duration.ofSeconds(10), () -> false);
            assertEquals(0, result.exitCode());
            assertEquals(Base64.getEncoder().encodeToString(argument.getBytes(StandardCharsets.UTF_8)) + "\n", result.output());
        }
    }

    @Test void cancellationBeforeLaunchHasNoProcessSideEffect() {
        Path pid = directory.resolve("cancelled-before-launch.pid");

        assertThrows(CancellationException.class, () -> DesktopCommandExecutor.run(
                command("wait", pid.toString()), Duration.ofSeconds(10), () -> true));
        assertFalse(Files.exists(pid));
    }

    @Test void nonpositiveDeadlineHasNoProcessSideEffect() {
        Path pid = directory.resolve("invalid-timeout.pid");

        for (Duration timeout : List.of(Duration.ZERO, Duration.ofSeconds(-1))) {
            assertThrows(IOException.class, () -> DesktopCommandExecutor.run(command("wait", pid.toString()), timeout, () -> false));
        }
        assertFalse(Files.exists(pid));
    }

    @Test void timeoutTerminatesTheCommand() throws Exception {
        Path pid = directory.resolve("timed-out.pid");
        try {
            IOException failure = assertThrows(IOException.class, () -> DesktopCommandExecutor.run(
                    command("wait", pid.toString()), Duration.ofSeconds(2), () -> false));
            assertTrue(failure.getMessage().toLowerCase().contains("timed out"));
            await(() -> Files.exists(pid), "Fixture must have started before its deadline");
            assertChildStopped(pid);
        } finally {
            stopFixture(pid);
        }
    }

    @Test void cancellationDuringCommandTerminatesTheCommand() throws Exception {
        Path pid = directory.resolve("cancelled-during-command.pid");
        try {
            assertThrows(CancellationException.class, () -> DesktopCommandExecutor.run(
                    command("wait", pid.toString()), Duration.ofSeconds(10), () -> Files.exists(pid)));
            assertTrue(Files.exists(pid));
            assertChildStopped(pid);
        } finally {
            stopFixture(pid);
        }
    }

    @Test void interruptionTerminatesTheCommandAndPropagatesInterruptedException() throws Exception {
        Path pid = directory.resolve("interrupted.pid");
        AtomicReference<Throwable> outcome = new AtomicReference<>();
        Thread caller = Thread.ofPlatform().start(() -> {
            try {
                DesktopCommandExecutor.run(command("wait", pid.toString()), Duration.ofSeconds(15), () -> false);
            } catch (Throwable failure) {
                outcome.set(failure);
            }
        });
        try {
            await(() -> Files.exists(pid), "Child fixture must publish its identity");
            caller.interrupt();
            caller.join(5000);
            assertFalse(caller.isAlive(), "Interrupted caller must finish promptly");
            assertInstanceOf(InterruptedException.class, outcome.get());
            assertChildStopped(pid);
        } finally {
            caller.interrupt();
            stopFixture(pid);
        }
    }

    @Test void detachedLaunchReturnsWhileFixtureContinuesAndDoesNotBlockOnOutput() throws Exception {
        Path pid = directory.resolve("detached.pid");
        Path release = directory.resolve("release-detached");
        Path completed = directory.resolve("completed-detached");
        try {
            DesktopCommandExecutor.launch(command("detach", pid.toString(), release.toString(), completed.toString()));
            await(() -> Files.exists(pid), "Detached fixture must start and drain discarded output");
            assertTrue(fixtureAlive(pid));
            assertFalse(Files.exists(completed), "Launcher must return while the launched application remains running");
            Files.writeString(release, "finish");
            await(() -> Files.exists(completed), "Detached child must be able to finish after its output is discarded");
            assertChildStopped(pid);
        } finally {
            stopFixture(pid);
        }
    }

    @Test void emptyCommandIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> DesktopCommandExecutor.run(List.of(), Duration.ofSeconds(1), () -> false));
        assertThrows(IllegalArgumentException.class, () -> DesktopCommandExecutor.launch(List.of()));
    }

    private static List<String> command(String... arguments) {
        String java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java").toString();
        String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        var command = new ArrayList<>(List.of(java, "-cp", classPath, Fixture.class.getName()));
        command.addAll(List.of(arguments));
        return command;
    }

    private static void assertChildStopped(Path pid) throws Exception {
        long id = Long.parseLong(Files.readString(pid).trim());
        await(() -> !ProcessHandle.of(id).map(ProcessHandle::isAlive).orElse(false),
                "Timed-out or cancelled fixture must be terminated");
    }

    private static boolean fixtureAlive(Path pid) throws IOException {
        long id = Long.parseLong(Files.readString(pid).trim());
        return ProcessHandle.of(id).map(ProcessHandle::isAlive).orElse(false);
    }

    private static void stopFixture(Path pid) throws IOException {
        if (Files.exists(pid)) {
            long id = Long.parseLong(Files.readString(pid).trim());
            ProcessHandle.of(id).ifPresent(process -> { if (process.isAlive()) process.destroyForcibly(); });
        }
    }

    private static void await(java.util.function.BooleanSupplier condition, String reason) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(20);
        assertTrue(condition.getAsBoolean(), reason);
    }

    /** Child entry point deliberately performs only stdout, sleep, and temporary-file operations. */
    public static final class Fixture {
        public static void main(String[] arguments) throws Exception {
            switch (arguments[0]) {
                case "flood" -> flood();
                case "error" -> { System.err.print("diagnostic from stderr"); System.exit(17); }
                case "echo" -> {
                    for (int index = 1; index < arguments.length; index++) {
                        System.out.print(Base64.getEncoder().encodeToString(arguments[index].getBytes(StandardCharsets.UTF_8)) + "\n");
                    }
                }
                case "wait" -> { publishPid(Path.of(arguments[1])); Thread.sleep(Duration.ofSeconds(30)); }
                case "detach" -> {
                    flood();
                    publishPid(Path.of(arguments[1]));
                    Path release = Path.of(arguments[2]);
                    long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
                    while (!Files.exists(release) && System.nanoTime() < deadline) Thread.sleep(20);
                    Files.writeString(Path.of(arguments[3]), "complete");
                }
                default -> throw new IllegalArgumentException("Unknown harmless test fixture");
            }
        }

        private static void flood() throws IOException {
            byte[] block = "x".repeat(2048).getBytes(StandardCharsets.UTF_8);
            for (int index = 0; index < 256; index++) System.out.write(block);
            System.out.flush();
        }

        private static void publishPid(Path target) throws IOException {
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(temporary, Long.toString(ProcessHandle.current().pid()));
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        }
    }
}
