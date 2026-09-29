package io.forgeloop.runner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Builds bounded, runner-local source context without sending credentials, binaries, or Git metadata. */
public final class RepositoryContextBuilder {
    private static final int MAX_FILES = 400;
    private static final int MAX_FILE_CHARS = 16 * 1024;
    private static final int MAX_CONTEXT_CHARS = 96 * 1024;
    private static final Set<String> GENERATED_DIRECTORIES = Set.of(".git", ".gradle", ".idea", ".next", ".venv", "build", "coverage", "dist", "node_modules", "out", "target", "vendor");
    private static final Set<String> SENSITIVE_SUFFIXES = Set.of(".key", ".pem", ".p12", ".pfx", ".keystore");
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "css", "graphql", "graphqls", "html", "java", "js", "json", "jsx", "md", "properties",
            "scss", "sql", "toml", "ts", "tsx", "txt", "xml", "yaml", "yml");

    public String build(Path repository, List<String> preferredPrefixes) throws IOException {
        return build(repository, preferredPrefixes, MAX_CONTEXT_CHARS);
    }

    /** Allows read-only scans to use a stricter prompt-size ceiling than normal planning context. */
    public String build(Path repository, List<String> preferredPrefixes, int maxContextChars) throws IOException {
        if (maxContextChars < 1024 || maxContextChars > MAX_CONTEXT_CHARS) throw new IllegalArgumentException("Repository context size limit is invalid");
        Path root = repository.toAbsolutePath().normalize();
        if (!Files.exists(root.resolve(".git"))) throw new IllegalArgumentException("Repository context requires a Git worktree");
        List<String> prefixes = preferredPrefixes == null ? List.of() : preferredPrefixes.stream()
                .map(value -> value.replace('\\', '/').replaceAll("/$", "")).toList();
        List<Path> candidatesInOrder = orderedCandidates(root, prefixes);
        StringBuilder context = new StringBuilder("Repository manifest:\n");
        List<Path> files = new ArrayList<>();
        // Reserve room for source text so unusual long paths cannot defeat the prompt ceiling.
        int manifestLimit = Math.min(maxContextChars / 3, 24 * 1024);
        for (Path path : candidatesInOrder) {
            String entry = "- " + relative(root, path) + "\n";
            if (context.length() + entry.length() > manifestLimit) break;
            context.append(entry);
            files.add(path);
        }
        for (Path file : files) {
            if (!textFile(file) || Files.size(file) > MAX_FILE_CHARS) continue;
            String content;
            try {
                content = Files.readString(file);
            } catch (java.nio.charset.MalformedInputException binaryContent) {
                continue;
            }
            content = EvidenceRedactor.redact(content);
            String section = "\n--- " + relative(root, file) + " ---\n" + content + "\n";
            if (context.length() + section.length() > maxContextChars) continue;
            context.append(section);
        }
        return context.toString();
    }

    /** Returns only prioritized repository paths so an agent can choose files without receiving their contents. */
    public String manifest(Path repository, List<String> preferredPrefixes) throws IOException {
        Path root = repository.toAbsolutePath().normalize();
        if (!Files.exists(root.resolve(".git"))) throw new IllegalArgumentException("Repository context requires a Git worktree");
        List<String> prefixes = normalizedPrefixes(preferredPrefixes);
        StringBuilder manifest = new StringBuilder("Repository manifest (paths only):\n");
        for (Path path : orderedCandidates(root, prefixes)) {
            manifest.append("- ").append(relative(root, path)).append('\n');
        }
        return manifest.toString();
    }

    private static List<String> normalizedPrefixes(List<String> preferredPrefixes) {
        return preferredPrefixes == null ? List.of() : preferredPrefixes.stream()
                .map(value -> value.replace('\\', '/').replaceAll("/$", "")).toList();
    }

    private static List<Path> orderedCandidates(Path root, List<String> prefixes) throws IOException {
        List<Path> candidates = new ArrayList<>();
        // Prune Git metadata before traversing it. Filtering a Files.walk stream
        // is too late: background Git maintenance can remove lock files mid-walk.
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                return !directory.equals(root) && GENERATED_DIRECTORIES.contains(directory.getFileName().toString())
                        ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                String name = file.getFileName().toString();
                if (attributes.isRegularFile() && !name.equals(".git") && !name.equalsIgnoreCase(".env")
                        && SENSITIVE_SUFFIXES.stream().noneMatch(suffix -> name.toLowerCase().endsWith(suffix))) candidates.add(file);
                return FileVisitResult.CONTINUE;
            }
        });
        return candidates.stream()
                .sorted(Comparator.comparingInt((Path path) -> priority(root, path, prefixes))
                        .thenComparing(path -> relative(root, path))).limit(MAX_FILES).toList();
    }

    private static int priority(Path root, Path path, List<String> prefixes) {
        String relative = relative(root, path);
        for (int index = 0; index < prefixes.size(); index++) {
            String prefix = prefixes.get(index);
            if (relative.equals(prefix) || relative.startsWith(prefix + "/")) return index;
        }
        return Integer.MAX_VALUE;
    }

    private static String relative(Path root, Path path) {
        return root.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private static boolean textFile(Path path) {
        String name = path.getFileName().toString();
        if (name.equals("Dockerfile") || name.equals("Makefile")) return true;
        int dot = name.lastIndexOf('.');
        return dot >= 0 && TEXT_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase());
    }
}
