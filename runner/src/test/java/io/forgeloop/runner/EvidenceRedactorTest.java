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

    @Test void identifiesCredentialCategoriesWithoutReturningSecrets() {
        assertEquals("GitHub token", EvidenceRedactor.findCredentialToken("ghp_abcdefghijklmnopqrstuvwxyz123456").orElseThrow());
        assertEquals("API key", EvidenceRedactor.findCredentialToken("sk-abcdefghijklmnopqrstuvwxyz123456").orElseThrow());
        assertEquals("private key", EvidenceRedactor.findCredentialToken(
                "-----BEGIN RSA PRIVATE KEY-----\nmaterial\n-----END RSA PRIVATE KEY-----").orElseThrow());
        assertTrue(EvidenceRedactor.findCredentialToken("api_key = os.environ[\"KEY\"]").isEmpty());
    }

    @Test void redactsCredentialTokensAndReportsOnlyTheCount() {
        var result = EvidenceRedactor.redactCredentialTokens(
                "token=ghp_abcdefghijklmnopqrstuvwxyz123456 and sk-abcdefghijklmnopqrstuvwxyz123456");
        assertEquals("token=[REDACTED] and [REDACTED]", result.content());
        assertEquals(2, result.count());
        assertFalse(result.content().contains("ghp_"));
        assertFalse(result.content().contains("sk-"));
    }

    @Test void legacyEvidenceRedactionKeepsItsExistingAssignmentBehavior() {
        String redacted = EvidenceRedactor.redact("api_key=abc123");
        assertEquals("api_key=[REDACTED]", redacted);
    }
}
