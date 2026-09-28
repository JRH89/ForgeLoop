package io.forgeloop.control.security;

import io.forgeloop.control.domain.OperatorRole;
import io.forgeloop.control.domain.Organization;
import io.forgeloop.control.domain.OrganizationMembership;
import io.forgeloop.control.domain.OrganizationMembershipRepository;
import io.forgeloop.control.domain.OrganizationRepository;
import io.forgeloop.control.domain.OrganizationPolicy;
import io.forgeloop.control.domain.OrganizationPolicyRepository;
import io.forgeloop.control.domain.HarnessDefinition;
import io.forgeloop.control.domain.HarnessDefinitionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates an isolated workspace for a new GitHub account or accepts its existing invitation. */
@Service
public class GithubLoginProvisioner {
    private final OrganizationRepository organizations;
    private final OrganizationMembershipRepository memberships;
    private final OrganizationPolicyRepository policies;
    private final HarnessDefinitionRepository harnesses;
    private final JdbcTemplate jdbc;

    public GithubLoginProvisioner(OrganizationRepository organizations, OrganizationMembershipRepository memberships,
                                  OrganizationPolicyRepository policies, HarnessDefinitionRepository harnesses, JdbcTemplate jdbc) {
        this.organizations = organizations; this.memberships = memberships; this.policies = policies; this.harnesses = harnesses; this.jdbc = jdbc;
    }

    @Transactional
    public OrganizationMembership requireMembership(String githubId, String githubLogin) {
        String subject = subject(githubId);
        // A transaction-scoped PostgreSQL lock serializes simultaneous first logins for this
        // GitHub account without making different customers contend on a global organization.
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?, 0))", subject);
        List<OrganizationMembership> existing = memberships.findBySubjectOrderByIdAsc(subject);
        if (!existing.isEmpty()) {
            existing.forEach(OrganizationMembership::accept);
            return existing.getFirst();
        }
        String organizationId = "org-" + UUID.randomUUID();
        String owner = githubLogin != null && githubLogin.matches("[A-Za-z0-9-]{1,39}") ? "@" + githubLogin : "GitHub " + githubId;
        Organization organization = organizations.save(new Organization(organizationId, owner + " workspace (" + githubId + ")"));
        // Safe defaults make the new tenant usable while preserving the explicit human gate.
        policies.save(new OrganizationPolicy(organizationId, 100, 4, List.of("anthropic", "openai", "gemini", "local"), true, false));
        harnesses.save(new HarnessDefinition(organizationId, "GENERIC", "Default repository-agnostic delivery roles",
                List.of("PLANNER", "IMPLEMENTATION", "BACKEND", "FRONTEND", "INDEPENDENT_TEST", "INTEGRATION", "REVIEW", "REPAIR", "VERIFICATION"), 2));
        return memberships.save(new OrganizationMembership(organization, subject, OperatorRole.ADMIN));
    }

    public static String subject(String githubId) {
        if (githubId == null || !githubId.matches("[1-9][0-9]*")) throw new AccessDeniedException("GitHub returned an invalid user identity");
        return "github:" + githubId;
    }
}
