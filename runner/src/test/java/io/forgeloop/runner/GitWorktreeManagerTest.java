package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitWorktreeManagerTest {
    @TempDir Path temporaryDirectory;

    @Test
    void rejectsUnsafeTaskIdentifierBeforeInvokingGit() {
        assertThrows(IllegalArgumentException.class,
                () -> new GitWorktreeManager().create(temporaryDirectory, "main", "../escape", temporaryDirectory.resolve("worktrees")));
    }
}
