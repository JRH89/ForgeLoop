package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

class ProviderUsageEvidenceTest {
    @Test void retainsUsageButOnlyDigestsTheProviderRequestIdentifier() {
        ProviderUsageEvidence evidence = ProviderUsageEvidence.from(new ProviderExecutionPolicy("anthropic", "claude", 2), new ProviderResult("secret output", 12, 34, "msg_secret"));
        assertEquals(12, evidence.inputTokens());
        assertEquals(34, evidence.outputTokens());
        assertFalse(evidence.requestIdDigest().contains("msg_secret"));
    }
}
