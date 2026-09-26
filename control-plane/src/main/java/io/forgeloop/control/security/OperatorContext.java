package io.forgeloop.control.security;

import org.springframework.beans.factory.annotation.Value;
import io.forgeloop.control.domain.OrganizationMembership;
import io.forgeloop.control.domain.OrganizationMembershipRepository;
import io.forgeloop.control.domain.OperatorRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;

/** Resolves the organization exclusively from a validated JWT claim, with an explicit local-only development fallback. */
@Component
public class OperatorContext {
    private final String mode;
    private final String developmentOrganizationId;
    private final OrganizationMembershipRepository memberships;

    public OperatorContext(@Value("${forgeloop.security.mode:production}") String mode,
                           @Value("${forgeloop.security.development-organization-id:local-development}") String developmentOrganizationId,
                           OrganizationMembershipRepository memberships) {
        this.mode = mode;
        this.developmentOrganizationId = developmentOrganizationId;
        this.memberships = memberships;
    }
    public String organizationId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            String organizationId = jwt.getToken().getClaimAsString("org_id");
            if (organizationId != null && !organizationId.isBlank() && memberships.findByOrganization_IdAndSubject(organizationId, jwt.getName()).isPresent()) return organizationId;
            throw new AccessDeniedException("JWT is missing required org_id claim");
        }
        if (authentication instanceof OAuth2AuthenticationToken oauth) return oauthMembership(oauth).getOrganizationId();
        if ("development".equals(mode)) return developmentOrganizationId;
        throw new AccessDeniedException("An authenticated organization principal is required");
    }
    public void requireOrganization(String organizationId) {
        if (!organizationId().equals(organizationId)) throw new AccessDeniedException("Cross-organization access is forbidden");
    }
    /** Administrative control-plane actions require a role minted by the OIDC provider, never a client argument. */
    public void requireAdministrator() {
        if ("development".equals(mode)) return;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof OAuth2AuthenticationToken oauth) {
            if (!oauthMembership(oauth).isAdministrator()) throw new AccessDeniedException("Organization administrator role is required");
            return;
        }
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !memberships.findByOrganization_IdAndSubject(organizationId(), jwt.getName()).map(OrganizationMembership::isAdministrator).orElse(false)) {
            throw new AccessDeniedException("Organization administrator role is required");
        }
    }
    /** Operators can control runs; viewers remain read-only. */
    public void requireOperator() {
        if ("development".equals(mode)) return;
        if (role() == OperatorRole.VIEWER) throw new AccessDeniedException("Organization operator role is required");
    }
    public OperatorRole role() {
        if ("development".equals(mode)) return OperatorRole.ADMIN;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof OAuth2AuthenticationToken oauth) return oauthMembership(oauth).getRole();
        if (!(authentication instanceof JwtAuthenticationToken jwt)) throw new AccessDeniedException("An authenticated operator is required");
        return memberships.findByOrganization_IdAndSubject(organizationId(), jwt.getName()).map(OrganizationMembership::getRole)
                .orElseThrow(() -> new AccessDeniedException("Organization membership is required"));
    }
    public String subject() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof OAuth2AuthenticationToken oauth) return GithubLoginProvisioner.subject(githubId(oauth));
        return authentication != null && authentication.isAuthenticated() ? authentication.getName() : "development-anonymous";
    }
    private OrganizationMembership oauthMembership(OAuth2AuthenticationToken oauth) {
        String subject = GithubLoginProvisioner.subject(githubId(oauth));
        return memberships.findBySubjectOrderByIdAsc(subject).stream().findFirst().orElseThrow(() -> new AccessDeniedException("Organization membership is required"));
    }
    private static String githubId(OAuth2AuthenticationToken oauth) {
        Object id = oauth.getPrincipal().getAttribute("id");
        return String.valueOf(id);
    }
}
