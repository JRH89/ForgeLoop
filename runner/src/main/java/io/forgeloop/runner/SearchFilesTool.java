package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Bounded regex search that never follows symlinks or enters Git metadata. */
final class SearchFilesTool implements LoopTool {
    private static final int MAX_FILES = 20_000;
    private static final int MAX_MATCHES = 100;
    private static final int MAX_LINE_SCAN_CHARS = 4_096;
    private static final ToolSpec SPEC = ToolSchemas.spec("search_files", "Search text files with a Java regular expression.",
            "{\"type\":\"object\",\"properties\":{\"pattern\":{\"type\":\"string\"},\"path\":{\"type\":\"string\"},\"glob\":{\"type\":\"string\"}},\"required\":[\"pattern\",\"path\",\"glob\"],\"additionalProperties\":false}");
    @Override public ToolSpec spec() { return SPEC; }
    @Override public void validateArguments(JsonNode args) throws LoopToolFailure {
        ToolArguments.fields(args, Set.of("pattern", "path", "glob"), "pattern", "path", "glob");
        ToolArguments.string(args, "pattern", false); ToolArguments.string(args, "path", false); ToolArguments.string(args, "glob", true);
    }
    @Override public ToolOutcome execute(ToolCall call, ToolContext context) throws LoopToolFailure, IOException {
        String expression = ToolArguments.string(call.arguments(), "pattern", false);
        Pattern pattern;
        try { pattern = SafeRegex.compile(expression); }
        catch (IllegalArgumentException invalid) { throw new LoopToolFailure(FailureCategory.VALIDATION, "Search pattern is invalid or exceeds safe regex limits"); }
        WorktreePathGuard guard = new WorktreePathGuard(context.worktree());
        String requested = ToolArguments.string(call.arguments(), "path", false);
        Path scope;
        try { scope = guard.directory(requested); }
        catch (IllegalArgumentException denied) { throw new LoopToolFailure(FailureCategory.PERMISSION, "Search path is outside the readable worktree"); }
        if (!Files.isDirectory(scope)) throw new LoopToolFailure(FailureCategory.VALIDATION, "Search directory does not exist");
        String glob = ToolArguments.string(call.arguments(), "glob", true);
        java.nio.file.PathMatcher matcher;
        try { matcher = glob.isEmpty() ? null : scope.getFileSystem().getPathMatcher("glob:" + glob); }
        catch (IllegalArgumentException invalid) { throw new LoopToolFailure(FailureCategory.VALIDATION, "File glob is invalid"); }
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(scope, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                return !dir.equals(scope) && (dir.getFileName().toString().equalsIgnoreCase(".git") || Files.isSymbolicLink(dir))
                        ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile() && !Files.isSymbolicLink(file) && !file.getFileName().toString().equalsIgnoreCase(".git")
                        && (matcher == null || matcher.matches(scope.relativize(file)))) files.add(file);
                return files.size() >= MAX_FILES ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
            }
        });
        List<String> matches = new ArrayList<>();
        boolean truncatedLongLine = false;
        for (Path file : files) {
            List<String> lines;
            try { lines = WorktreeText.read(file, 1024 * 1024).lines().toList(); }
            catch (WorktreeText.TooLarge | WorktreeText.BinaryFile ignored) { continue; }
            String relative = context.worktree().relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
            for (int i = 0; i < lines.size(); i++) {
                String searchable = lines.get(i);
                if (searchable.length() > MAX_LINE_SCAN_CHARS) { searchable = searchable.substring(0, MAX_LINE_SCAN_CHARS); truncatedLongLine = true; }
                if (!pattern.matcher(searchable).find()) continue;
                String line = lines.get(i).length() > 300 ? lines.get(i).substring(0, 297) + "..." : lines.get(i);
                matches.add(relative + ":" + (i + 1) + ": " + line);
                if (matches.size() >= MAX_MATCHES) return ToolOutcome.ok(String.join("\n", matches));
            }
        }
        String result = matches.isEmpty() ? "No matches." : String.join("\n", matches);
        if (truncatedLongLine) result += "\n[lines longer than " + MAX_LINE_SCAN_CHARS + " characters were searched only at the beginning]";
        return ToolOutcome.ok(result);
    }
}
