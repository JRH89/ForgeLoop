package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TestCheckArtifactTest {
    @Test
    void serializesIsoTimestampsAndCanonicalOutcomeDigestsWithoutProviderOutput() throws Exception {
        Instant started = Instant.parse("2026-09-28T20:00:00Z");
        VerificationResult result = new VerificationResult(0, false, "tests completed", started, started.plusSeconds(2));
        TestRunReport report = new TestRunReport(TestRunReport.Status.READ, Map.of("Suite#test", TestRunReport.Outcome.PASSED));
        TestCheckArtifact artifact = new TestCheckArtifact("GREEN", "unit", "image@sha256:" + "a".repeat(64),
                List.of("test"), "b".repeat(40), null, null, TestCheckArtifact.from(result, report), List.of());

        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(artifact);

        assertTrue(json.contains("\"startedAt\":\"2026-09-28T20:00:00Z\""));
        assertTrue(json.contains("\"outcomeDigest\":\"" + EvidenceDigests.sha256("Suite#test\u0000PASSED") + "\""));
        assertTrue(!json.contains("tests completed"), "only the output digest leaves the runner");
    }

    @Test
    void rejectsPossibleSecretsBeforeTheBundleCanBeUploaded() {
        Instant now = Instant.now();
        VerificationResult result = new VerificationResult(0, false, "safe", now, now);
        TestRunReport report = new TestRunReport(TestRunReport.Status.READ, Map.of("Suite#api_key=sk-" + "x".repeat(30), TestRunReport.Outcome.PASSED));

        TestCheckArtifact.TestCheckRun run = TestCheckArtifact.from(result, report);
        assertThrows(IllegalArgumentException.class, () -> new TestCheckArtifact("GREEN", "unit", "image",
                List.of("test"), "b".repeat(40), null, null, run, List.of()));
    }

    @Test
    void oversizedReportCanBeSubmittedAsUnreadableEvidence() throws Exception {
        Instant now = Instant.parse("2026-09-28T20:00:00Z");
        Map<String, TestRunReport.Outcome> many = new java.util.HashMap<>();
        for (int index = 0; index < 3_000; index++) {
            many.put("S#" + index + "x".repeat(490), TestRunReport.Outcome.PASSED);
        }
        VerificationResult result = new VerificationResult(0, false, "safe", now, now.plusSeconds(1));
        var report = TestCheckArtifact.from(result, new TestRunReport(TestRunReport.Status.READ, many));
        TestCheckArtifact artifact = new TestCheckArtifact("GREEN", "unit", "image@sha256:" + "a".repeat(64),
                List.of("test"), "b".repeat(40), null, null, report, List.of());
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        assertTrue(mapper.writeValueAsBytes(artifact).length > TestCheckArtifact.MAX_ARTIFACT_BYTES);
        TestCheckArtifact bounded = artifact.withUnreadableReports();

        assertEquals("UNREADABLE", bounded.after().status());
        assertTrue(bounded.after().outcomes().isEmpty());
        assertTrue(mapper.writeValueAsBytes(bounded).length <= TestCheckArtifact.MAX_ARTIFACT_BYTES);
    }
}
