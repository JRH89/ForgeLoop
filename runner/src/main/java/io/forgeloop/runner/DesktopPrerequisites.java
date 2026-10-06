package io.forgeloop.runner;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Checks local tools before runner enrollment or paid task execution. */
public final class DesktopPrerequisites {
    private static final String DOCKER_LINUX = "linux";

    public enum State { READY, MISSING, NOT_RUNNING, ACCESS_DENIED, WRONG_CONTAINER_MODE, CHECK_FAILED }

    public record Check(String tool, State state, String detail, URI installGuide) {
        public boolean ready() { return state == State.READY; }
    }

    public record Report(Check git, Check docker) {
        public boolean ready() { return git.ready() && docker.ready(); }
        public String summary() {
            if (ready()) return "Git and Docker are ready.";
            if (!git.ready() && !docker.ready()) return git.detail() + " " + docker.detail();
            return git.ready() ? docker.detail() : git.detail();
        }
    }

    @FunctionalInterface
    interface CommandRunner {
        CommandResult run(String tool, String... arguments) throws IOException, InterruptedException;
    }

    record CommandResult(int exitCode, String output) { }

    private DesktopPrerequisites() { }

    /** Uses OS-specific official installation guides, keeping the app itself platform-neutral. */
    public static URI gitGuide() {
        return switch (platform()) {
            case WINDOWS -> URI.create("https://git-scm.com/download/win");
            case MACOS -> URI.create("https://git-scm.com/download/mac");
            case LINUX -> URI.create("https://git-scm.com/download/linux");
        };
    }

    public static URI dockerGuide() {
        return switch (platform()) {
            case WINDOWS -> URI.create("https://docs.docker.com/desktop/setup/install/windows-install/");
            case MACOS -> URI.create("https://docs.docker.com/desktop/setup/install/mac-install/");
            case LINUX -> URI.create("https://docs.docker.com/desktop/setup/install/linux/");
        };
    }

    static Report check(CommandRunner commands) {
        Check git = checkGit(commands);
        Check docker = checkDocker(commands);
        return new Report(git, docker);
    }

    public static Report check() {
        return check(Duration.ofSeconds(30), () -> false);
    }

    /** Both probes share a deadline; startup cancellation can terminate an in-flight native check. */
    static Report check(Duration timeout, BooleanSupplier cancelled) {
        long deadline = System.nanoTime() + timeout.toNanos();
        return check((tool, arguments) -> {
            List<String> command = new ArrayList<>();
            command.add(tool);
            command.addAll(Arrays.asList(arguments));
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new IOException("Prerequisite check timed out");
            return DesktopCommandExecutor.run(command, Duration.ofNanos(Math.min(remaining,
                    Duration.ofSeconds(15).toNanos())), cancelled);
        });
    }

    public static void requireReady() {
        Report report = check();
        if (!report.ready()) throw new IllegalStateException(report.summary());
    }

    private static Check checkGit(CommandRunner commands) {
        try {
            CommandResult result = commands.run("git", "--version");
            if (result.exitCode() == 0 && result.output().toLowerCase(Locale.ROOT).contains("git version")) {
                return new Check("Git", State.READY, "Git is ready.", gitGuide());
            }
            return new Check("Git", State.CHECK_FAILED,
                    "Git is installed but did not return a valid version. Reinstall Git, then check again.", gitGuide());
        } catch (CancellationException cancelled) {
            throw cancelled;
        } catch (IllegalStateException missing) {
            return new Check("Git", State.MISSING,
                    "Git was not found. Install Git for your operating system, then restart ForgeLoop Runner.", gitGuide());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new Check("Git", State.CHECK_FAILED, "Git check was interrupted. Try again.", gitGuide());
        } catch (IOException unavailable) {
            return new Check("Git", State.CHECK_FAILED,
                    "Git could not be checked. Confirm it is installed and available to your account.", gitGuide());
        }
    }

    private static Check checkDocker(CommandRunner commands) {
        try {
            CommandResult result = commands.run("docker", "info", "--format", "{{.OSType}}");
            if (result.exitCode() == 0 && DOCKER_LINUX.equalsIgnoreCase(result.output().trim())) {
                return new Check("Docker", State.READY, "Docker is running with Linux containers.", dockerGuide());
            }
            if (result.exitCode() == 0) {
                return new Check("Docker", State.WRONG_CONTAINER_MODE,
                        "Docker is running in Windows-container mode. Switch Docker Desktop to Linux containers, then check again.", dockerGuide());
            }
            if (dockerAccessDenied(result.output())) {
                return new Check("Docker", State.ACCESS_DENIED,
                        "Your account cannot access the Docker engine. Fix Docker socket or Desktop permissions, then check again.", dockerGuide());
            }
            return new Check("Docker", State.NOT_RUNNING,
                    "Docker is installed but not ready. Start runner can try to start your local Docker engine; or start Docker manually and check again.", dockerGuide());
        } catch (CancellationException cancelled) {
            throw cancelled;
        } catch (IllegalStateException missing) {
            return new Check("Docker", State.MISSING,
                    "Docker was not found. Install Docker for your operating system, then restart ForgeLoop Runner.", dockerGuide());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new Check("Docker", State.CHECK_FAILED, "Docker check was interrupted. Try again.", dockerGuide());
        } catch (IOException unavailable) {
            return new Check("Docker", State.NOT_RUNNING,
                    "Docker could not connect to its engine. Start Docker and confirm your account can access it.", dockerGuide());
        }
    }

    /** Permission failures are not a stopped daemon and must not trigger privileged startup. */
    static boolean dockerAccessDenied(String output) {
        String detail = output.toLowerCase(Locale.ROOT);
        return detail.contains("permission denied") || detail.contains("access is denied")
                || detail.contains("operation not permitted");
    }

    private static Platform platform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac") || os.contains("darwin")) return Platform.MACOS;
        if (os.contains("win")) return Platform.WINDOWS;
        return Platform.LINUX;
    }

    private enum Platform { WINDOWS, MACOS, LINUX }
}
