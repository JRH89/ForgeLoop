package io.forgeloop.runner;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises startup decisions without launching Docker or changing a host service. */
class DesktopDockerStartupTest {
    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final String ENGINE_FORMAT = "{{.OSType}}";
    private static final BooleanSupplier NOT_CANCELLED = () -> false;

    @Test void runningEnginePinsConnectionWithoutLaunchingOrPolling() {
        FakeHost host = new FakeHost();
        host.initial = ready();
        host.endpoint = "npipe:////./pipe/docker_engine";

        var prepared = DesktopDockerStartup.prepareConnection(host, windows(), WAIT, NOT_CANCELLED, message -> { });

        assertTrue(prepared.prerequisites().ready());
        assertEquals(Map.of("DOCKER_CONTEXT", "default", "DOCKER_HOST", ""), prepared.dockerEnvironment());
        assertTrue(host.commands.stream().allMatch(command -> command.contains("context")
                && (command.contains("show") || command.contains("inspect"))));
        assertFalse(host.hasProbe());
        assertTrue(host.launches.isEmpty());
        assertEquals(0, host.sleeps);
    }

    @Test void missingDependenciesOrUnsafePrerequisiteStateDoesNotStartDocker() {
        for (DesktopPrerequisites.State state : List.of(
                DesktopPrerequisites.State.MISSING,
                DesktopPrerequisites.State.WRONG_CONTAINER_MODE,
                DesktopPrerequisites.State.CHECK_FAILED,
                DesktopPrerequisites.State.ACCESS_DENIED)) {
            FakeHost host = new FakeHost();
            host.initial = report(DesktopPrerequisites.State.READY, state);
            assertEquals(state, prepare(host, windows()).docker().state());
            assertTrue(host.commands.isEmpty(), state + " must not invoke startup commands");
            assertTrue(host.launches.isEmpty());
        }
        FakeHost host = new FakeHost();
        host.initial = report(DesktopPrerequisites.State.MISSING, DesktopPrerequisites.State.NOT_RUNNING);
        assertFalse(prepare(host, windows()).ready());
        assertTrue(host.commands.isEmpty());
        assertTrue(host.launches.isEmpty());
    }

    @Test void dockerDesktopCommandStartsWindowsEngineBeforeNativeFallback() {
        FakeHost host = new FakeHost();
        host.endpoint = "npipe:////./pipe/docker_engine";
        host.desktopCommand = result(0, "starting");

        assertTrue(prepare(host, windows()).ready());
        assertTrue(host.commands.contains(List.of("docker", "desktop", "start", "--detach")));
        assertTrue(host.launches.isEmpty());
        assertTrue(host.commands.contains(List.of("docker", "--context", "default", "info", "--format", ENGINE_FORMAT)));
    }

    @Test void olderWindowsDesktopIsLaunchedFromKnownInstallationAndPolled() {
        FakeHost host = new FakeHost();
        host.endpoint = "npipe:////./pipe/docker_engine";
        host.probes.add(result(1, "daemon unavailable"));
        host.probes.add(result(0, "linux\n"));
        List<String> progress = new ArrayList<>();

        assertTrue(DesktopDockerStartup.prepare(host, windows(), WAIT, NOT_CANCELLED, progress::add).ready());
        assertEquals(1, host.launches.size());
        assertTrue(normalize(host.launches.getFirst().getFirst()).endsWith("/Docker/Docker/Docker Desktop.exe"));
        assertEquals(1, host.sleeps);
        assertFalse(progress.isEmpty());
    }

    @Test void macDesktopFallbackUsesApplicationLauncher() {
        FakeHost host = new FakeHost();
        host.endpoint = "unix:///Users/runner/.docker/run/docker.sock";

        assertTrue(prepare(host, environment(DesktopDockerStartup.Platform.MACOS, "/Users/runner", Map.of())).ready());
        assertEquals(List.of(List.of("/usr/bin/open", "-a", "Docker")), host.launches);
    }

    @Test void macDefaultSocketAlsoStartsDesktop() {
        FakeHost host = new FakeHost();
        host.endpoint = "unix:///var/run/docker.sock";

        assertTrue(prepare(host, environment(DesktopDockerStartup.Platform.MACOS, "/Users/runner", Map.of())).ready());
        assertEquals(List.of(List.of("/usr/bin/open", "-a", "Docker")), host.launches);
    }

