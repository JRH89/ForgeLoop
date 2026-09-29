package io.forgeloop.control.domain;

import java.util.List;
import java.util.regex.Pattern;

/** Validates and matches the deliberately small repository-relative test-path glob language. */
public final class TestPathGlobs {
    private TestPathGlobs() { }

    public static boolean areValid(List<String> globs) {
        return globs != null && !globs.isEmpty() && globs.size() <= 32
                && globs.stream().allMatch(TestPathGlobs::isValid);
    }

    public static boolean isValid(String glob) {
        if (glob == null || glob.isBlank() || glob.length() > 500 || glob.startsWith("/")
                || glob.matches("^[A-Za-z]:.*") || glob.endsWith("/") || glob.contains("\\")
                || glob.codePoints().anyMatch(Character::isISOControl)) return false;
        String[] segments = glob.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) return false;
            if ("**".equals(segment)) continue;
            if (segment.contains("**") || segment.chars().anyMatch(TestPathGlobs::unsupportedGlobCharacter)) return false;
        }
        return true;
    }

    public static boolean matchesAny(String path, List<String> globs) {
        return globs != null && globs.stream().anyMatch(glob -> matches(glob, path));
    }

    public static boolean matches(String glob, String path) {
        if (!isValid(glob) || path == null || path.isBlank() || path.startsWith("/")
                || path.contains("\\") || path.contains("\u0000")) return false;
        String[] patternSegments = glob.split("/");
        String[] pathSegments = path.split("/", -1);
        if (java.util.Arrays.stream(pathSegments).anyMatch(segment -> segment.isEmpty() || ".".equals(segment) || "..".equals(segment))) return false;
        return matchesSegments(patternSegments, 0, pathSegments, 0, new byte[patternSegments.length + 1][pathSegments.length + 1]);
    }

    private static boolean matchesSegments(String[] patterns, int patternIndex, String[] paths, int pathIndex, byte[][] memo) {
        if (memo[patternIndex][pathIndex] != 0) return memo[patternIndex][pathIndex] == 2;
        boolean matched;
        if (patternIndex == patterns.length) matched = pathIndex == paths.length;
        else if ("**".equals(patterns[patternIndex])) {
            // Memoized zero-or-more matching keeps adjacent ** segments linear in path length.
            matched = matchesSegments(patterns, patternIndex + 1, paths, pathIndex, memo)
                    || (pathIndex < paths.length && matchesSegments(patterns, patternIndex, paths, pathIndex + 1, memo));
        } else {
            matched = pathIndex < paths.length && segmentPattern(patterns[patternIndex]).matcher(paths[pathIndex]).matches()
                    && matchesSegments(patterns, patternIndex + 1, paths, pathIndex + 1, memo);
        }
        memo[patternIndex][pathIndex] = (byte) (matched ? 2 : 1);
        return matched;
    }

    private static boolean unsupportedGlobCharacter(int character) {
        return Character.isISOControl(character) || "{}[]!".indexOf(character) >= 0;
    }

    private static Pattern segmentPattern(String segment) {
        StringBuilder regex = new StringBuilder("^");
        for (char value : segment.toCharArray()) {
            if (value == '*') regex.append(".*");
            else if (value == '?') regex.append('.');
            else regex.append(Pattern.quote(String.valueOf(value)));
        }
        return Pattern.compile(regex.append('$').toString());
    }
}
