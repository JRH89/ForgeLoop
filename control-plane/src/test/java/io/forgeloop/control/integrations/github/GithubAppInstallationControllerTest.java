package io.forgeloop.control.integrations.github;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static org.mockito.Mockito.*;
import io.forgeloop.control.domain.GithubInstallationRepository;
import io.forgeloop.control.domain.GithubInstallation;
import io.forgeloop.control.security.OperatorContext;

class GithubAppInstallationControllerTest {
    @Test
    void redirectsToConfiguredGithubAppInstallation() {
        GithubInstallationState state = mock(GithubInstallationState.class); OperatorContext operators = mock(OperatorContext.class); when(operators.organizationId()).thenReturn("org-1"); when(state.issue("org-1")).thenReturn("signed-state");
        var response = new GithubAppInstallationController("forgeloop-dev", state, mock(GithubInstallationRepository.class), operators).install();
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals("https://github.com/apps/forgeloop-dev/installations/new?state=signed-state", response.getHeaders().getLocation().toString());
    }

    @Test
    void refusesToRedirectWithoutAConfiguredApp() {
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, new GithubAppInstallationController("", mock(GithubInstallationState.class), mock(GithubInstallationRepository.class), mock(OperatorContext.class)).install().getStatusCode());
    }
    @Test
    void persistsTheSignedCallbackOrganization() {
        GithubInstallationState state = mock(GithubInstallationState.class); GithubInstallationRepository installations = mock(GithubInstallationRepository.class);
        when(state.verify("state")).thenReturn("org-1"); when(installations.findByInstallationId(8L)).thenReturn(java.util.Optional.empty());
        var response = new GithubAppInstallationController("forgeloop-dev", state, installations, mock(OperatorContext.class)).callback(8L, "state");
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode()); verify(installations).save(any(GithubInstallation.class));
    }
}
