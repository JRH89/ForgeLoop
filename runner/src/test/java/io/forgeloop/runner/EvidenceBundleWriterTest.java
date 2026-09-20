package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvidenceBundleWriterTest {
    @TempDir Path temporaryDirectory;

    @Test
    void writesEscapedReportAndChecksumManifest() throws Exception {
        VerificationEvidenceReport report = new VerificationEvidenceReport("CONTAINER", "unit", "node:22-alpine", "npm test",
                new VerificationResult(0, false, "line one\nline two", Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:01Z")));

        Path artifact = new EvidenceBundleWriter().write(temporaryDirectory, report);

        assertTrue(Files.readString(artifact).contains("\\n"));
        assertTrue(Files.exists(artifact.resolveSibling(artifact.getFileName() + ".sha256")));
        assertEquals(64, Files.readString(artifact.resolveSibling(artifact.getFileName() + ".sha256")).substring(0, 64).length());
    }
}
