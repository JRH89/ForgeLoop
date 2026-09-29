package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Reads numbered, bounded pages from text files in the worktree. */
final class ReadFileTool implements LoopTool {
    private static final long MAX_BYTES = 1024 * 1024;
    private static final ToolSpec SPEC = ToolSchemas.spec("read_file", "Read a numbered page of a text file.",
            "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"},\"startLine\":{\"type\":\"integer\",\"minimum\":1},\"maxLines\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":400}},\"required\":[\"path\",\"startLine\",\"maxLines\"],\"additionalProperties\":false}");
    @Override public ToolSpec spec() { return SPEC; }
    @Override public void validateArguments(JsonNode args) throws LoopToolFailure {
        ToolArguments.fields(args, Set.of("path", "startLine", "maxLines"), "path", "startLine", "maxLines");
        ToolArguments.string(args, "path", false); ToolArguments.integer(args, "startLine", 1, Integer.MAX_VALUE); ToolArguments.integer(args, "maxLines", 1, 400);
    }
    @Override public ToolOutcome execute(ToolCall call, ToolContext context) throws LoopToolFailure, IOException {
        String requested = ToolArguments.string(call.arguments(), "path", false);
        Path file;
        try { file = new WorktreePathGuard(context.worktree()).readable(requested); }
        catch (IllegalArgumentException denied) { throw new LoopToolFailure(FailureCategory.PERMISSION, "File path is outside the readable worktree"); }
        if (!Files.isRegularFile(file)) throw new LoopToolFailure(FailureCategory.VALIDATION, "File does not exist");
        List<String> lines;
        try { lines = WorktreeText.read(file, MAX_BYTES).lines().toList(); }
        catch (WorktreeText.TooLarge tooLarge) { throw new LoopToolFailure(FailureCategory.BUSINESS_RULE, "File exceeds the 1 MiB read limit"); }
        catch (WorktreeText.BinaryFile binary) { throw new LoopToolFailure(FailureCategory.BUSINESS_RULE, "File is not readable UTF-8 text"); }
        int startLine = ToolArguments.integer(call.arguments(), "startLine", 1, Integer.MAX_VALUE);
        int maxLines = ToolArguments.integer(call.arguments(), "maxLines", 1, 400);
        if (startLine > lines.size() + 1) throw new LoopToolFailure(FailureCategory.VALIDATION, "startLine is past the end of the file");
        int endExclusive = Math.min(lines.size(), startLine - 1 + maxLines);
        StringBuilder page = new StringBuilder();
        for (int line = startLine - 1; line < endExclusive; line++) page.append(line + 1).append(": ").append(lines.get(line)).append('\n');
        if (endExclusive < lines.size()) page.append("[more lines available; continue with startLine=").append(endExclusive + 1).append(']');
        return ToolOutcome.ok(page.isEmpty() ? "(empty file)" : page.toString().stripTrailing());
    }
}
