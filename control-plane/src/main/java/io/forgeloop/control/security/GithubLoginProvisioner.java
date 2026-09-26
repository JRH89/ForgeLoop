package io.forgeloop.control.security;

import io.forgeloop.control.domain.OperatorRole;
import io.forgeloop.control.domain.Organization;
import io.forgeloop.control.domain.OrganizationMembership;
import io.forgeloop.control.domain.OrganizationMembershipRepository;
import io.forgeloop.control.domain.OrganizationRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Maps immutable GitHub numeric identities to persisted ForgeLoop membership. */
@Service
public class GithubLoginProvisioner {
    private final OrganizationRepository organizations;
    private final OrganizationMembershipRepository memberships;
    private final String bootstrapOrganizationId;

    public GithubLoginProvisioner(OrganizationRepository organizations, OrganizationMembershipRepository memberships,
                                  @Value("${forgeloop.github.default-policy.organization-id:local-development}") String bootstrapOrganizationId) {
        this.organizations = organizations; this.memberships = memberships; this.bootstrapOrganizationId = bootstrapOrganizationId;
    }

    @Transactional
    public OrganizationMembership requireMembership(String githubId) {
        String subject = subject(githubId);
        List<OrganizationMembership> existing = memberships.findBySubjectOrderByIdAsc(subject);
        if (!existing.isEmpty()) return existing.getFirst();
        Organization organization = organizations.findForUpdateById(bootstrapOrganizationId)
                .orElseThrow(() -> new IllegalStateException("Bootstrap organization is not configured"));
        if (memberships.countByOrganization_Id(bootstrapOrganizationId) > 0) {
            throw new AccessDeniedException("Your GitHub account has not been invited to this ForgeLoop organization");
        }
        return memberships.save(new OrganizationMembership(organization, subject, OperatorRole.ADMIN));
    }

    public static String subject(String githubId) {
        if (githubId == null || !githubId.matches("[1-9][0-9]*")) throw new AccessDeniedException("GitHub returned an invalid user identity");
        return "github:" + githubId;
    }
}
