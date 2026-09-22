package io.forgeloop.control.domain;

import static org.junit.jupiter.api.Assertions.assertThrows;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class VerificationEvidenceTest {
    @Test
    void rejectsCorruptedOutputAndBundleChecksums() {
        FeatureRun run = new FeatureRun("org", "acme/repo", "issue-1", "Title", "spec", 10, "GENERIC", "main", 1);
        DeliveryTask task = run.addPlannedTask("verify-unit", "VERIFICATION", "Verify", "docker", List.of(), 2, 0);
        Runner runner = new Runner("org", "runner", "1", List.of("docker"), "hash");
        Instant time = Instant.parse("2026-01-01T00:00:00Z");

        assertThrows(IllegalArgumentException.class, () -> new VerificationEvidence(task, runner, "CONTAINER", "unit",
                "node@sha256:" + "a".repeat(64), List.of("npm", "test"), 0, false, "changed", time, time, null,
                VerificationEvidence.digest("original"), "b".repeat(64)));
    }
}
