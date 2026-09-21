package io.forgeloop.runner;

import java.util.Set;

/** Least-privilege manifest for every agent role in the delivery pipeline. */
public enum WorkerRolePolicy {
    PLANNER(false, Set.of("repository-read", "task-graph-propose")),
    IMPLEMENTATION(true, Set.of("repository-read", "scoped-file-write", "git-commit")),
    BACKEND(true, Set.of("repository-read", "scoped-file-write", "git-commit")),
    FRONTEND(true, Set.of("repository-read", "scoped-file-write", "git-commit")),
    INDEPENDENT_TEST(true, Set.of("repository-read", "scoped-file-write", "git-commit")),
    INTEGRATION(false, Set.of("repository-read", "git-integrate")),
    REPAIR(true, Set.of("repository-read", "scoped-file-write", "git-commit", "failure-evidence-read")),
    REVIEW(false, Set.of("repository-read", "diff-read", "evidence-read"));

    private final boolean repositoryWrite;
    private final Set<String> tools;

    WorkerRolePolicy(boolean repositoryWrite, Set<String> tools) {
        this.repositoryWrite = repositoryWrite;
        this.tools = tools;
    }

    public boolean repositoryWrite() { return repositoryWrite; }
    public Set<String> tools() { return tools; }

    public static WorkerRolePolicy require(String role) {
        try { return valueOf(role); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("Unknown worker role", exception); }
    }
}
