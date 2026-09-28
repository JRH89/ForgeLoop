package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class EvidenceRedactorTest {
    @Test void removesCommonCredentialAssignmentsTokensAndPrivateKeys() {
        String source = "api_key=abc123 password: hunter2 sk-abcdefghijklmnopqrstuvwxyz123456\n"
                + "-----BEGIN PRIVATE KEY-----\nprivate material\n-----END PRIVATE KEY-----";
        String redacted = EvidenceRedactor.redact(source);
        assertFalse(redacted.contains("abc123"));
        assertFalse(redacted.contains("hunter2"));
        assertFalse(redacted.contains("sk-abcdefghijklmnopqrstuvwxyz123456"));
        assertFalse(redacted.contains("private material"));
        assertTrue(redacted.contains("[REDACTED PRIVATE KEY]"));
    }
}
