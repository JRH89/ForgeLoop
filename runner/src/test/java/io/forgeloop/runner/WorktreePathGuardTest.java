package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorktreePathGuardTest {
    @TempDir Path temporaryDirectory;

    @Test
    void confinesReadsAndWritesToTheWorktreeAndOwnedPrefixes() throws Exception {
        Files.createDirectory(temporaryDirectory.resolve(".git"));
        WorktreePathGuard guard = new WorktreePathGuard(temporaryDirectory);
        assertEquals(temporaryDirectory.resolve("src/main/App.java"), guard.writable("src/main/App.java", List.of("src/")));
        assertEquals(temporaryDirectory.resolve("README.md"), guard.readable("README.md"));
        assertThrows(IllegalArgumentException.class, () -> guard.readable("../outside.txt"));
        assertThrows(IllegalArgumentException.class, () -> guard.writable("src-escape/No.java", List.of("src")));
    }

    @Test
    void refusesWindowsAliasesForGitMetadata() throws Exception {
        Files.createDirectory(temporaryDirectory.resolve(".git"));
        WorktreePathGuard guard = new WorktreePathGuard(temporaryDirectory);
        for (String alias : List.of(".git.", ".GIT..", ".git ", ".git. .", "GIT~1", "git~12", ".git::$INDEX_ALLOCATION")) {
            assertThrows(IllegalArgumentException.class, () -> guard.writable(alias + "/config", List.of(".")), alias);
            assertThrows(IllegalArgumentException.class, () -> guard.readable("src/" + alias + "/HEAD"), alias);
        }
        // Ordinary names that merely resemble Git metadata stay usable.
        for (String ordinary : List.of(".github/workflows/ci.yml", ".gitignore", ".gitattributes", "docs/git~notes.md", "git/README.md")) {
            assertEquals(temporaryDirectory.resolve(ordinary), guard.writable(ordinary, List.of(".")), ordinary);
        }
    }

    @Test
    void refusesGitMetadataAndSymbolicLinkTraversal() throws Exception {
        Path worktree = Files.createDirectory(temporaryDirectory.resolve("worktree"));
        Path outside = Files.createDirectory(temporaryDirectory.resolve("outside"));
        Files.createDirectory(worktree.resolve(".git"));
        Files.writeString(outside.resolve("secret.txt"), "private");
        WorktreePathGuard guard = new WorktreePathGuard(worktree);
        assertThrows(IllegalArgumentException.class, () -> guard.readable(".git/config"));
        Path link = worktree.resolve("linked");
        try {
            Files.createSymbolicLink(link, outside);
            assertThrows(IllegalArgumentException.class, () -> guard.readable("linked/secret.txt"));
            assertThrows(IllegalArgumentException.class, () -> guard.writable("linked/new.txt", List.of("linked/")));
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException unsupported) {
            // Some Windows configurations prohibit creating symlinks without elevated privileges.
        }
    }
}
