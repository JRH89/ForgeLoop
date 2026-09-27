package io.forgeloop.control.support;

import io.forgeloop.control.domain.OrganizationMembershipRepository;
import io.forgeloop.control.domain.OrganizationMembership;
import io.forgeloop.control.security.GithubLoginProvisioner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Support staff belong to the configured service-owner organization, not any customer admin role. */
@Component
public class SupportIdentity {
    private final OrganizationMembershipRepository memberships;
    private final String staffOrganization;
    public SupportIdentity(OrganizationMembershipRepository memberships,
            @Value("${forgeloop.support.admin-organization-id:${forgeloop.github.default-policy.organization-id:local-development}}") String staffOrganization) {
        this.memberships = memberships; this.staffOrganization = staffOrganization;
    }
    public String subject() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof OAuth2AuthenticationToken oauth && auth.isAuthenticated()) {
            // Preserve Object typing: generic getAttribute can otherwise select String.valueOf(char[]).
            Object githubId=oauth.getPrincipal().getAttribute("id");
            return githubId==null?null:GithubLoginProvisioner.subject(String.valueOf(githubId));
        }
        if (auth instanceof JwtAuthenticationToken jwt && auth.isAuthenticated()) {
            String subject=jwt.getName();
            return subject==null||subject.isBlank()?null:subject;
        }
        return null; // Public support must never inherit the console's anonymous development administrator.
    }
    public boolean administrator() {
        String subject = subject();
        return subject != null && memberships.findByOrganization_IdAndSubject(staffOrganization, subject)
                .map(OrganizationMembership::isAdministrator).orElse(false);
    }
    public String requireSubject() {
        String subject = subject();
        if (subject == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to view your tickets");
        return subject;
    }
    public void requireAdministrator() {
        requireSubject();
        if (!administrator()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Support administrator access is required");
    }
}
