package io.forgeloop.runner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Builds bounded, runner-local source context without sending credentials, binaries, or Git metadata. */
public final class RepositoryContextBuilder {
    private static final int MAX_FILES = 400;
    private static final int MAX_FILE_CHARS = 16 * 1024;
    private static final int MAX_CONTEXT_CHARS = 96 * 1024;
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "css", "graphql", "graphqls", "html", "java", "js", "json", "jsx", "md", "properties",
            "scss", "sql", "toml", "ts", "tsx", "txt", "xml", "yaml", "yml");

    public String build(Path repository, List<String> preferredPrefixes) throws IOException {
        Path root = repository.toAbsolutePath().normalize();
        if (!Files.exists(root.resolve(".git"))) throw new IllegalArgumentException("Repository context requires a Git worktree");
        List<String> prefixes = preferredPrefixes == null ? List.of() : preferredPrefixes.stream()
                .map(value -> value.replace('\\', '/').replaceAll("/$", "")).toList();
        List<Path> files;
        try (var paths = Files.walk(root)) {
            files = paths.filter(Files::isRegularFile)
                    .filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> !path.startsWith(root.resolve(".git")))
                    .sorted(Comparator.comparingInt((Path path) -> preferred(root, path, prefixes) ? 0 : 1)
                            .thenComparing(path -> relative(root, path)))
                    .limit(MAX_FILES)
                    .toList();
        }
        StringBuilder context = new StringBuilder("Repository manifest:\n");
        files.forEach(path -> context.append("- ").append(relative(root, path)).append('\n'));
        for (Path file : files) {
            if (!textFile(file) || Files.size(file) > MAX_FILE_CHARS) continue;
            String content;
            try {
                content = Files.readString(file);
            } catch (java.nio.charset.MalformedInputException binaryContent) {
                continue;
            }
            String section = "\n--- " + relative(root, file) + " ---\n" + content + "\n";
            if (context.length() + section.length() > MAX_CONTEXT_CHARS) continue;
            context.append(section);
        }
        return context.toString();
    }

    private static boolean preferred(Path root, Path path, List<String> prefixes) {
        String relative = relative(root, path);
        return prefixes.stream().anyMatch(prefix -> relative.equals(prefix) || relative.startsWith(prefix + "/"));
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
