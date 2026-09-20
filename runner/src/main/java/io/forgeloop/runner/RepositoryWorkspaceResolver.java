package io.forgeloop.runner;

import java.nio.file.Files;
import java.nio.file.Path;

/** Resolves only a pre-cloned repository beneath a runner-owned root; it never accepts an arbitrary path from ForgeLoop. */
public final class RepositoryWorkspaceResolver {
    public Path resolve(Path repositoriesRoot, String repository) {
        if (repositoriesRoot == null || repository == null || !repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("Repository context is unsafe");
        }
        Path root = repositoriesRoot.toAbsolutePath().normalize();
        Path checkout = root.resolve(repository).normalize();
        if (!checkout.startsWith(root) || !Files.isDirectory(checkout.resolve(".git"))) {
            throw new IllegalArgumentException("Configured runner repository checkout is unavailable");
        }
        return checkout;
    }
}
