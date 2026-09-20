package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VerificationExecutorTest {
    @TempDir Path temporaryDirectory;

    @Test
    void rejectsCommandsOutsideAnIsolatedGitWorktree() {
        assertThrows(IllegalArgumentException.class,
                () -> new VerificationExecutor().execute(temporaryDirectory, List.of("echo", "unsafe"), Duration.ofSeconds(1)));
    }

    @Test
    void rejectsUnboundedTimeout() {
        assertThrows(IllegalArgumentException.class,
                () -> new VerificationExecutor().execute(temporaryDirectory, List.of("echo"), Duration.ofHours(2)));
    }
}
