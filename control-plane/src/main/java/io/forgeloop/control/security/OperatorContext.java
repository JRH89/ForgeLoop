package io.forgeloop.control.security;

import org.springframework.beans.factory.annotation.Value;
import io.forgeloop.control.domain.OrganizationMembership;
import io.forgeloop.control.domain.OrganizationMembershipRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !memberships.findByOrganization_IdAndSubject(organizationId(), jwt.getName()).map(OrganizationMembership::isAdministrator).orElse(false)) {
            throw new AccessDeniedException("Organization administrator role is required");
        }
    }
}
