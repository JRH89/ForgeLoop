package io.forgeloop.control.integrations.github;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class GithubInstallationStateTest {
    private final GithubInstallationState state = new GithubInstallationState("x".repeat(32));
    @Test void restoresTheIssuingOrganization() { assertEquals("org-1", state.verify(state.issue("org-1"))); }
    @Test void rejectsModifiedState() { String token = state.issue("org-1"); assertThrows(IllegalArgumentException.class, () -> state.verify(token.substring(0, token.length() - 1) + "x")); }
}
