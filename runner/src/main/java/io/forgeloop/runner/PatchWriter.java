package io.forgeloop.runner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.ArrayList;
import java.util.List;

/** Applies validated complete-file changes only below policy-approved relative path prefixes. */
public final class PatchWriter {
    public void apply(Path worktree, PatchPlan plan, List<String> allowedPrefixes) throws IOException {
        if (worktree == null || !Files.exists(worktree.resolve(".git")) || allowedPrefixes == null || allowedPrefixes.isEmpty()) throw new IllegalArgumentException("Patch write policy is incomplete");
        Path root = worktree.toAbsolutePath().normalize();
        List<Path> allowedRoots = allowedPrefixes.stream().map(prefix -> allowedRoot(root, prefix)).toList();
        List<Target> targets = new ArrayList<>();
        for (ProposedChange change : plan.changes()) {
            Path target = root.resolve(change.path()).normalize();
            if (!target.startsWith(root) || allowedRoots.stream().noneMatch(target::startsWith)) throw new IllegalArgumentException("Patch path is not permitted by policy");
            rejectSymbolicLinkTraversal(root, target);
            targets.add(new Target(change, target));
        }
        for (Target item : targets) {
            Path target = item.path(); ProposedChange change = item.change();
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), ".forgeloop-", ".tmp");
            try {
                Files.writeString(temporary, change.content(), StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
                try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temporary); }
        }
    }

    private static Path allowedRoot(Path root, String prefix) {
        if (prefix == null || prefix.isBlank()) throw new IllegalArgumentException("Allowed path prefix is invalid");
        Path relative = Path.of(prefix).normalize();
        if (relative.isAbsolute() || relative.startsWith("..")) throw new IllegalArgumentException("Allowed path prefix is invalid");
        return root.resolve(relative).normalize();
    }

    private static void rejectSymbolicLinkTraversal(Path root, Path target) {
        Path current = root;
        for (Path segment : root.relativize(target)) {
            current = current.resolve(segment);
            if (Files.exists(current) && Files.isSymbolicLink(current)) throw new IllegalArgumentException("Patch path traverses a symbolic link");
        }
    }

    private record Target(ProposedChange change, Path path) { }
}
