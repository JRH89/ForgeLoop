package io.forgeloop.control.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ProviderAttemptSubmissionTest {
    @Test
    void answeredModelIsOptionalForOlderRunners() {
        assertDoesNotThrow(() -> submission(null));
    }

    @Test
    void answeredModelRejectsWhitespaceAndControlCharacters() {
        assertThrows(IllegalArgumentException.class, () -> submission("model with spaces"));
        assertThrows(IllegalArgumentException.class, () -> submission("model\nname"));
    }

    @Test
    void answeredModelIsBoundedToDatabaseColumn() {
        assertThrows(IllegalArgumentException.class, () -> submission("m".repeat(256)));
    }

    private static ProviderAttemptSubmission submission(String answeredModel) {
        return new ProviderAttemptSubmission("openai", "requested-model", "a".repeat(64), 1, 2,
                1, "SUCCEEDED", 0, false, false, "COMPLETED", answeredModel);
    }
}
