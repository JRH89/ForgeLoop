package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RepositoryScanWorkerTest {
    private static final String COMMIT = "a".repeat(40);
    private static final String CONTEXT = "Repository manifest:\n- src/Validator.java\n\n--- src/Validator.java ---\nReject empty input.\n";
    private static final String FINDING = "{\"findings\":[{\"severity\":\"HIGH\",\"title\":\"Reject empty input\",\"description\":\"Empty values pass validation.\",\"impact\":\"Invalid records enter the workflow.\",\"evidence\":\"Validator returns success on an empty string.\",\"affectedFiles\":[\"src/Validator.java\"],\"acceptanceCriteria\":[\"Add a test proving empty values are rejected.\"]}]}";
    private final ProviderExecutionPolicy policy = new ProviderExecutionPolicy("openai", "model", 1,
            java.math.BigDecimal.ONE, java.math.BigDecimal.ONE);

    @Test void acceptsOnlyEvidenceBackedManifestPathsAndTracksUsage() throws Exception {
        ProviderClient provider = ignored -> new ProviderResult(FINDING, 15, 10, "request-1");
        RepositoryScanResult result = new RepositoryScanWorker().execute(policy, provider, CONTEXT, COMMIT, "scan-1");
        assertEquals(COMMIT, result.commitSha());
        assertEquals("Reject empty input", result.findings().getFirst().title());
        assertEquals("src/Validator.java", result.findings().getFirst().affectedFiles().getFirst());
        assertEquals(25, result.usage().estimatedCostMicros());
    }

    @Test void rejectsHallucinatedPathsAndMalformedOutput() {
        ProviderClient hallucinated = ignored -> new ProviderResult(FINDING.replace("src/Validator.java", "src/Missing.java"), 2, 1, "request-2");
        assertThrows(GuardedPatchFailure.class, () -> new RepositoryScanWorker().execute(policy, hallucinated, CONTEXT, COMMIT, "scan-2"));
        ProviderClient malformed = ignored -> new ProviderResult("not json", 2, 1, "request-3");
        assertThrows(GuardedPatchFailure.class, () -> new RepositoryScanWorker().execute(policy, malformed, CONTEXT, COMMIT, "scan-3"));
    }
}
