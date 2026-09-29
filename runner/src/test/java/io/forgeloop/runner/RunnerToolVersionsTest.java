package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunnerToolVersionsTest {
    @Test void capturesBoundedToolAndHostPinsWithFifteenSecondCommandTimeouts() {
        List<List<String>> commands = new ArrayList<>();
        RunnerToolVersions versions = RunnerToolVersions.capture((command, timeout) -> {
            commands.add(List.copyOf(command));
            assertEquals(Duration.ofSeconds(15), timeout);
            return String.join(" ", command) + "\nversion";
        });
        assertEquals(3, commands.size());
        assertEquals("git --version version", versions.git());
        assertEquals("docker --version version", versions.dockerClient());
        assertEquals("docker version --format {{.Server.Version}} version", versions.dockerServer());
        assertTrue(versions.asMap().containsKey("java"));
        assertTrue(versions.asMap().containsKey("osArchitecture"));
    }

    @Test void recordsUnavailableToolVersionsWithoutFailingTaskSetup() {
        RunnerToolVersions versions = RunnerToolVersions.capture((command, timeout) -> { throw new java.io.IOException("not installed"); });
        assertEquals("unavailable", versions.git());
        assertEquals("unavailable", versions.dockerClient());
        assertEquals("unavailable", versions.dockerServer());
    }
}
