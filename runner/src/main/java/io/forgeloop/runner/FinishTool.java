package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates every worktree change against task ownership, then performs the one permitted commit. */
final class FinishTool implements LoopTool {
    private static final ToolSpec SPEC = ToolSchemas.spec("finish", "Finish this task by validating and committing the owned changes.",
            "{\"type\":\"object\",\"properties\":{\"summary\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":500}},\"required\":[\"summary\"],\"additionalProperties\":false}");
    private final GitWorktreeManager git;
    FinishTool(GitWorktreeManager git) { this.git = git; }
    @Override public ToolSpec spec() { return SPEC; }
    @Override public void validateArguments(JsonNode args) throws LoopToolFailure {
        ToolArguments.fields(args, Set.of("summary"), "summary");
        String summary = ToolArguments.string(args, "summary", false);
        if (summary.length() > 500) throw new LoopToolFailure(FailureCategory.BUSINESS_RULE, "Finish summary must be at most 500 characters");
    }
    @Override public ToolOutcome execute(ToolCall call, ToolContext context) throws LoopToolFailure, LoopHarnessFailure, IOException, InterruptedException {
        if (git == null) throw new IllegalStateException("Git worktree manager is not available");
        List<String> changed;
        try { changed = git.changedPaths(context.worktree()); }
        catch (IllegalStateException invalidChanges) { throw new LoopToolFailure(FailureCategory.BUSINESS_RULE, invalidChanges.getMessage()); }
        if (changed.isEmpty()) throw new LoopToolFailure(FailureCategory.BUSINESS_RULE, "Finish requires at least one changed path");
        if (changed.size() > 20) throw new LoopToolFailure(FailureCategory.BUSINESS_RULE, "Finish allows at most 20 changed paths; current count: " + changed.size());
        WorktreePathGuard guard = new WorktreePathGuard(context.worktree());
        for (String path : changed) {
            try { guard.writable(path, context.ownedPrefixes()); }
            catch (IllegalArgumentException outside) { throw new LoopToolFailure(FailureCategory.BUSINESS_RULE, "Finish found a changed path outside owned paths: " + path); }
        }
        String summary = ToolArguments.string(call.arguments(), "summary", false).strip();
        String sha;
        try { sha = git.commit(context.worktree(), GuardedPatchWorker.commitMessage(summary)); }
        catch (IOException | InterruptedException failure) {
            throw new LoopHarnessFailure("LOCAL_COMMIT_FAILURE", "Local task commit failed", failure);
        }
        return ToolOutcome.ok("Finished and committed " + changed.size() + " changed paths.", Map.of("changeSha", sha, "changedFiles", changed.size(), "summary", summary), List.of());
    }
}
