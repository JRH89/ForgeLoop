package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitWorktreeManagerTest {
    @TempDir Path temporaryDirectory;

    @Test
    void rejectsUnsafeTaskIdentifierBeforeInvokingGit() {
        assertThrows(IllegalArgumentException.class,
                () -> new GitWorktreeManager().create(temporaryDirectory, "main", "../escape", temporaryDirectory.resolve("worktrees")));
    }

    @Test
    void commitsChangesInsideAnActualGitWorktree() throws Exception {
        run("git", "init", temporaryDirectory.toString());
        run("git", "-C", temporaryDirectory.toString(), "config", "user.email", "runner@example.test");
        run("git", "-C", temporaryDirectory.toString(), "config", "user.name", "ForgeLoop Runner");
        Files.writeString(temporaryDirectory.resolve("README.md"), "base");
        run("git", "-C", temporaryDirectory.toString(), "add", "."); run("git", "-C", temporaryDirectory.toString(), "commit", "-m", "base");
        run("git", "-C", temporaryDirectory.toString(), "config", "--unset", "user.email");
        run("git", "-C", temporaryDirectory.toString(), "config", "--unset", "user.name");
        Path worktree = new GitWorktreeManager().create(temporaryDirectory, "HEAD", "task-1", temporaryDirectory.resolve("worktrees"));
        Files.writeString(worktree.resolve("README.md"), "changed");
        assertFalse(new GitWorktreeManager().commit(worktree, "feat: task change").isBlank());
    }
    @Test
    void integratesIndependentDependencyCommitsInDeclaredOrder() throws Exception {
        run("git", "init", temporaryDirectory.toString());
        run("git", "-C", temporaryDirectory.toString(), "config", "user.email", "runner@example.test");
        run("git", "-C", temporaryDirectory.toString(), "config", "user.name", "ForgeLoop Runner");
        Files.writeString(temporaryDirectory.resolve("README.md"), "base");
        run("git", "-C", temporaryDirectory.toString(), "add", ".");
        run("git", "-C", temporaryDirectory.toString(), "commit", "-m", "base");
        GitWorktreeManager git = new GitWorktreeManager();
        Path root = temporaryDirectory.resolve("worktrees");
        Path first = git.create(temporaryDirectory, "HEAD", "first", root);
        Files.writeString(first.resolve("backend.txt"), "backend");
        String firstSha = git.commit(first, "feat: backend");
        Path second = git.create(temporaryDirectory, "HEAD", "second", root);
        Files.writeString(second.resolve("frontend.txt"), "frontend");
        String secondSha = git.commit(second, "feat: frontend");
        Path integration = git.create(temporaryDirectory, "HEAD", "integration", root);

        String integratedSha = git.integrate(integration, java.util.List.of(firstSha, secondSha));

        assertFalse(integratedSha.isBlank());
        assertTrue(Files.exists(integration.resolve("backend.txt")));
        assertTrue(Files.exists(integration.resolve("frontend.txt")));
    }

    @Test
    void buildsAndIntegratesASequencedWriterChainAndReportsItsChangedFiles() throws Exception {
        run("git", "init", temporaryDirectory.toString());
        run("git", "-C", temporaryDirectory.toString(), "config", "user.email", "runner@example.test");
        run("git", "-C", temporaryDirectory.toString(), "config", "user.name", "ForgeLoop Runner");
        Files.writeString(temporaryDirectory.resolve("README.md"), "base");
        run("git", "-C", temporaryDirectory.toString(), "add", ".");
        run("git", "-C", temporaryDirectory.toString(), "commit", "-m", "base");
        GitWorktreeManager git = new GitWorktreeManager();
        String baseSha = output("git", "-C", temporaryDirectory.toString(), "rev-parse", "HEAD");
        Path root = temporaryDirectory.resolve("worktrees");
        Path tests = git.create(temporaryDirectory, baseSha, "tests-task", root);
        Files.createDirectories(tests.resolve("tests"));
        Files.writeString(tests.resolve("tests/Acceptance.java"), "class AcceptanceTest {}\n");
        String testsSha = git.commit(tests, "test: add acceptance coverage");
        Path implementation = git.create(temporaryDirectory, testsSha, "implementation-task", root);

        assertEquals(testsSha, git.headSha(implementation));
        Files.createDirectories(implementation.resolve("src"));
        Files.writeString(implementation.resolve("src/Feature.java"), "class Feature {}\n");
        String implementationSha = git.commit(implementation, "feat: implement feature");
        assertTrue(git.changedFiles(implementation, baseSha).containsAll(
                java.util.List.of("src/Feature.java", "tests/Acceptance.java")));
        Path integration = git.create(temporaryDirectory, baseSha, "integration-task", root);

        String integratedSha = git.integrate(integration, java.util.List.of(testsSha, implementationSha));

        assertFalse(integratedSha.isBlank());
        assertTrue(Files.readString(integration.resolve("tests/Acceptance.java")).contains("class AcceptanceTest {}"));
        assertTrue(Files.readString(integration.resolve("src/Feature.java")).contains("class Feature {}"));
    }

    @Test
    void resolvesRedCheckParentAndCapturesAddedAndRemovedBlobIdentities() throws Exception {
        run("git", "init", temporaryDirectory.toString());
        run("git", "-C", temporaryDirectory.toString(), "config", "user.email", "runner@example.test");
        run("git", "-C", temporaryDirectory.toString(), "config", "user.name", "ForgeLoop Runner");
        Files.createDirectories(temporaryDirectory.resolve("tests"));
        Files.writeString(temporaryDirectory.resolve("tests/OldTest.java"), "class OldTest {}\n");
        run("git", "-C", temporaryDirectory.toString(), "add", ".");
        run("git", "-C", temporaryDirectory.toString(), "commit", "-m", "base");
        GitWorktreeManager git = new GitWorktreeManager();
        String parent = git.headSha(temporaryDirectory);
        Path tests = git.create(temporaryDirectory, parent, "red-writer", temporaryDirectory.resolve("worktrees"));
        Files.delete(tests.resolve("tests/OldTest.java"));
        Files.createDirectories(tests.resolve("tests"));
        Files.writeString(tests.resolve("tests/NewTest.java"), "class NewTest {}\n");
        String target = git.commit(tests, "test: replace test fixture");

        assertEquals(parent, git.parentCommitSha(temporaryDirectory, target));
        List<GitWorktreeManager.ChangedFile> changed = git.changedFilesInCommit(temporaryDirectory, parent, target);
        String newBlob = output("git", "-C", temporaryDirectory.toString(), "rev-parse", target + ":tests/NewTest.java");

        assertEquals(2, changed.size());
        assertTrue(changed.stream().anyMatch(file -> file.path().equals("tests/NewTest.java")
                && file.blobSha().equals(newBlob)));
        assertTrue(changed.stream().anyMatch(file -> file.path().equals("tests/OldTest.java")
                && file.blobSha().chars().allMatch(character -> character == '0')));
    }

    private static String output(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String result = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0) throw new IllegalStateException(result);
        return result.strip();
    }
    private static void run(String... command) throws Exception { Process process = new ProcessBuilder(command).redirectErrorStream(true).start(); if (process.waitFor() != 0) throw new IllegalStateException(new String(process.getInputStream().readAllBytes())); }
}