    @Test void linuxDesktopContextStartsUserDesktopService() {
        FakeHost host = new FakeHost();
        host.endpoint = "unix:///home/runner/.docker/desktop/docker.sock";

        assertTrue(prepare(host, linux()).ready());
        assertTrue(host.commands.stream().anyMatch(command -> command.contains("--user") && command.contains("docker-desktop.service")));
        assertFalse(host.commands.stream().anyMatch(command -> command.contains("docker.service")));
    }

    @Test void linuxRootlessSocketStartsOnlyUserEngineService() {
        FakeHost host = new FakeHost();
        host.endpoint = "unix:///run/user/1000/docker.sock";

        assertTrue(prepare(host, linux()).ready());
        assertTrue(host.commands.stream().anyMatch(command -> command.contains("--user") && command.contains("docker.service")));
        assertFalse(host.commands.stream().anyMatch(command -> command.contains("--no-ask-password")));
        assertFalse(host.commands.stream().anyMatch(command -> command.getFirst().endsWith("pkexec")));
    }

    @Test void rootlessSocketForAnotherUserNeverStartsAService() {
        FakeHost host = new FakeHost();
        host.endpoint = "unix:///run/user/1000/docker.sock";
        host.uid = "2000";

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> prepare(host, linux()));
        assertTrue(failure.getMessage().contains("another user"));
        assertTrue(host.commands.contains(List.of("/usr/bin/id", "-u")));
        assertFalse(host.commands.stream().anyMatch(command -> command.contains("start")));
        assertTrue(host.launches.isEmpty());
    }

    @Test void linuxNativeEngineStartsSystemServiceWithoutTerminalPasswordPrompt() {
        FakeHost host = new FakeHost();

        assertTrue(prepare(host, linux()).ready());
        assertTrue(host.commands.contains(List.of("/usr/bin/systemctl", "--no-ask-password", "--no-block", "start", "docker.service")));
        assertFalse(host.commands.stream().anyMatch(command -> command.contains("--user")));
        assertFalse(host.commands.stream().anyMatch(command -> command.getFirst().endsWith("pkexec")));
    }

    @Test void linuxSystemPermissionFailureCanUseNativeAuthenticationAgent() {
        FakeHost host = new FakeHost();
        host.systemService = result(1, "Interactive authentication required.");

        assertTrue(prepare(host, linux()).ready());
        assertTrue(host.commands.contains(List.of("/usr/bin/pkexec", "--disable-internal-agent", "/usr/bin/systemctl", "--no-block", "start", "docker.service")));
    }

    @Test void cancelledLinuxAuthenticationStopsBeforeEnginePolling() {
        FakeHost host = new FakeHost();
        host.systemService = result(1, "Interactive authentication required.");
        host.privilegedService = result(126, "Authentication cancelled");

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> prepare(host, linux()));
        assertTrue(failure.getMessage().toLowerCase().contains("cancel"));
        assertFalse(host.hasProbe());
    }

    @Test void explicitContextOverridesHostEnvironmentAndPinsReadinessProbe() {
        FakeHost host = new FakeHost();
        host.contexts.put("selected", "unix:///var/run/docker.sock");
        var environment = environment(DesktopDockerStartup.Platform.LINUX, "/home/runner", Map.of(
                "DOCKER_CONTEXT", "selected", "DOCKER_HOST", "ssh://remote.example"));

        assertTrue(prepare(host, environment).ready());
        assertTrue(host.commands.stream().anyMatch(command -> command.contains("inspect") && command.contains("selected")));
        assertTrue(host.commands.contains(List.of("docker", "--context", "selected", "info", "--format", ENGINE_FORMAT)));
        assertFalse(host.commands.stream().anyMatch(command -> command.contains("--host")));
    }

    @Test void directLocalDockerHostPinsReadinessProbeWithoutInspectingContext() {
        FakeHost host = new FakeHost();
        var environment = environment(DesktopDockerStartup.Platform.LINUX, "/home/runner",
                Map.of("DOCKER_HOST", "unix:///run/docker.sock"));

        assertTrue(prepare(host, environment).ready());
        assertTrue(host.commands.contains(List.of("docker", "--host", "unix:///run/docker.sock", "info", "--format", ENGINE_FORMAT)));
        assertFalse(host.commands.stream().anyMatch(command -> command.contains("context") && command.contains("inspect")));
    }

    @Test void preparedWorkerPinsSelectedContextAndClearsConflictingHostOverride() {
        FakeHost host = new FakeHost();
        host.contexts.put("selected", "unix:///var/run/docker.sock");
        var environment = environment(DesktopDockerStartup.Platform.LINUX, "/home/runner", Map.of(
                "DOCKER_CONTEXT", "selected", "DOCKER_HOST", "ssh://remote.example"));

        var prepared = DesktopDockerStartup.prepareConnection(host, environment, WAIT, NOT_CANCELLED, message -> { });

        assertTrue(prepared.prerequisites().ready());
        assertEquals(Map.of("DOCKER_CONTEXT", "selected", "DOCKER_HOST", ""), prepared.dockerEnvironment());
    }

    @Test void preparedWorkerPinsDirectHostAndClearsInheritedContext() {
        FakeHost host = new FakeHost();
        var environment = environment(DesktopDockerStartup.Platform.LINUX, "/home/runner",
                Map.of("DOCKER_HOST", "unix:///run/docker.sock"));

        var prepared = DesktopDockerStartup.prepareConnection(host, environment, WAIT, NOT_CANCELLED, message -> { });

        assertTrue(prepared.prerequisites().ready());
        assertEquals(Map.of("DOCKER_CONTEXT", "", "DOCKER_HOST", "unix:///run/docker.sock"), prepared.dockerEnvironment());
    }

    @Test void remoteAndUnknownSocketsNeverLaunchLocalEngine() {
        for (String endpoint : List.of("ssh://user@remote.example", "tcp://remote.example:2376",
                "tcp://127.0.0.1:2375", "unix:///tmp/another-engine.sock")) {
            FakeHost host = new FakeHost();
            host.endpoint = endpoint;

            assertThrows(IllegalStateException.class, () -> prepare(host, linux()), endpoint);
            assertTrue(host.launches.isEmpty());
            assertFalse(host.commands.stream().anyMatch(command -> command.contains("start")), endpoint);
            assertFalse(host.hasProbe());
        }
    }

    @Test void contextInspectionFailureDoesNotGuessWhichLocalEngineToStart() {
        FakeHost host = new FakeHost();
        host.contextInspection = result(1, "private context details");

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> prepare(host, linux()));
        assertFalse(failure.getMessage().contains("private context details"));
        assertTrue(host.launches.isEmpty());
        assertFalse(host.commands.stream().anyMatch(command -> command.contains("start")));
    }

    @Test void boundedWaitTimesOutWithoutReportingReady() {
        FakeHost host = new FakeHost();
        host.defaultProbe = result(1, "daemon unavailable");

        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                DesktopDockerStartup.prepare(host, linux(), Duration.ofSeconds(5), NOT_CANCELLED, message -> { }));
        assertTrue(failure.getMessage().toLowerCase().contains("tim") || failure.getMessage().toLowerCase().contains("ready"));
        assertTrue(host.now <= Duration.ofSeconds(7).toNanos(), "Polling must obey a bounded deadline");
        assertTrue(host.sleeps > 0);
        assertEquals(1, host.checks, "Timeout must not perform the final successful prerequisite check");
    }

    @Test void cancellationBeforeStartingDoesNotLaunchOrProbe() {
        FakeHost host = new FakeHost();

        assertThrows(IllegalStateException.class, () -> DesktopDockerStartup.prepare(host, linux(), WAIT, () -> true, message -> { }));
        assertTrue(host.launches.isEmpty());
        assertFalse(host.commands.stream().anyMatch(command -> command.contains("start")));
        assertFalse(host.hasProbe());
    }

    @Test void cancellationDuringPollingPreventsFinalReadyReport() {
        FakeHost host = new FakeHost();
        AtomicBoolean cancelled = new AtomicBoolean();
        host.defaultProbe = result(1, "daemon unavailable");
        host.afterSleep = () -> cancelled.set(true);

        assertThrows(IllegalStateException.class, () -> DesktopDockerStartup.prepare(host, linux(), WAIT, cancelled::get, message -> { }));
        assertEquals(1, host.sleeps);
        assertEquals(1, host.checks);
    }

    @Test void cancellationDuringInitialPrerequisiteCheckPreventsDockerLaunch() {
        FakeHost host = new FakeHost();
        AtomicBoolean cancelled = new AtomicBoolean();
        host.afterCheck = count -> cancelled.set(true);

        assertThrows(IllegalStateException.class, () ->
                DesktopDockerStartup.prepare(host, linux(), WAIT, cancelled::get, message -> { }));
        assertTrue(host.commands.isEmpty());
        assertTrue(host.launches.isEmpty());
        assertEquals(1, host.checks);
        assertEquals(1, host.checkTimeouts.size());
    }

    @Test void cancellationDuringFinalPrerequisiteCheckPreventsReadyReport() {
        FakeHost host = new FakeHost();
        AtomicBoolean cancelled = new AtomicBoolean();
        host.afterCheck = count -> { if (count == 2) cancelled.set(true); };

        assertThrows(IllegalStateException.class, () ->
                DesktopDockerStartup.prepareConnection(host, linux(), WAIT, cancelled::get, message -> { }));
        assertEquals(2, host.checks);
        assertEquals(2, host.checkTimeouts.size());
    }

    @Test void initialPrerequisiteChecksConsumeTheSameOverallDeadline() {
        FakeHost host = new FakeHost();
        host.initialCheckElapsed = WAIT.plusSeconds(1);

        assertThrows(IllegalStateException.class, () -> prepare(host, linux()));
        assertTrue(host.commands.isEmpty());
        assertEquals(WAIT, host.checkTimeouts.getFirst());
    }

    @Test void finalPrerequisiteCheckCannotReturnReadyAfterOverallDeadline() {
        FakeHost host = new FakeHost();
        host.finalCheckElapsed = WAIT.plusSeconds(1);

        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                DesktopDockerStartup.prepareConnection(host, linux(), WAIT, NOT_CANCELLED, message -> { }));
        assertTrue(failure.getMessage().toLowerCase().contains("timeout"));
        assertEquals(2, host.checks);
    }

    @Test void interruptedPollingPreservesInterruptAndNeverReturnsReady() {
        FakeHost host = new FakeHost();
        host.defaultProbe = result(1, "daemon unavailable");
        host.interruptSleep = true;

        try {
            assertThrows(IllegalStateException.class, () -> prepare(host, linux()));
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, host.checks);
        } finally {
            Thread.interrupted();
        }
    }

    @Test void enginePermissionDenialDuringPollingDoesNotKeepRetrying() {
        FakeHost host = new FakeHost();
        host.defaultProbe = result(1, "permission denied while trying to connect to the Docker daemon socket");

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> prepare(host, linux()));
        assertTrue(failure.getMessage().toLowerCase().contains("permission") || failure.getMessage().toLowerCase().contains("access"));
        assertEquals(0, host.sleeps);
        assertEquals(1, host.checks);
    }

    @Test void windowsContainersAfterStartupAreNotAccepted() {
        FakeHost host = new FakeHost();
        host.endpoint = "npipe:////./pipe/docker_engine";
        host.defaultProbe = result(0, "windows\n");

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> prepare(host, windows()));
        assertTrue(failure.getMessage().contains("Linux"));
        assertFalse(host.commands.stream().anyMatch(command -> command.contains("--switch-linux")));
        assertEquals(1, host.checks);
    }

    @Test void desktopContextSwitchIsRestoredBeforeFinalPrerequisiteCheck() {
        FakeHost host = new FakeHost();
        host.endpoint = "npipe:////./pipe/docker_engine";
        host.desktopCommand = result(0, "started");
        host.activeContextAfterStart = "desktop-linux";

        assertTrue(prepare(host, windows()).ready());
        assertTrue(host.commands.contains(List.of("docker", "context", "use", "default")));
        assertEquals("default", host.activeContext);
        assertEquals(2, host.checks);
    }

    @Test void unexpectedContextSwitchFailsWithoutOverwritingUserSelection() {
        FakeHost host = new FakeHost();
        host.endpoint = "npipe:////./pipe/docker_engine";
        host.desktopCommand = result(0, "started");
        host.activeContextAfterStart = "unrelated-user-context";

        assertThrows(IllegalStateException.class, () -> prepare(host, windows()));
        assertFalse(host.commands.stream().anyMatch(command -> command.contains("context") && command.contains("use")));
        assertEquals("unrelated-user-context", host.activeContext);
        assertEquals(1, host.checks);
    }

    @Test void nativeLauncherFailureDoesNotExposeRawOutputOrReportReady() {
        FakeHost host = new FakeHost();
        host.endpoint = "unix:///Users/runner/.docker/run/docker.sock";
        host.launchFailure = new IOException("raw private launcher information");

        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                prepare(host, environment(DesktopDockerStartup.Platform.MACOS, "/Users/runner", Map.of())));
        assertFalse(failure.getMessage().contains("raw private launcher information"));
        assertFalse(host.hasProbe());
    }

    @Test void finalPrerequisiteCheckCanStillPreventRunnerStart() {
        FakeHost host = new FakeHost();
        host.finalReport = report(DesktopPrerequisites.State.MISSING, DesktopPrerequisites.State.READY);

        assertFalse(prepare(host, linux()).ready());
        assertEquals(2, host.checks);
    }

    @Test void darwinIsMacRatherThanWindows() {
        assertEquals(DesktopDockerStartup.Platform.MACOS, DesktopDockerStartup.Platform.fromOsName("Darwin"));
        assertEquals(DesktopDockerStartup.Platform.MACOS, DesktopDockerStartup.Platform.fromOsName("Mac OS X"));
        assertEquals(DesktopDockerStartup.Platform.WINDOWS, DesktopDockerStartup.Platform.fromOsName("Windows 11"));
        assertEquals(DesktopDockerStartup.Platform.LINUX, DesktopDockerStartup.Platform.fromOsName("Linux"));
    }

    private static DesktopPrerequisites.Report prepare(FakeHost host, DesktopDockerStartup.Environment environment) {
        return DesktopDockerStartup.prepare(host, environment, WAIT, NOT_CANCELLED, message -> { });
    }

    private static DesktopDockerStartup.Environment windows() {
        return environment(DesktopDockerStartup.Platform.WINDOWS, "C:/Users/runner", Map.of(
                "ProgramFiles", "C:/Program Files", "LOCALAPPDATA", "C:/Users/runner/AppData/Local"));
    }

    private static DesktopDockerStartup.Environment linux() {
        return environment(DesktopDockerStartup.Platform.LINUX, "/home/runner", Map.of());
    }

    private static DesktopDockerStartup.Environment environment(DesktopDockerStartup.Platform platform, String home, Map<String, String> variables) {
        return new DesktopDockerStartup.Environment(platform, home, variables);
    }

    private static DesktopPrerequisites.Report ready() {
        return report(DesktopPrerequisites.State.READY, DesktopPrerequisites.State.READY);
    }

    private static DesktopPrerequisites.Report report(DesktopPrerequisites.State git, DesktopPrerequisites.State docker) {
        return new DesktopPrerequisites.Report(
                new DesktopPrerequisites.Check("Git", git, "Git " + git, URI.create("https://git-scm.com/downloads")),
                new DesktopPrerequisites.Check("Docker", docker, "Docker " + docker, URI.create("https://docs.docker.com/desktop/")));
    }

    private static DesktopPrerequisites.CommandResult result(int code, String output) {
        return new DesktopPrerequisites.CommandResult(code, output);
    }

    private static String normalize(String path) {
        return path.replace('\\', '/');
    }

    /** All native effects and elapsed time are deterministic, so these tests remain offline. */
    private static final class FakeHost implements DesktopDockerStartup.Host {
        final List<List<String>> commands = new ArrayList<>();
        final List<List<String>> launches = new ArrayList<>();
        final List<Duration> checkTimeouts = new ArrayList<>();
        final Deque<DesktopPrerequisites.CommandResult> probes = new ArrayDeque<>();
        final Map<String, String> contexts = new HashMap<>();
        DesktopPrerequisites.Report initial = report(DesktopPrerequisites.State.READY, DesktopPrerequisites.State.NOT_RUNNING);
        DesktopPrerequisites.Report finalReport = ready();
        DesktopPrerequisites.CommandResult desktopCommand = result(1, "unknown command desktop");
        DesktopPrerequisites.CommandResult defaultProbe = result(0, "linux\n");
        DesktopPrerequisites.CommandResult systemService = result(0, "");
        DesktopPrerequisites.CommandResult privilegedService = result(0, "");
        DesktopPrerequisites.CommandResult contextInspection;
        String endpoint = "unix:///var/run/docker.sock";
        String activeContext = "default";
        String activeContextAfterStart;
        String uid = "1000";
        IOException launchFailure;
        Runnable afterSleep = () -> { };
        IntConsumer afterCheck = count -> { };
        Duration initialCheckElapsed = Duration.ZERO;
        Duration finalCheckElapsed = Duration.ZERO;
        boolean interruptSleep;
        int checks;
        int sleeps;
        long now;

        @Override public DesktopPrerequisites.Report check() {
            return checks++ == 0 ? initial : finalReport;
        }

        @Override public DesktopPrerequisites.Report check(Duration timeout, BooleanSupplier cancelled) {
            assertTrue(timeout.isPositive());
            checkTimeouts.add(timeout);
            var report = check();
            now += (checks == 1 ? initialCheckElapsed : finalCheckElapsed).toNanos();
            afterCheck.accept(checks);
            if (cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException();
            return report;
        }

        @Override public DesktopPrerequisites.CommandResult run(List<String> command, Duration timeout, BooleanSupplier cancelled) {
            commands.add(List.copyOf(command));
            assertTrue(timeout.isPositive(), "Every command needs a finite positive timeout");
            if (command.equals(List.of("/usr/bin/id", "-u"))) return result(0, uid);
            if (command.equals(List.of("docker", "context", "show"))) return result(0, activeContext + "\n");
            if (command.size() >= 4 && command.getFirst().equals("docker") && command.contains("context") && command.contains("inspect")) {
                if (contextInspection != null) return contextInspection;
                String context = command.get(command.indexOf("inspect") + 1);
                return result(0, contexts.getOrDefault(context, endpoint));
            }
            if (command.size() == 4 && command.subList(0, 3).equals(List.of("docker", "context", "use"))) {
                activeContext = command.getLast();
                return result(0, activeContext);
            }
            if (command.contains("info")) return probes.isEmpty() ? defaultProbe : probes.removeFirst();
            if (command.equals(List.of("docker", "desktop", "start", "--detach"))) {
                if (desktopCommand.exitCode() == 0) switchContextAfterStart();
                return desktopCommand;
            }
            if (command.getFirst().endsWith("pkexec")) {
                if (privilegedService.exitCode() == 0) switchContextAfterStart();
                return privilegedService;
            }
            if (command.getFirst().endsWith("systemctl") && command.contains("start")) {
                if (systemService.exitCode() == 0) switchContextAfterStart();
                return systemService;
            }
            throw new AssertionError("Unexpected command: " + command);
        }

        @Override public void launch(List<String> command) throws IOException {
            launches.add(List.copyOf(command));
            if (launchFailure != null) throw launchFailure;
            switchContextAfterStart();
        }

        @Override public boolean executable(Path path) {
            String value = normalize(path.toString());
            return value.equals("/usr/bin/systemctl") || value.equals("/usr/bin/pkexec") || value.equals("/usr/bin/open")
                    || value.equals("/usr/bin/id")
                    || value.equals("C:/Program Files/Docker/Docker/Docker Desktop.exe");
        }

        @Override public long nanoTime() { return now; }

        @Override public void sleep(Duration duration) throws InterruptedException {
            if (interruptSleep) throw new InterruptedException("test cancellation");
            sleeps++;
            now += duration.toNanos();
            afterSleep.run();
        }

        boolean hasProbe() { return commands.stream().anyMatch(command -> command.contains("info")); }

        private void switchContextAfterStart() {
            if (activeContextAfterStart != null) activeContext = activeContextAfterStart;
        }
    }
}
