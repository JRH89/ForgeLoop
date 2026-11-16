package io.forgeloop.runner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Centralizes path confinement for tools that read or write a task worktree. */
public final class WorktreePathGuard {
    private final Path root;

    public WorktreePathGuard(Path worktree) {
        if (worktree == null) throw new IllegalArgumentException("Worktree path is required");
        root = worktree.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(root)) throw new IllegalArgumentException("Worktree path is a symbolic link");
        if (!Files.exists(root.resolve(".git"))) throw new IllegalArgumentException("Path guard requires a Git worktree");
    }

    /** Resolves a readable repository-relative path, rejecting Git metadata and symlink traversal. */
    public Path readable(String relativePath) {
        Path target = resolve(relativePath);
        rejectGitMetadata(relativePath);
        rejectSymbolicLinkTraversal(target);
        return target;
    }

    /** Resolves a readable directory, allowing the explicit dot path to mean the worktree root. */
    public Path directory(String relativePath) {
        if (".".equals(relativePath)) return root;
        return readable(relativePath);
    }

    /** Resolves a writable path only when it is under an owned prefix. */
    public Path writable(String relativePath, List<String> allowedPrefixes) {
        if (allowedPrefixes == null || allowedPrefixes.isEmpty()) throw new IllegalArgumentException("Patch write policy is incomplete");
        Path target = resolve(relativePath);
        rejectGitMetadata(relativePath);
        boolean allowed = allowedPrefixes.stream().map(prefix -> allowedRoot(prefix)).anyMatch(target::startsWith);
        if (!allowed) throw new IllegalArgumentException("Patch path is not permitted by policy");
        rejectSymbolicLinkTraversal(target);
        return target;
    }

    public Path root() { return root; }

    private Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank() || relativePath.contains("\\")) throw new IllegalArgumentException("Repository path is invalid");
        try {
            Path relative = Path.of(relativePath);
            if (relative.isAbsolute() || relative.normalize().startsWith("..")) throw new IllegalArgumentException("Repository path is outside the worktree");
            Path target = root.resolve(relative).normalize();
            if (!target.startsWith(root) || target.equals(root)) throw new IllegalArgumentException("Repository path is outside the worktree");
            return target;
        } catch (java.nio.file.InvalidPathException invalid) {
            throw new IllegalArgumentException("Repository path is invalid", invalid);
        }
    }

    private Path allowedRoot(String prefix) {
        if (prefix == null || prefix.isBlank()) throw new IllegalArgumentException("Allowed path prefix is invalid");
        try {
            Path relative = Path.of(prefix).normalize();
            if (relative.isAbsolute() || relative.startsWith("..")) throw new IllegalArgumentException("Allowed path prefix is invalid");
            return root.resolve(relative).normalize();
        } catch (java.nio.file.InvalidPathException invalid) {
            throw new IllegalArgumentException("Allowed path prefix is invalid", invalid);
        }
    }

    private void rejectGitMetadata(String relativePath) {
        for (Path segment : Path.of(relativePath)) {
            if (isGitMetadataAlias(segment.toString())) throw new IllegalArgumentException("Repository metadata is not accessible");
        }
    }

    /**
     * Matches every spelling that a Windows filesystem resolves to {@code .git}, mirroring Git's own
     * {@code core.protectNTFS} rules. Win32 drops trailing dots and spaces ({@code ".git. "}), treats
     * {@code ".git::$INDEX_ALLOCATION"} as the directory itself, and exposes the 8.3 short name
     * {@code GIT~1}. In a worktree, {@code .git} is a gitfile, so overwriting it could redirect Git to an
     * attacker-controlled configuration that runs commands on the runner host. The check applies on every
     * platform because a repository written here may later be checked out on Windows.
     */
    static boolean isGitMetadataAlias(String segment) {
        String name = segment.toLowerCase(java.util.Locale.ROOT);
        int stream = name.indexOf(':');
        if (stream >= 0) name = name.substring(0, stream);
        name = name.replaceAll("[. ]+$", "");
        return name.equals(".git") || name.matches("git~\\d+");
    }

    private void rejectSymbolicLinkTraversal(Path target) {
        Path current = root;
        for (Path segment : root.relativize(target)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) throw new IllegalArgumentException("Repository path traverses a symbolic link");
        }
    }
}
