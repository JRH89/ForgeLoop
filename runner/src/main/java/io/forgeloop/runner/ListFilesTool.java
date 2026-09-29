package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;

/** Lists immediate worktree entries without following links or exposing Git internals. */
final class ListFilesTool implements LoopTool {
    private static final ToolSpec SPEC = ToolSchemas.spec("list_files", "List direct entries in a repository directory.",
            "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}},\"required\":[\"path\"],\"additionalProperties\":false}");
    @Override public ToolSpec spec() { return SPEC; }
    @Override public void validateArguments(JsonNode args) throws LoopToolFailure { ToolArguments.fields(args, Set.of("path"), "path"); ToolArguments.string(args, "path", false); }
    @Override public ToolOutcome execute(ToolCall call, ToolContext context) throws LoopToolFailure, IOException {
        WorktreePathGuard guard = new WorktreePathGuard(context.worktree());
        String requested = ToolArguments.string(call.arguments(), "path", false);
        Path directory;
        try { directory = guard.directory(requested); }
        catch (IllegalArgumentException denied) { throw new LoopToolFailure(FailureCategory.PERMISSION, "Directory path is outside the permitted worktree"); }
        if (!Files.isDirectory(directory)) throw new LoopToolFailure(FailureCategory.VALIDATION, "Directory does not exist");
        try (Stream<Path> entries = Files.list(directory)) {
            var visible = entries.filter(path -> !path.getFileName().toString().equalsIgnoreCase(".git"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString())).limit(501).toList();
            if (visible.size() > 500) visible = visible.subList(0, 500);
            StringBuilder listing = new StringBuilder();
            for (Path entry : visible) {
                if (Files.isSymbolicLink(entry)) continue;
                String name = entry.getFileName().toString();
                listing.append(name);
                if (Files.isDirectory(entry)) listing.append('/');
                listing.append('\n');
            }
            return ToolOutcome.ok(listing.isEmpty() ? "(empty directory)" : listing.toString().stripTrailing());
        }
    }
}
