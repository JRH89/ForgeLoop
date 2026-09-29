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
        apply(worktree, plan, allowedPrefixes, WriteBoundary.any());
    }

    public void apply(Path worktree, PatchPlan plan, List<String> allowedPrefixes, WriteBoundary boundary) throws IOException {
        if (boundary == null) throw new IllegalArgumentException("Patch write policy is incomplete");
        WorktreePathGuard guard = new WorktreePathGuard(worktree);
        List<Target> targets = new ArrayList<>();
        for (ProposedChange change : plan.changes()) {
            Path target = guard.writable(change.path(), allowedPrefixes);
            String repositoryPath = guard.root().relativize(target).toString().replace('\\', '/');
            boolean testPath = boundary.isTestPath(repositoryPath);
            if (boundary.requiresTestPaths() && !testPath) {
                throw new WriteBoundaryViolation("Patch path " + repositoryPath + " is not a test file; this task writes tests only");
            }
            if (boundary.forbidsTestPaths() && testPath) {
                throw new WriteBoundaryViolation("Patch path " + repositoryPath + " is a test file; this task may not write tests");
            }
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

    private record Target(ProposedChange change, Path path) { }
}
