package io.forgeloop.control.integrations.github;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class GithubInstallationStateTest {
    private final GithubInstallationState state = new GithubInstallationState("x".repeat(32));
    @Test void restoresTheIssuingOrganizationAndOperator() {
        assertEquals(new GithubInstallationState.Claim("org-1", "github:42"), state.verify(state.issue("org-1", "github:42")));
    }
    @Test void rejectsModifiedState() { String token = state.issue("org-1", "github:42"); assertThrows(IllegalArgumentException.class, () -> state.verify(token.substring(0, token.length() - 1) + "x")); }
    @Test void stateSignedWithAnotherSecretIsRejected() {
        String foreign = new GithubInstallationState("y".repeat(32)).issue("org-1", "github:42");
        assertThrows(IllegalArgumentException.class, () -> state.verify(foreign));
    }
    @Test void refusesToIssueStateWithoutAnOperatorOrWithAmbiguousFields() {
        assertThrows(IllegalArgumentException.class, () -> state.issue("org-1", null));
        assertThrows(IllegalArgumentException.class, () -> state.issue("org-1\ngithub:1", "github:42"));
    }
}
