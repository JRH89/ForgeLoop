package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class VerificationEvidenceReportTest {
    @Test
    void redactsCredentialsBeforeCalculatingChecksums() {
        VerificationEvidenceReport report = new VerificationEvidenceReport("SECURITY", "secrets", "scanner@sha256:" + "a".repeat(64),
                List.of("scan"), new VerificationResult(1, false, "api_key=super-secret-value ghp_abcdefghijklmnopqrstuvwxyz", Instant.EPOCH, Instant.EPOCH.plusSeconds(1)), "local-evidence/task");

        assertFalse(report.result().output().contains("super-secret-value"));
        assertFalse(report.result().output().contains("ghp_"));
        assertTrue(report.result().output().contains("REDACTED"));
        assertTrue(report.bundleDigest().matches("[0-9a-f]{64}"));
    }

    @Test
    void preservesTruncationAndPinsWithoutChangingTheExistingBundleDigest() {
        var result = new VerificationResult(0, false, "passed", Instant.EPOCH, Instant.EPOCH.plusSeconds(1), true);
        var legacy = new VerificationEvidenceReport("CONTAINER", "unit", "image@sha256:" + "a".repeat(64),
                List.of("npm", "test"), result, "artifact://bundle");
        var pinned = new VerificationEvidenceReport("CONTAINER", "unit", "image@sha256:" + "a".repeat(64),
                List.of("npm", "test"), result, "artifact://bundle", null, null, "b".repeat(40), "sha256:" + "c".repeat(64));

        assertTrue(pinned.result().outputTruncated());
        assertEquals("b".repeat(40), pinned.targetSha());
        assertEquals("sha256:" + "c".repeat(64), pinned.imageId());
        assertEquals(legacy.bundleDigest(), pinned.bundleDigest());
    }
}
