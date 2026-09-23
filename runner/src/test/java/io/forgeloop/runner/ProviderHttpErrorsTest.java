package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class ProviderHttpErrorsTest {
    @Test void reportsOnlyBoundedStructuredErrorMetadata() {
        ProviderException failure = ProviderHttpErrors.from("Anthropic", 400,
                "{\"error\":{\"type\":\"invalid_request_error\",\"message\":\"schema is invalid\"},\"secret\":\"must-not-leak\"}");
        assertTrue(failure.getMessage().contains("invalid_request_error: schema is invalid"));
        assertFalse(failure.getMessage().contains("must-not-leak"));
        assertFalse(failure.retryable());
    }

    @Test void classifiesCapacityAndServerFailuresAsRetryable() {
        assertTrue(ProviderHttpErrors.from("OpenAI", 429, "{}").retryable());
        assertTrue(ProviderHttpErrors.from("OpenAI", 503, "not-json").retryable());
    }
}
