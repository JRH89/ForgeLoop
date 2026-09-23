package io.forgeloop.runner;

import java.util.List;

/** Immutable, server-derived work context a runner needs before it can safely prepare a worktree. */
public record RunnerTask(String id, String role, String title, String repository, String baseBranch,
                         String sourceRef, String specification, String requiredCapability, double budgetUsd,
                         List<String> ownedPaths, List<String> dependencyChangeShas, String verificationGateName,
                         String verificationKind, String verificationImageDigest, List<String> verificationCommand,
                         String verificationNetworkPolicy, Integer verificationTimeoutSeconds, String verificationBaseRef,
                         String executionBaseRef, List<String> acceptanceCriteria) {
    public RunnerTask {
        if (id == null || id.isBlank() || repository == null || !repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
                || baseBranch == null || baseBranch.isBlank() || executionBaseRef == null || executionBaseRef.isBlank()
                || specification == null || specification.isBlank() || requiredCapability == null || requiredCapability.isBlank()) {
            throw new IllegalArgumentException("Runner task context is incomplete");
        }
        ownedPaths = ownedPaths == null ? List.of() : List.copyOf(ownedPaths);
        dependencyChangeShas = dependencyChangeShas == null ? List.of() : List.copyOf(dependencyChangeShas);
        verificationCommand = verificationCommand == null ? List.of() : List.copyOf(verificationCommand);
        acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
    }
    public RunnerTask(String id, String role, String title, String repository, String baseBranch,
                      String sourceRef, String specification, String requiredCapability) {
        this(id, role, title, repository, baseBranch, sourceRef, specification, requiredCapability, 0, List.of(), List.of(), null, null, null, List.of(), null, null, baseBranch, baseBranch, List.of());
    }
}
