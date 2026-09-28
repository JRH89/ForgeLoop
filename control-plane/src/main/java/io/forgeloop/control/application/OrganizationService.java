package io.forgeloop.control.application;

import io.forgeloop.control.domain.OperatorRole;
import io.forgeloop.control.domain.Organization;
import io.forgeloop.control.domain.OrganizationMembership;
import io.forgeloop.control.domain.OrganizationMembershipRepository;
import io.forgeloop.control.domain.OrganizationRepository;
import io.forgeloop.control.security.OperatorContext;
import io.forgeloop.control.security.GithubLoginProvisioner;
import io.forgeloop.control.integrations.github.GithubUserDirectory;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manages persistent tenant membership; only an existing organization administrator can grant access. */
@Service
public class OrganizationService {
    private final OrganizationRepository organizations;
    private final OrganizationMembershipRepository memberships;
    private final OperatorContext operators;
    private final AuditLedgerService audit;
    private final GithubUserDirectory githubUsers;
    public OrganizationService(OrganizationRepository organizations, OrganizationMembershipRepository memberships, OperatorContext operators, AuditLedgerService audit, GithubUserDirectory githubUsers) { this.organizations = organizations; this.memberships = memberships; this.operators = operators; this.audit = audit; this.githubUsers = githubUsers; }
    public List<OrganizationMembership> memberships(String organizationId) { operators.requireAdministrator(); operators.requireOrganization(organizationId); return memberships.findByOrganization_IdOrderBySubjectAsc(organizationId); }
    @Transactional public OrganizationMembership inviteGithubUser(String organizationId, String login, OperatorRole role) {
        operators.requireAdministrator(); operators.requireOrganization(organizationId);
        GithubUserDirectory.GithubUser user = githubUsers.findByLogin(login);
        String subject = GithubLoginProvisioner.subject(Long.toString(user.id()));
        if (memberships.findByOrganization_IdAndSubject(organizationId, subject).isPresent()) throw new IllegalStateException("GitHub user is already invited or a member");
        Organization organization = organizations.findById(organizationId).orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        OrganizationMembership membership = memberships.save(OrganizationMembership.invitation(organization, subject, user.login(), role));
        audit.record("ORGANIZATION_MEMBERSHIP_INVITED", "ORGANIZATION", organizationId, subject + "|" + role.name());
        return membership;
    }
    @Transactional public boolean revokeMembership(String organizationId, String membershipId) {
        operators.requireAdministrator(); operators.requireOrganization(organizationId);
        // Serialize administrative removals so concurrent requests cannot remove the last administrator.
        organizations.findForUpdateById(organizationId).orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        OrganizationMembership membership = memberships.findById(membershipId).orElseThrow(() -> new IllegalArgumentException("Membership not found"));
        if (!membership.getOrganizationId().equals(organizationId)) throw new IllegalArgumentException("Membership not found");
        if (membership.isAdministrator() && memberships.countByOrganization_IdAndRole(organizationId, OperatorRole.ADMIN) <= 1) {
            throw new IllegalStateException("The last organization administrator cannot be removed");
        }
        memberships.delete(membership);
        audit.record("ORGANIZATION_MEMBERSHIP_REVOKED", "ORGANIZATION", organizationId, membership.getSubject());
        return true;
    }
    @Transactional public OrganizationMembership grantMembership(String organizationId, String subject, OperatorRole role) {
        operators.requireAdministrator(); operators.requireOrganization(organizationId);
        if (subject == null || !subject.matches("github:[1-9][0-9]*")) throw new IllegalArgumentException("Membership subject must be an immutable GitHub user ID");
        if (memberships.findByOrganization_IdAndSubject(organizationId, subject).isPresent()) throw new IllegalStateException("Subject is already an organization member");
        Organization organization = organizations.findById(organizationId).orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        OrganizationMembership membership = memberships.save(new OrganizationMembership(organization, subject, role));
        audit.record("ORGANIZATION_MEMBERSHIP_GRANTED", "ORGANIZATION", organizationId, subject + "|" + role.name());
        return membership;
    }
}
