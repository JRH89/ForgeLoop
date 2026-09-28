package io.forgeloop.runner;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;

/** Checks local tools before runner enrollment or paid task execution. */
public final class DesktopPrerequisites {
    private static final String DOCKER_LINUX = "linux";

    public enum State { READY, MISSING, NOT_RUNNING, WRONG_CONTAINER_MODE, CHECK_FAILED }

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
        return check((tool, arguments) -> {
            String executable = DesktopToolPaths.executable(tool);
            Process process = new ProcessBuilder(concat(executable, arguments))
                    .redirectErrorStream(true).start();
            if (!process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Timed out");
            }
            return new CommandResult(process.exitValue(),
                    new String(process.getInputStream().readNBytes(8192)));
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
            return new Check("Docker", State.NOT_RUNNING,
                    "Docker is installed but not ready. Start Docker Desktop or the Docker service, then check again.", dockerGuide());
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

    private static String[] concat(String executable, String[] arguments) {
        String[] command = new String[arguments.length + 1];
        command[0] = executable;
        System.arraycopy(arguments, 0, command, 1, arguments.length);
        return command;
    }

    private static Platform platform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return Platform.WINDOWS;
        if (os.contains("mac") || os.contains("darwin")) return Platform.MACOS;
        return Platform.LINUX;
    }

    private enum Platform { WINDOWS, MACOS, LINUX }
}
