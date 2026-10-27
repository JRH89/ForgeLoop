package io.forgeloop.control.integrations.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import static org.mockito.Mockito.*;
import io.forgeloop.control.domain.GithubInstallationRepository;
import io.forgeloop.control.domain.GithubInstallation;
import io.forgeloop.control.security.OperatorContext;

class GithubAppInstallationControllerTest {
    private final GithubInstallationState state = mock(GithubInstallationState.class);
    private final GithubInstallationRepository installations = mock(GithubInstallationRepository.class);
    private final GithubInstallationRepositorySyncService synchronization = mock(GithubInstallationRepositorySyncService.class);
    private final OperatorContext operators = mock(OperatorContext.class);
    private final GithubInstallationOwnershipVerifier ownership = mock(GithubInstallationOwnershipVerifier.class);
    private final GithubInstallationState.Claim claim = new GithubInstallationState.Claim("org-1", "github:42");

    private GithubAppInstallationController controller(String slug) {
        return new GithubAppInstallationController(slug, state, installations, synchronization, operators, ownership);
    }

    @Test
    void redirectsToConfiguredGithubAppInstallation() {
        when(operators.organizationId()).thenReturn("org-1"); when(operators.subject()).thenReturn("github:42");
        when(state.issue("org-1", "github:42")).thenReturn("signed-state");
        var response = controller("forgeloop-dev").install();
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals("https://github.com/apps/forgeloop-dev/installations/new?state=signed-state", response.getHeaders().getLocation().toString());
        verify(operators).requireAdministrator();
    }

    @Test
    void onlyAdministratorsMayStartAnInstallation() {
        doThrow(new AccessDeniedException("Organization administrator role is required")).when(operators).requireAdministrator();
        assertThrows(AccessDeniedException.class, () -> controller("forgeloop-dev").install());
        verifyNoInteractions(state);
    }

    @Test
    void refusesToRedirectWithoutAConfiguredApp() {
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, controller("").install().getStatusCode());
    }

    @Test
    void persistsTheSignedCallbackOrganizationAfterVerifyingUserAccess() {
        when(state.verify("state")).thenReturn(claim); when(installations.findByInstallationId(8L)).thenReturn(java.util.Optional.empty());
        var response = controller("forgeloop-dev").callback(8L, "state");
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        var order = inOrder(ownership, installations, synchronization);
        order.verify(ownership).requireAccess(claim, 8L);
        order.verify(installations).save(any(GithubInstallation.class));
        order.verify(synchronization).synchronizeInstallation(8L);
    }

    @Test
    void aSpoofedInstallationIdIsNeverBound() {
        when(state.verify("state")).thenReturn(claim);
        doThrow(new AccessDeniedException("Your GitHub account cannot access this GitHub App installation")).when(ownership).requireAccess(claim, 9L);
        assertThrows(AccessDeniedException.class, () -> controller("forgeloop-dev").callback(9L, "state"));
        verify(installations, never()).save(any());
        verifyNoInteractions(synchronization);
    }

    @Test
    void refusesToReassignAnInstallationToAnotherOrganization() {
        when(state.verify("state")).thenReturn(new GithubInstallationState.Claim("org-2", "github:42"));
        when(installations.findByInstallationId(8L)).thenReturn(java.util.Optional.of(new GithubInstallation(8L, "org-1")));
        assertThrows(IllegalArgumentException.class, () -> controller("forgeloop-dev").callback(8L, "state"));
    }

    @Test
    void invalidCallbackInputIsAClientError() {
        assertEquals(HttpStatus.BAD_REQUEST, controller("forgeloop-dev").rejectInvalidCallback().getStatusCode());
    }
}
