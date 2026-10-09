package io.forgeloop.runner;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DesktopConnectionRecoveryTest {
    @TempDir Path directory;

    @Test void backsUpEnrollmentAndPreservesProviderSettingsAndWork() throws Exception {
        Files.writeString(directory.resolve("identity"), "fixture-runner\nfixture-credential");
        Files.writeString(directory.resolve("config.json"), "fixture-config");
        Files.writeString(directory.resolve("anthropic-key.dpapi"), "fixture-protected-key");
        Files.createDirectory(directory.resolve("worktrees"));
        Path backup = DesktopConnectionRecovery.reset(directory);
        assertFalse(Files.exists(directory.resolve("identity")));
        assertEquals("fixture-runner\nfixture-credential", Files.readString(backup));
        assertEquals("fixture-config", Files.readString(directory.resolve("config.json")));
        assertEquals("fixture-protected-key", Files.readString(directory.resolve("anthropic-key.dpapi")));
        assertTrue(Files.isDirectory(directory.resolve("worktrees")));
        new RunnerIdentityStore().save(directory.resolve("identity"),new RunnerIdentity("new-runner","new-credential"));
        assertEquals("new-runner",new RunnerIdentityStore().load(directory.resolve("identity")).runnerId());
    }

    @Test void missingEnrollmentDoesNotCreateOrChangeFiles() {
        assertThrows(IllegalStateException.class, () -> DesktopConnectionRecovery.reset(directory));
        assertFalse(Files.exists(directory.resolve("identity")));
    }
}
