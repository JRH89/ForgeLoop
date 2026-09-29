package io.forgeloop.control.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TestPathGlobsTest {
    @Test
    void matchesWholeSegmentsAndSingleSegmentWildcards() {
        assertTrue(TestPathGlobs.matches("src/test/**", "src/test/java/WidgetTest.java"));
        assertTrue(TestPathGlobs.matches("**/*_test.go", "internal/api/user_test.go"));
        assertTrue(TestPathGlobs.matches("**/*.test.ts", "src/components/Button.test.ts"));
        assertTrue(TestPathGlobs.matches("tests/?ile-*.py", "tests/file-users.py"));
        assertFalse(TestPathGlobs.matches("src/test/*", "src/test/java/WidgetTest.java"));
        assertFalse(TestPathGlobs.matches("**/*.test.ts", "src/components/Button.ts"));
    }

    @Test
    void rejectsUnsupportedOrEscapingGlobSyntax() {
        assertFalse(TestPathGlobs.areValid(List.of("src/{test,spec}/**")));
        assertFalse(TestPathGlobs.areValid(List.of("src/[ab]/**")));
        assertFalse(TestPathGlobs.areValid(List.of("../tests/**")));
        assertFalse(TestPathGlobs.areValid(List.of("C:/tests/**")));
        assertFalse(TestPathGlobs.areValid(List.of("src/**/foo**bar")));
    }
}
