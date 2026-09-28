package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class RepositoryIssueProposalWorkerTest {
    private final ProviderExecutionPolicy policy = new ProviderExecutionPolicy("openai", "model", 1,
            java.math.BigDecimal.ONE, java.math.BigDecimal.ONE);
    private final RepositoryIssueProposalGrant grant = new RepositoryIssueProposalGrant("proposal-1", "acme/app",
            "a".repeat(40), "HIGH", "Reject expired sessions", "Expired sessions are accepted.",
            "Old credentials may keep accessing data.", "SessionGuard does not compare expiry.",
            java.util.List.of("src/SessionGuard.java"), java.util.List.of("Reject expired credentials."));

    @Test void createsAnEditableEvidenceBoundDraftAndReturnsItsUsage() throws Exception {
        String output = "{\"title\":\"Reject expired credentials\",\"body\":\"Expired credentials pass the current session guard.\",\"acceptanceCriteria\":[\"Reject expired credentials.\",\"Add a regression test.\"]}";
        ProviderClient provider = request -> {
            assertEquals("model", request.model());
            assertTrue(request.instructions().contains("only the supplied evidence"));
            assertTrue(request.input().contains("SessionGuard does not compare expiry."));
            assertEquals("object", request.outputSchema().path("type").asText());
            return new ProviderResult(output, 20, 15, "request-1");
        };

        RepositoryIssueProposalResult result = new RepositoryIssueProposalWorker().execute(policy, provider, grant);

        assertEquals("Reject expired credentials", result.specification().title());
        assertEquals(2, result.specification().acceptanceCriteria().size());
        assertEquals(35, result.usage().estimatedCostMicros());
    }

    @Test void invalidDraftRetainsBillableUsageForReporting() {
        ProviderClient provider = ignored -> new ProviderResult("{\"title\":\"x\"}", 20, 15, "request-2");

        IssueProposalOutputFailure failure = assertThrows(IssueProposalOutputFailure.class,
                () -> new RepositoryIssueProposalWorker().execute(policy, provider, grant));

        assertEquals(35, failure.usage().estimatedCostMicros());
    }
}
