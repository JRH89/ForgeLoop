package io.forgeloop.control.integrations.github;

import io.forgeloop.control.security.OperatorContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Proves that the browser completing a GitHub App setup redirect belongs to the operator who started it and
 * that this GitHub user can actually access the installation.
 *
 * <p>GitHub's setup URL carries {@code installation_id} as a plain query parameter. Anyone can edit it, so
 * the signed state alone only proves which tenant started an installation, not which installation it was.
 * Without this check, a member of one tenant could claim another customer's unclaimed installation, and
 * with it that customer's repositories.</p>
 */
@Component
public class GithubInstallationOwnershipVerifier {
    private final boolean developmentMode;
    private final OperatorContext operators;
    private final ObjectProvider<OAuth2AuthorizedClientService> authorizedClients;
    private final GithubUserInstallations userInstallations;

    public GithubInstallationOwnershipVerifier(@Value("${forgeloop.security.mode:production}") String securityMode, OperatorContext operators,
                                               ObjectProvider<OAuth2AuthorizedClientService> authorizedClients,
                                               GithubUserInstallations userInstallations) {
        this.developmentMode = "development".equals(securityMode);
        this.operators = operators;
        this.authorizedClients = authorizedClients;
        this.userInstallations = userInstallations;
    }

    /** Throws {@link AccessDeniedException} unless the current GitHub session may bind {@code installationId}. */
    public void requireAccess(GithubInstallationState.Claim claim, long installationId) {
        // Development mode has no GitHub identity to check and is documented as loopback-only.
        if (developmentMode) return;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof OAuth2AuthenticationToken oauth)) {
            throw new AccessDeniedException("Sign in with GitHub to finish installing the GitHub App");
        }
        if (!claim.subject().equals(operators.subject())) {
            throw new AccessDeniedException("The GitHub App installation must be finished by the operator who started it");
        }
        OAuth2AuthorizedClientService service = authorizedClients.getIfAvailable();
        OAuth2AuthorizedClient client = service == null ? null
                : service.loadAuthorizedClient(oauth.getAuthorizedClientRegistrationId(), oauth.getName());
        if (client == null || client.getAccessToken() == null) {
            throw new AccessDeniedException("Sign out and sign in with GitHub again, then retry the installation");
        }
        boolean accessible;
        try {
            accessible = userInstallations.canAccess(client.getAccessToken().getTokenValue(), installationId);
        } catch (GithubUserInstallations.GithubUserTokenRejectedException expired) {
            throw new AccessDeniedException("Your GitHub sign-in has expired. Sign out and back in, then retry the installation");
        }
        if (!accessible) throw new AccessDeniedException("Your GitHub account cannot access this GitHub App installation");
    }
}
