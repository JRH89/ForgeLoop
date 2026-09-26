package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryContextBuilderTest {
    @TempDir Path temporaryDirectory;

    @Test
    void includesManifestAndTextButExcludesGitMetadataAndBinaries() throws Exception {
        Files.createDirectories(temporaryDirectory.resolve(".git"));
        Files.createDirectories(temporaryDirectory.resolve("frontend/src"));
        Files.writeString(temporaryDirectory.resolve("frontend/src/Panel.tsx"), "export const Panel = () => <div />;");
        Files.write(temporaryDirectory.resolve("frontend/src/logo.png"), new byte[] {0, 1, 2});
        Files.writeString(temporaryDirectory.resolve(".git/config"), "secret-token");

        String context = new RepositoryContextBuilder().build(temporaryDirectory, List.of("frontend/src"));

        assertTrue(context.contains("frontend/src/Panel.tsx"));
        assertTrue(context.contains("export const Panel"));
        assertTrue(context.contains("frontend/src/logo.png"));
        assertFalse(context.contains("secret-token"));
    }

    @Test void prunesNestedMetadataAndWorktreePointerFiles() throws Exception {
        Files.writeString(temporaryDirectory.resolve(".git"),"gitdir: /private/worktree-metadata");
        Files.createDirectories(temporaryDirectory.resolve("nested/.git/objects"));
        Files.writeString(temporaryDirectory.resolve("nested/.git/objects/maintenance.lock"),"transient Git state");
        Files.writeString(temporaryDirectory.resolve("nested/.git/config.json"),"private metadata");
        Files.writeString(temporaryDirectory.resolve("README.md"),"project documentation");
        String context=new RepositoryContextBuilder().build(temporaryDirectory,List.of());
        assertTrue(context.contains("project documentation"));
        assertFalse(context.contains("maintenance.lock"));
        assertFalse(context.contains("private metadata"));
        assertFalse(context.contains("worktree-metadata"));
    }
}
