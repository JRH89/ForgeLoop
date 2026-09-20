package io.forgeloop.control.application;

import io.forgeloop.control.domain.OperatorRole;
import io.forgeloop.control.domain.Organization;
import io.forgeloop.control.domain.OrganizationMembership;
import io.forgeloop.control.domain.OrganizationMembershipRepository;
import io.forgeloop.control.domain.OrganizationRepository;
import io.forgeloop.control.security.OperatorContext;
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
    public OrganizationService(OrganizationRepository organizations, OrganizationMembershipRepository memberships, OperatorContext operators, AuditLedgerService audit) { this.organizations = organizations; this.memberships = memberships; this.operators = operators; this.audit = audit; }
    public List<OrganizationMembership> memberships(String organizationId) { operators.requireOrganization(organizationId); return memberships.findByOrganization_IdOrderBySubjectAsc(organizationId); }
    @Transactional public OrganizationMembership grantMembership(String organizationId, String subject, OperatorRole role) {
        operators.requireAdministrator(); operators.requireOrganization(organizationId);
        if (memberships.findByOrganization_IdAndSubject(organizationId, subject).isPresent()) throw new IllegalStateException("Subject is already an organization member");
        Organization organization = organizations.findById(organizationId).orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        OrganizationMembership membership = memberships.save(new OrganizationMembership(organization, subject, role));
        audit.record("ORGANIZATION_MEMBERSHIP_GRANTED", "ORGANIZATION", organizationId, subject + "|" + role.name());
        return membership;
    }
}
