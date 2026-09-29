package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PatchWriterTest {
    @TempDir Path temporaryDirectory;
    @Test void writesOnlyPolicyApprovedPaths() throws Exception {
        Files.createDirectories(temporaryDirectory.resolve(".git"));
        new PatchWriter().apply(temporaryDirectory, new PatchPlan("x", List.of(new ProposedChange("src/a.txt", "ok", "test"))), List.of("src/"));
        assertEquals("ok", Files.readString(temporaryDirectory.resolve("src/a.txt")));
    }
    @Test void rejectsPathsOutsideThePolicy() throws Exception {
        Files.createDirectories(temporaryDirectory.resolve(".git"));
        assertThrows(IllegalArgumentException.class, () -> new PatchWriter().apply(temporaryDirectory, new PatchPlan("x", List.of(new ProposedChange("README.md", "x", "test"))), List.of("src/")));
    }
    @Test void validatesEveryPathBeforeWritingAnyFile() throws Exception {
        Files.createDirectories(temporaryDirectory.resolve(".git"));
        PatchPlan plan = new PatchPlan("x", List.of(new ProposedChange("src/ok.txt", "ok", "ok"), new ProposedChange("src-escape/no.txt", "no", "no")));
        assertThrows(IllegalArgumentException.class, () -> new PatchWriter().apply(temporaryDirectory, plan, List.of("src")));
        org.junit.jupiter.api.Assertions.assertFalse(Files.exists(temporaryDirectory.resolve("src/ok.txt")));
    }

    @Test void testOnlyBoundaryAcceptsMatchingFilesAndRejectsSourceFiles() throws Exception {
        Files.createDirectories(temporaryDirectory.resolve(".git"));
        WriteBoundary boundary = new WriteBoundary("TESTS_ONLY", List.of("**/*.test.ts"));
        new PatchWriter().apply(temporaryDirectory, new PatchPlan("tests", List.of(
                new ProposedChange("src/Button.test.ts", "test", "test"))), List.of("src"), boundary);
        assertEquals("test", Files.readString(temporaryDirectory.resolve("src/Button.test.ts")));
        assertThrows(WriteBoundaryViolation.class, () -> new PatchWriter().apply(temporaryDirectory, new PatchPlan("mixed", List.of(
                new ProposedChange("src/Other.test.ts", "should not be written", "test"),
                new ProposedChange("src/Button.ts", "forbidden", "source"))), List.of("src"), boundary));
        assertFalse(Files.exists(temporaryDirectory.resolve("src/Other.test.ts")));
    }

    @Test void noTestsBoundaryRejectsMatchingFileWithoutPartialWrites() throws Exception {
        Files.createDirectories(temporaryDirectory.resolve(".git"));
        WriteBoundary boundary = new WriteBoundary("NO_TESTS", List.of("**/*.test.ts"));
        assertThrows(WriteBoundaryViolation.class, () -> new PatchWriter().apply(temporaryDirectory, new PatchPlan("mixed", List.of(
                new ProposedChange("src/Feature.ts", "source", "source"),
                new ProposedChange("src/Feature.test.ts", "test", "test"))), List.of("src"), boundary));
        assertFalse(Files.exists(temporaryDirectory.resolve("src/Feature.ts")));
    }
}
