package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import org.junit.jupiter.api.Test;

class ProviderUsageEvidenceTest {
    @Test void retainsUsageButOnlyDigestsTheProviderRequestIdentifier() {
        ProviderUsageEvidence evidence = ProviderUsageEvidence.from(new ProviderExecutionPolicy("anthropic", "claude", 2), new ProviderExecutionResult(new ProviderResult("secret output", 12, 34, "msg_secret"), 2));
        assertEquals(12, evidence.inputTokens());
        assertEquals(34, evidence.outputTokens());
        assertEquals(2, evidence.attemptCount());
        assertFalse(evidence.costKnown());
        assertFalse(evidence.requestIdDigest().contains("msg_secret"));
    }

    @Test void missingProviderRequestIdentifiersRemainUniquePerLease() {
        ProviderExecutionPolicy policy = new ProviderExecutionPolicy("local", "model", 1);
        ProviderExecutionResult result = new ProviderExecutionResult(new ProviderResult("output", 1, 1, null), 1);

        String first = ProviderUsageEvidence.from(policy, result, ProviderCostEstimate.unknown(), "lease-1").requestIdDigest();
        String second = ProviderUsageEvidence.from(policy, result, ProviderCostEstimate.unknown(), "lease-2").requestIdDigest();

        assertNotEquals(first, second);
    }
}
