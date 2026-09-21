package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class ProviderFailureEvidenceTest {
    @Test void classifiesOutagesWithoutPersistingTheExceptionMessage() {
        ProviderFailureEvidence evidence = ProviderFailureEvidence.from(new ProviderExecutionPolicy("anthropic", "claude", 2), new ProviderExecutionFailure(new ProviderException("key=secret", true), 2), "lease-1");
        assertEquals("TRANSIENT_PROVIDER_FAILURE", evidence.category());
        assertEquals(2, evidence.attemptCount());
    }
}
