package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class ProviderFailureEvidenceTest {
    @Test void classifiesOutagesWithoutPersistingTheExceptionMessage() {
        ProviderFailureEvidence evidence = ProviderFailureEvidence.from(new ProviderExecutionPolicy("anthropic", "claude", 2), new ProviderException("key=secret", true));
        assertEquals("TRANSIENT_PROVIDER_FAILURE", evidence.category());
    }
}
