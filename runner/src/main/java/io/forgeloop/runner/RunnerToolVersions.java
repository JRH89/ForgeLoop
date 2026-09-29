package io.forgeloop.runner;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Captures bounded host tooling identity once per runner process for attempt pins. */
public record RunnerToolVersions(String git, String dockerClient, String dockerServer,
                                 String javaRuntime, String osName, String osVersion, String osArchitecture) {
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(15);
    private static final RunnerToolVersions CURRENT = capture(RunnerToolVersions::runVersionCommand);

    public static RunnerToolVersions current() { return CURRENT; }

    static RunnerToolVersions capture(VersionCommand command) {
        if (command == null) throw new IllegalArgumentException("Version command is required");
        return new RunnerToolVersions(safe(command, List.of("git", "--version")),
                safe(command, List.of("docker", "--version")),
                safe(command, List.of("docker", "version", "--format", "{{.Server.Version}}")),
                System.getProperty("java.runtime.version", "unavailable"),
                System.getProperty("os.name", "unavailable"), System.getProperty("os.version", "unavailable"),
                System.getProperty("os.arch", "unavailable"));
    }

    private static String safe(VersionCommand command, List<String> arguments) {
        try {
            String value = command.run(arguments, COMMAND_TIMEOUT);
            if (value == null || value.isBlank()) return "unavailable";
            String bounded = value.replaceAll("[\\r\\n]+", " ").strip();
            return bounded.substring(0, Math.min(bounded.length(), 160));
        } catch (Exception unavailable) { return "unavailable"; }
    }

    private static String runVersionCommand(List<String> arguments, Duration timeout) throws Exception {
        Process process = new ProcessBuilder(arguments).redirectErrorStream(true).start();
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Tool version command timed out");
        }
        byte[] output = process.getInputStream().readNBytes(4096);
        if (process.exitValue() != 0) throw new IllegalStateException("Tool version command failed");
        return new String(output, java.nio.charset.StandardCharsets.UTF_8);
    }

    @FunctionalInterface interface VersionCommand { String run(List<String> command, Duration timeout) throws Exception; }

    Map<String, String> asMap() {
        Map<String, String> versions = new LinkedHashMap<>();
        versions.put("git", git); versions.put("dockerClient", dockerClient); versions.put("dockerServer", dockerServer);
        versions.put("java", javaRuntime); versions.put("osName", osName); versions.put("osVersion", osVersion);
        versions.put("osArchitecture", osArchitecture);
        return Map.copyOf(versions);
    }
}
