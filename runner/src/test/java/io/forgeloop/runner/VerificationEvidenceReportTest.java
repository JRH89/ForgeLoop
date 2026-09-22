package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
