package io.forgeloop.runner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/** Applies validated complete-file changes only below policy-approved relative path prefixes. */
public final class PatchWriter {
    public void apply(Path worktree, PatchPlan plan, List<String> allowedPrefixes) throws IOException {
        if (worktree == null || !Files.exists(worktree.resolve(".git")) || allowedPrefixes == null || allowedPrefixes.isEmpty()) throw new IllegalArgumentException("Patch write policy is incomplete");
        Path root = worktree.toAbsolutePath().normalize();
        for (ProposedChange change : plan.changes()) {
            if (allowedPrefixes.stream().noneMatch(prefix -> change.path().startsWith(prefix))) throw new IllegalArgumentException("Patch path is not permitted by policy");
            Path target = root.resolve(change.path()).normalize();
            if (!target.startsWith(root)) throw new IllegalArgumentException("Patch path escapes worktree");
            Files.createDirectories(target.getParent());
            Files.writeString(target, change.content(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }
}
