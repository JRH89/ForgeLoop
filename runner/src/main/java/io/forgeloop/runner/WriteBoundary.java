package io.forgeloop.runner;

import java.util.List;

/** Server-derived task patch boundary; owned prefixes remain an additional required constraint. */
public record WriteBoundary(String mode, List<String> testPathGlobs) {
    public WriteBoundary {
        mode = mode == null ? "ANY" : mode;
        testPathGlobs = testPathGlobs == null ? List.of() : List.copyOf(testPathGlobs);
        if (!List.of("ANY", "TESTS_ONLY", "NO_TESTS").contains(mode)
                || ("TESTS_ONLY".equals(mode) && !TestPathGlobs.areValid(testPathGlobs))) {
            throw new IllegalArgumentException("Task write boundary is invalid");
        }
    }

    public static WriteBoundary any() { return new WriteBoundary("ANY", List.of()); }
    public boolean requiresTestPaths() { return "TESTS_ONLY".equals(mode); }
    public boolean forbidsTestPaths() { return "NO_TESTS".equals(mode); }
    public boolean isAny() { return "ANY".equals(mode); }
    public boolean isTestPath(String path) { return TestPathGlobs.matchesAny(path, testPathGlobs); }
}
