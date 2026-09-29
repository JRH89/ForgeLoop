package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ProviderAttemptReportTest {
    private static final String DIGEST = "a".repeat(64);

    @Test
    void successfulAndRejectedCallsRetainTheAnsweredModel() {
        ProviderUsageEvidence usage = new ProviderUsageEvidence("openai", "requested-model", DIGEST,
                10, 20, 1, 7, true, "actual-model-2026-01");

        assertEquals("actual-model-2026-01", ProviderAttemptReport.succeeded(usage).answeredModel());
        assertEquals("actual-model-2026-01", ProviderAttemptReport.rejected(usage, "DECLINED").answeredModel());
    }

    @Test
    void providerFailuresWithoutAResponseHaveNoAnsweredModel() {
        ProviderFailureEvidence failure = new ProviderFailureEvidence("openai", "requested-model", DIGEST,
                1, false, "PERMANENT_PROVIDER_FAILURE");

        assertNull(ProviderAttemptReport.failed(failure).answeredModel());
    }
}
