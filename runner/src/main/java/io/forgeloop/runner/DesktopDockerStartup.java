package io.forgeloop.runner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Starts only a recognized local Docker installation, then verifies the connection before paid work. */
public final class DesktopDockerStartup {
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);
    private static final String ENGINE_FORMAT = "{{.OSType}}";

    enum Platform {
        WINDOWS, MACOS, LINUX;
        static Platform fromOsName(String name) {
            String os = name.toLowerCase(Locale.ROOT);
            // Darwin contains "win", so macOS must be tested first.
            if (os.contains("mac") || os.contains("darwin")) return MACOS;
            return os.contains("win") ? WINDOWS : LINUX;
        }
    }

    record Environment(Platform platform, String userHome, Map<String, String> variables) {
        Environment { variables = Map.copyOf(variables); }
        String variable(String name) { return variables.getOrDefault(name, "").trim(); }
    }

    /** Native effects and time are injectable so platform behavior can be tested without starting services. */
    interface Host {
        DesktopPrerequisites.Report check();
        default DesktopPrerequisites.Report check(Duration timeout, BooleanSupplier cancelled) { return check(); }
        DesktopPrerequisites.CommandResult run(List<String> command, Duration timeout, BooleanSupplier cancelled)
                throws IOException, InterruptedException;
        void launch(List<String> command) throws IOException;
        boolean executable(Path path);
        long nanoTime();
        void sleep(Duration duration) throws InterruptedException;
    }

    private enum Engine { DESKTOP, ROOTLESS, SYSTEM }

    private record Connection(String context, String endpoint, boolean overridden) {
        List<String> probe() {
            return List.of("docker", context.isEmpty() ? "--host" : "--context",
                    context.isEmpty() ? endpoint : context, "info", "--format", ENGINE_FORMAT);
        }
        Map<String, String> workerEnvironment() {
            // A Desktop launch may switch the persisted CLI context asynchronously. Pin this attempt's
            // selected connection in the child JVM so later switches cannot redirect its Docker commands.
            return context.isEmpty() ? Map.of("DOCKER_CONTEXT", "", "DOCKER_HOST", endpoint)
                    : Map.of("DOCKER_CONTEXT", context, "DOCKER_HOST", "");
        }
    }

    record Prepared(DesktopPrerequisites.Report prerequisites, Map<String, String> dockerEnvironment) { }

    private DesktopDockerStartup() { }

    public static DesktopPrerequisites.Report prepare(Consumer<String> progress, BooleanSupplier cancelled) {
        return prepareForWorker(progress, cancelled).prerequisites();
    }

    static Prepared prepareForWorker(Consumer<String> progress, BooleanSupplier cancelled) {
        return prepareConnection(new SystemHost(), new Environment(Platform.fromOsName(System.getProperty("os.name", "")),
                System.getProperty("user.home", ""), System.getenv()), STARTUP_TIMEOUT, cancelled, progress);
    }

    static DesktopPrerequisites.Report prepare(Host host, Environment environment, Duration timeout,
                                               BooleanSupplier cancelled, Consumer<String> progress) {
        return prepareConnection(host, environment, timeout, cancelled, progress).prerequisites();
    }

    static Prepared prepareConnection(Host host, Environment environment, Duration timeout,
                                       BooleanSupplier cancelled, Consumer<String> progress) {
        Connection connection = null;
        Engine engine = null;
        boolean launched = false;
        long deadline = host.nanoTime() + timeout.toNanos();
        try {
            checkCancelled(cancelled);
            var initial = host.check(remaining(host, deadline, COMMAND_TIMEOUT.multipliedBy(3)), cancelled);
            checkCancelled(cancelled);
            remaining(host, deadline, COMMAND_TIMEOUT);
            if (!initial.git().ready() || (initial.docker().state() != DesktopPrerequisites.State.NOT_RUNNING
                    && !initial.docker().ready())) return new Prepared(initial, Map.of());
            connection = connection(host, environment, deadline, cancelled);
            // Already-working engines, including remote ones, need no startup or service changes.
            if (initial.ready()) return new Prepared(initial, connection.workerEnvironment());
            engine = localEngine(connection.endpoint(), environment);
            if (engine == Engine.ROOTLESS) {
                String uid = checkedOutput(run(host, List.of(trustedTool(host, "id"), "-u"), deadline, COMMAND_TIMEOUT, cancelled));
                if (!uid.matches("[0-9]+") || !connection.endpoint().equals("unix:///run/user/" + uid + "/docker.sock")) {
                    throw new IllegalStateException("This rootless Docker socket belongs to another user. Select your own Docker context or start that engine manually.");
                }
            }
            progress.accept("Starting local Docker" + (engine == Engine.DESKTOP ? " Desktop" : " engine") + ". No paid work has started.");
            launched = true;
            start(host, environment, engine, deadline, cancelled, progress);
            progress.accept("Waiting for Docker with Linux containers (up to 2 minutes). You can cancel startup.");
            waitForEngine(host, connection, deadline, cancelled);
            if (engine == Engine.DESKTOP) restoreContext(host, connection, deadline, cancelled);
            // Recheck the actual worker connection, not just a launcher exit code or a pinned probe.
            var ready = host.check(remaining(host, deadline, COMMAND_TIMEOUT.multipliedBy(3)), cancelled);
            checkCancelled(cancelled);
            remaining(host, deadline, COMMAND_TIMEOUT);
            if (ready.ready()) progress.accept("Docker is ready. Starting the runner.");
            return new Prepared(ready, connection.workerEnvironment());
        } catch (CancellationException cancelledAction) {
            throw new IllegalStateException("Docker startup cancelled. The runner was not started; Docker may remain running.");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Docker startup interrupted. The runner was not started.");
        } catch (IOException unavailable) {
            throw new IllegalStateException("Could not start or check Docker. Start Docker manually, then try Start runner again.");
        } finally {
            // Docker Desktop can switch contexts even if waiting is cancelled or startup fails.
            // Cleanup is bounded and never overwrites an unrelated selection made by the user.
            if (launched && engine == Engine.DESKTOP && connection != null && !Thread.currentThread().isInterrupted()) {
                try {
                    restoreContext(host, connection, host.nanoTime() + PROBE_TIMEOUT.toNanos(), () -> false);
                } catch (Exception notRestored) {
                    progress.accept("Check your Docker context before retrying. The runner will not change an unrelated selection.");
                }
            }
        }
    }

    private static Connection connection(Host host, Environment environment, long deadline,
                                         BooleanSupplier cancelled) throws IOException, InterruptedException {
        String explicitContext = environment.variable("DOCKER_CONTEXT");
        String directHost = environment.variable("DOCKER_HOST");
        if (explicitContext.isEmpty() && !directHost.isEmpty()) return new Connection("", directHost, true);
        String context = explicitContext.isEmpty()
                ? checkedOutput(run(host, List.of("docker", "context", "show"), deadline, COMMAND_TIMEOUT, cancelled))
                : explicitContext;
        String endpoint = checkedOutput(run(host, List.of("docker", "context", "inspect", context,
                "--format", "{{.Endpoints.docker.Host}}"), deadline, COMMAND_TIMEOUT, cancelled));
        return new Connection(context, endpoint, !explicitContext.isEmpty());
    }

    private static String checkedOutput(DesktopPrerequisites.CommandResult result) {
        if (result.exitCode() != 0 || result.output().isBlank()) {
            throw new IllegalStateException("Could not identify your Docker connection. Check your Docker context, then try again.");
        }
        return result.output().trim();
    }

    private static Engine localEngine(String endpoint, Environment environment) {
        String home = environment.userHome().replace('\\', '/');
        if (environment.platform() == Platform.WINDOWS && (endpoint.equals("npipe:////./pipe/docker_engine")
                || endpoint.equals("npipe:////./pipe/dockerDesktopLinuxEngine"))) return Engine.DESKTOP;
        if (environment.platform() == Platform.MACOS && (endpoint.equals("unix:///var/run/docker.sock")
                || endpoint.equals("unix://" + home + "/.docker/run/docker.sock"))) return Engine.DESKTOP;
        if (environment.platform() == Platform.LINUX) {
            if (endpoint.equals("unix://" + home + "/.docker/desktop/docker.sock")) return Engine.DESKTOP;
            if (endpoint.matches("unix:///run/user/[0-9]+/docker\\.sock")) return Engine.ROOTLESS;
            if (endpoint.equals("unix:///var/run/docker.sock") || endpoint.equals("unix:///run/docker.sock")) return Engine.SYSTEM;
        }
        throw new IllegalStateException("This Docker connection is remote or uses a custom socket. Start that engine manually, or select your local Docker context, then try again.");
    }

    private static void start(Host host, Environment environment, Engine engine, long deadline,
                              BooleanSupplier cancelled, Consumer<String> progress) throws IOException, InterruptedException {
        if (engine == Engine.DESKTOP) {
            // New Docker Desktop versions provide a cross-platform CLI; older installs use native launchers.
            try {
                if (run(host, List.of("docker", "desktop", "start", "--detach"), deadline, COMMAND_TIMEOUT, cancelled).exitCode() == 0) return;
            } catch (IOException oldDesktop) {
                checkCancelled(cancelled);
            }
            checkCancelled(cancelled);
            remaining(host, deadline, COMMAND_TIMEOUT);
            switch (environment.platform()) {
                case WINDOWS -> host.launch(List.of(windowsDesktop(host, environment)));
                case MACOS -> host.launch(List.of(trustedTool(host, "open"), "-a", "Docker"));
                case LINUX -> requireService(run(host, List.of(trustedTool(host, "systemctl"), "--user", "--no-block",
                        "start", "docker-desktop.service"), deadline, COMMAND_TIMEOUT, cancelled));
            }
            return;
        }
        String systemctl = trustedTool(host, "systemctl");
        if (engine == Engine.ROOTLESS) {
            requireService(run(host, List.of(systemctl, "--user", "--no-block", "start", "docker.service"),
                    deadline, COMMAND_TIMEOUT, cancelled));
            return;
        }
        var service = run(host, List.of(systemctl, "--no-ask-password", "--no-block", "start", "docker.service"),
                deadline, COMMAND_TIMEOUT, cancelled);
        if (service.exitCode() == 0) return;
        String detail = service.output().toLowerCase(Locale.ROOT);
        if (!detail.contains("authentication") && !detail.contains("authorization")
                && !detail.contains("access denied") && !detail.contains("permission denied")) {
            requireService(service);
        }
        progress.accept("Linux may ask for permission to start Docker. Approve the system dialog or cancel; ForgeLoop never asks for your password.");
        // Fixed system paths and fixed service name only. Never elevate a PATH-selected executable or shell.
        var authorized = run(host, List.of(trustedTool(host, "pkexec"), "--disable-internal-agent", systemctl,
                "--no-block", "start", "docker.service"), deadline, remaining(host, deadline, STARTUP_TIMEOUT), cancelled);
        if (authorized.exitCode() == 126) throw new CancellationException();
        requireService(authorized);
    }

    private static String windowsDesktop(Host host, Environment environment) {
        for (String root : List.of(environment.variable("ProgramFiles"), environment.variable("LOCALAPPDATA"))) {
            if (root.isEmpty()) continue;
            String suffix = root.equals(environment.variable("ProgramFiles"))
                    ? "Docker/Docker/Docker Desktop.exe" : "Programs/DockerDesktop/Docker Desktop.exe";
            Path path = Path.of(root, suffix);
            if (host.executable(path)) return path.toString();
        }
        throw new IllegalStateException("Docker Desktop was not found in its standard installation location. Start it manually, then try again.");
    }

    private static String trustedTool(Host host, String name) {
        for (String directory : List.of("/usr/bin", "/bin")) {
            Path path = Path.of(directory, name);
            if (host.executable(path)) return directory + "/" + name;
        }
        throw new IllegalStateException("Automatic Docker startup is unavailable on this system. Start Docker manually, then try again.");
    }

    private static void requireService(DesktopPrerequisites.CommandResult result) {
        if (result.exitCode() != 0) throw new IllegalStateException("Docker could not be started by the system service. Check the Docker installation or permissions, start it manually, then try again.");
    }

    private static void waitForEngine(Host host, Connection connection, long deadline,
                                      BooleanSupplier cancelled) throws IOException, InterruptedException {
        while (true) {
            checkCancelled(cancelled);
            try {
                var probe = run(host, connection.probe(), deadline, PROBE_TIMEOUT, cancelled);
                if (probe.exitCode() == 0) {
                    if ("linux".equalsIgnoreCase(probe.output().trim())) return;
                    throw new IllegalStateException("Docker must use Linux containers. Switch Docker Desktop to Linux containers, then try again.");
                }
                if (DesktopPrerequisites.dockerAccessDenied(probe.output())) {
                    throw new IllegalStateException("Your account cannot access the Docker engine. Fix Docker permissions, then try again.");
                }
            } catch (IOException notReadyYet) {
                // A slow daemon can time out individual probes; the overall deadline still bounds retries.
            }
            checkCancelled(cancelled);
            host.sleep(remaining(host, deadline, POLL_INTERVAL));
        }
    }

    private static void restoreContext(Host host, Connection connection, long deadline,
                                       BooleanSupplier cancelled) throws IOException, InterruptedException {
        if (connection.overridden() || connection.context().isEmpty()) return;
        String current = checkedOutput(run(host, List.of("docker", "context", "show"), deadline, COMMAND_TIMEOUT, cancelled));
        if (current.equals(connection.context())) return;
        if (!current.equals("desktop-linux")) {
            throw new IllegalStateException("Your Docker context changed during startup. The runner was not started. Check the selected context and try again.");
        }
        checkedOutput(run(host, List.of("docker", "context", "use", connection.context()), deadline, COMMAND_TIMEOUT, cancelled));
    }

    private static DesktopPrerequisites.CommandResult run(Host host, List<String> command, long deadline,
                                                          Duration maximum, BooleanSupplier cancelled)
            throws IOException, InterruptedException {
        checkCancelled(cancelled);
        var result = host.run(command, remaining(host, deadline, maximum), cancelled);
        checkCancelled(cancelled);
        return result;
    }

    private static Duration remaining(Host host, long deadline, Duration maximum) {
        long nanos = deadline - host.nanoTime();
        if (nanos <= 0) throw new IllegalStateException("Docker did not become ready before the startup timeout. Complete any Docker setup dialogs, or start Docker manually, then try again.");
        return Duration.ofNanos(Math.min(nanos, maximum.toNanos()));
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Docker startup interrupted. The runner was not started.");
        if (cancelled.getAsBoolean()) throw new CancellationException();
    }

    private static final class SystemHost implements Host {
        public DesktopPrerequisites.Report check() { return DesktopPrerequisites.check(); }
        public DesktopPrerequisites.Report check(Duration timeout, BooleanSupplier cancelled) { return DesktopPrerequisites.check(timeout, cancelled); }
        public DesktopPrerequisites.CommandResult run(List<String> command, Duration timeout, BooleanSupplier cancelled)
                throws IOException, InterruptedException { return DesktopCommandExecutor.run(command, timeout, cancelled); }
        public void launch(List<String> command) throws IOException { DesktopCommandExecutor.launch(command); }
        public boolean executable(Path path) { return Files.isRegularFile(path) && Files.isExecutable(path); }
        public long nanoTime() { return System.nanoTime(); }
        public void sleep(Duration duration) throws InterruptedException { Thread.sleep(duration); }
    }
}
