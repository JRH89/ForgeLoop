package io.forgeloop.runner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Reset only enrollment material; never touch provider secrets, configuration, or task workspaces. */
final class DesktopConnectionRecovery {
    private DesktopConnectionRecovery() {}

    static Path reset(Path directory) throws IOException {
        Path identity = directory.resolve("identity");
        if (!Files.exists(identity)) throw new IllegalStateException("No saved connection to reset");
        Path backup = directory.resolve("identity.backup-" + UUID.randomUUID());
        // A move preserves owner-only permissions and permits explicit recovery if pairing fails.
        Files.move(identity, backup);
        return backup;
    }
}
