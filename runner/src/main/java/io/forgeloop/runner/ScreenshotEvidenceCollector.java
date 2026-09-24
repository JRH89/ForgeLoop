package io.forgeloop.runner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Finds bounded Playwright PNG evidence without following links outside the task worktree. */
public final class ScreenshotEvidenceCollector {
    private static final int MAX_SCREENSHOTS = 10;
    private static final long MAX_SCREENSHOT_BYTES = 1024 * 1024;

    public List<Screenshot> collect(Path worktree) throws IOException {
        Path root = worktree.toRealPath(LinkOption.NOFOLLOW_LINKS);
        List<Path> candidates = new ArrayList<>();
        collectFrom(root, root.resolve("test-results"), candidates);
        collectFrom(root, root.resolve("playwright-report"), candidates);
        candidates.sort(Comparator.comparing(Path::toString));
        List<Screenshot> screenshots = new ArrayList<>();
        for (Path candidate : candidates.stream().limit(MAX_SCREENSHOTS).toList()) {
            byte[] content = Files.readAllBytes(candidate);
            if (content.length == 0 || content.length > MAX_SCREENSHOT_BYTES || !isPng(content)) continue;
            String safeName = String.format("screenshot-%02d-%s", screenshots.size() + 1,
                    candidate.getFileName().toString().replaceAll("[^A-Za-z0-9_.-]", "-"));
            screenshots.add(new Screenshot(safeName, content));
        }
        return List.copyOf(screenshots);
    }

    private static void collectFrom(Path root, Path directory, List<Path> candidates) throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return;
        try (var paths = Files.walk(directory)) {
            paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".png"))
                    .filter(path -> {
                        try { return path.toRealPath(LinkOption.NOFOLLOW_LINKS).startsWith(root); }
                        catch (IOException ignored) { return false; }
                    })
                    .forEach(candidates::add);
        }
    }

    private static boolean isPng(byte[] content) {
        byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        if (content.length < signature.length) return false;
        for (int index = 0; index < signature.length; index++) if (content[index] != signature[index]) return false;
        return true;
    }

    public record Screenshot(String displayName, byte[] content) {
        public Screenshot { content = content.clone(); }
        @Override public byte[] content() { return content.clone(); }
    }
}
