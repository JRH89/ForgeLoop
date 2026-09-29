package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Writes one complete, bounded file using the same atomic guarded writer as the single-call worker. */
final class WriteFileTool implements LoopTool {
    private static final int MAX_CHARS = 500_000;
    private static final ToolSpec SPEC = ToolSchemas.spec("write_file", "Create or replace one complete file inside your owned paths.",
            "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"},\"content\":{\"type\":\"string\"}},\"required\":[\"path\",\"content\"],\"additionalProperties\":false}");
    @Override public ToolSpec spec() { return SPEC; }
    @Override public void validateArguments(JsonNode args) throws LoopToolFailure {
        ToolArguments.fields(args, Set.of("path", "content"), "path", "content");
        ToolArguments.string(args, "path", false); ToolArguments.string(args, "content", true);
        if (args.get("content").asText().length() > MAX_CHARS) throw new LoopToolFailure(FailureCategory.BUSINESS_RULE, "File content exceeds 500000 characters");
    }
    @Override public ToolOutcome execute(ToolCall call, ToolContext context) throws LoopToolFailure, IOException {
        String relative = ToolArguments.string(call.arguments(), "path", false);
        String content = ToolArguments.string(call.arguments(), "content", true);
        WorktreePathGuard guard = new WorktreePathGuard(context.worktree());
        try { guard.writable(relative, context.ownedPrefixes()); }
        catch (IllegalArgumentException denied) { throw new LoopToolFailure(FailureCategory.PERMISSION, "Write path is outside the permitted owned paths"); }
        ProposedChange change;
        try { change = new ProposedChange(relative, content, "Agent loop write"); }
        catch (IllegalArgumentException invalid) { throw new LoopToolFailure(FailureCategory.VALIDATION, "Write path or content is invalid"); }
        new PatchWriter().apply(context.worktree(), new PatchPlan("Agent loop write", List.of(change)), context.ownedPrefixes());
        String postImage = Files.readString(guard.readable(relative));
        PostImage image = new PostImage(relative, Hashing.sha256(postImage), postImage);
        return ToolOutcome.ok("Wrote " + relative + " (" + postImage.length() + " characters).", Map.of("path", relative, "sha256", image.sha256()), List.of(image));
    }
}
