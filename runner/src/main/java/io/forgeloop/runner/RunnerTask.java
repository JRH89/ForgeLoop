package io.forgeloop.runner;

import java.util.List;

/** Immutable, server-derived work context a runner needs before it can safely prepare a worktree. */
public record RunnerTask(String id, String role, String title, String repository, String baseBranch,
                         String sourceRef, String specification, String requiredCapability, double budgetUsd,
                         List<String> ownedPaths, List<String> dependencyChangeShas) {
    public RunnerTask {
        if (id == null || id.isBlank() || repository == null || !repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
                || baseBranch == null || baseBranch.isBlank() || specification == null || specification.isBlank() || requiredCapability == null || requiredCapability.isBlank()) {
            throw new IllegalArgumentException("Runner task context is incomplete");
        }
        ownedPaths = ownedPaths == null ? List.of() : List.copyOf(ownedPaths);
        dependencyChangeShas = dependencyChangeShas == null ? List.of() : List.copyOf(dependencyChangeShas);
    }
    public RunnerTask(String id, String role, String title, String repository, String baseBranch,
                      String sourceRef, String specification, String requiredCapability) {
        this(id, role, title, repository, baseBranch, sourceRef, specification, requiredCapability, 0, List.of(), List.of());
    }
}
