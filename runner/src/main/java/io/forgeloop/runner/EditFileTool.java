package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Applies exact text replacements, refusing ambiguous edits unless replaceAll is explicit. */
final class EditFileTool implements LoopTool {
    private static final ToolSpec SPEC = ToolSchemas.spec("edit_file", "Replace exact text in an existing file you own.",
            "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"},\"oldText\":{\"type\":\"string\",\"minLength\":1},\"newText\":{\"type\":\"string\"},\"replaceAll\":{\"type\":\"boolean\"}},\"required\":[\"path\",\"oldText\",\"newText\",\"replaceAll\"],\"additionalProperties\":false}");
    @Override public ToolSpec spec() { return SPEC; }
    @Override public void validateArguments(JsonNode args) throws LoopToolFailure {
        ToolArguments.fields(args, Set.of("path", "oldText", "newText", "replaceAll"), "path", "oldText", "newText", "replaceAll");
        ToolArguments.string(args, "path", false); ToolArguments.string(args, "oldText", false);
        ToolArguments.string(args, "newText", true); ToolArguments.bool(args, "replaceAll");
    }
    @Override public ToolOutcome execute(ToolCall call, ToolContext context) throws LoopToolFailure, IOException {
        String relative = ToolArguments.string(call.arguments(), "path", false);
        String oldText = ToolArguments.string(call.arguments(), "oldText", false);
        String newText = ToolArguments.string(call.arguments(), "newText", true);
        boolean replaceAll = ToolArguments.bool(call.arguments(), "replaceAll");
        WorktreePathGuard guard = new WorktreePathGuard(context.worktree());
        Path file;
        try { file = guard.writable(relative, context.ownedPrefixes()); }
        catch (IllegalArgumentException denied) { throw new LoopToolFailure(FailureCategory.PERMISSION, "Edit path is outside the permitted owned paths"); }
        if (!Files.isRegularFile(file)) throw new LoopToolFailure(FailureCategory.VALIDATION, "File does not exist");
        String original;
        try { original = WorktreeText.read(file, 1024 * 1024); }
        catch (WorktreeText.TooLarge tooLarge) { throw new LoopToolFailure(FailureCategory.BUSINESS_RULE, "File exceeds the 1 MiB edit limit"); }
        catch (WorktreeText.BinaryFile binary) { throw new LoopToolFailure(FailureCategory.BUSINESS_RULE, "File is not readable UTF-8 text"); }
        int first = original.indexOf(oldText);
        if (first < 0) throw new LoopToolFailure(FailureCategory.VALIDATION, "oldText was not found in the file");
        int occurrences = 0;
        for (int cursor = first; cursor >= 0; cursor = original.indexOf(oldText, cursor + oldText.length())) occurrences++;
        if (!replaceAll && occurrences != 1) throw new LoopToolFailure(FailureCategory.VALIDATION, "oldText must occur exactly once unless replaceAll is true");
        String updated = replaceAll ? original.replace(oldText, newText) : original.substring(0, first) + newText + original.substring(first + oldText.length());
        if (updated.length() > 500_000) throw new LoopToolFailure(FailureCategory.BUSINESS_RULE, "Edited file exceeds 500000 characters");
        ProposedChange change = new ProposedChange(relative, updated, "Agent loop edit");
        new PatchWriter().apply(context.worktree(), new PatchPlan("Agent loop edit", List.of(change)), context.ownedPrefixes());
        PostImage image = new PostImage(relative, Hashing.sha256(updated), updated);
        return ToolOutcome.ok("Edited " + relative + " (" + occurrences + " replacement" + (occurrences == 1 ? "" : "s") + ").",
                Map.of("path", relative, "sha256", image.sha256(), "replacements", occurrences), List.of(image));
    }
}
