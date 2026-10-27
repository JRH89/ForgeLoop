package io.forgeloop.control.integrations.github;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import io.forgeloop.control.security.OperatorContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

class GithubInstallationOwnershipVerifierTest {
    private static final GithubInstallationState.Claim CLAIM = new GithubInstallationState.Claim("org-1", "github:42");
    private final OperatorContext operators = mock(OperatorContext.class);
    private final OAuth2AuthorizedClientService clients = mock(OAuth2AuthorizedClientService.class);
    private final GithubUserInstallations userInstallations = mock(GithubUserInstallations.class);

    @AfterEach void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test void acceptsTheInitiatingUserWhenGithubListsTheInstallation() {
        signIn(); when(userInstallations.canAccess("user-token", 8L)).thenReturn(true);
        assertDoesNotThrow(() -> verifier("github").requireAccess(CLAIM, 8L));
    }

    @Test void rejectsAnInstallationTheUserCannotAccess() {
        signIn(); when(userInstallations.canAccess("user-token", 9L)).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> verifier("github").requireAccess(CLAIM, 9L));
    }

    @Test void rejectsADifferentOperatorReplayingTheState() {
        signIn(); when(operators.subject()).thenReturn("github:7");
        assertThrows(AccessDeniedException.class, () -> verifier("github").requireAccess(CLAIM, 8L));
        verifyNoInteractions(userInstallations);
    }

    @Test void rejectsCallbacksWithoutAGithubBrowserSession() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("anonymous", "n/a"));
        assertThrows(AccessDeniedException.class, () -> verifier("production").requireAccess(CLAIM, 8L));
        verifyNoInteractions(userInstallations);
    }

    @Test void rejectsWhenNoUserTokenIsAvailableOrGithubRejectsIt() {
        signIn();
        when(clients.loadAuthorizedClient("github", "42")).thenReturn(null);
        assertThrows(AccessDeniedException.class, () -> verifier("github").requireAccess(CLAIM, 8L));

        signIn(); when(userInstallations.canAccess("user-token", 8L)).thenThrow(new GithubUserInstallations.GithubUserTokenRejectedException());
        assertThrows(AccessDeniedException.class, () -> verifier("github").requireAccess(CLAIM, 8L));
    }

    @Test void loopbackDevelopmentModeHasNoGithubIdentityToVerify() {
        assertDoesNotThrow(() -> verifier("development").requireAccess(CLAIM, 8L));
        verifyNoInteractions(userInstallations);
    }

    @SuppressWarnings("unchecked")
    private GithubInstallationOwnershipVerifier verifier(String mode) {
        ObjectProvider<OAuth2AuthorizedClientService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(clients);
        return new GithubInstallationOwnershipVerifier(mode, operators, provider, userInstallations);
    }

    private void signIn() {
        DefaultOAuth2User user = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("OAUTH2_USER")), Map.of("id", 42, "login", "octocat"), "id");
        SecurityContextHolder.getContext().setAuthentication(new OAuth2AuthenticationToken(user, user.getAuthorities(), "github"));
        when(operators.subject()).thenReturn("github:42");
        ClientRegistration registration = ClientRegistration.withRegistrationId("github").clientId("client").clientSecret("secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://github.com/login/oauth/authorize").tokenUri("https://github.com/login/oauth/access_token").build();
        OAuth2AccessToken token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "user-token", Instant.now(), Instant.now().plusSeconds(3600));
        when(clients.loadAuthorizedClient("github", "42")).thenReturn(new OAuth2AuthorizedClient(registration, "42", token));
    }
}
