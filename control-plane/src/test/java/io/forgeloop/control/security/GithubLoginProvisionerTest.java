package io.forgeloop.control.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import io.forgeloop.control.domain.Organization;
import io.forgeloop.control.domain.OrganizationMembership;
import io.forgeloop.control.domain.OrganizationMembershipRepository;
import io.forgeloop.control.domain.OrganizationRepository;
import io.forgeloop.control.domain.OrganizationPolicyRepository;
import io.forgeloop.control.domain.HarnessDefinitionRepository;
import io.forgeloop.control.domain.OperatorRole;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.jdbc.core.JdbcTemplate;

class GithubLoginProvisionerTest {
    private final OrganizationRepository organizations = mock(OrganizationRepository.class);
    private final OrganizationMembershipRepository memberships = mock(OrganizationMembershipRepository.class);
    private final OrganizationPolicyRepository policies = mock(OrganizationPolicyRepository.class);
    private final HarnessDefinitionRepository harnesses = mock(HarnessDefinitionRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final GithubLoginProvisioner provisioner = new GithubLoginProvisioner(organizations, memberships, policies, harnesses, jdbc);

    @Test void preservesAnInvitedUsersPersistedRole() {
        OrganizationMembership viewer = OrganizationMembership.invitation(new Organization("acme", "Acme"), "github:42", "octocat", OperatorRole.VIEWER);
        org.junit.jupiter.api.Assertions.assertFalse(viewer.isAccepted());
        when(memberships.findBySubjectOrderByIdAsc("github:42")).thenReturn(List.of(viewer));
        assertEquals(OperatorRole.VIEWER, provisioner.requireMembership("42", "octocat").getRole());
        org.junit.jupiter.api.Assertions.assertTrue(viewer.isAccepted());
        verifyNoInteractions(organizations);
    }

    @Test void createsAnIsolatedAdminWorkspaceForEveryNewGithubAccount() {
        when(memberships.findBySubjectOrderByIdAsc("github:42")).thenReturn(List.of());
        when(organizations.save(any())).thenAnswer(call -> call.getArgument(0));
        when(memberships.save(any())).thenAnswer(call -> call.getArgument(0));
        OrganizationMembership created = provisioner.requireMembership("42", "octocat");
        assertEquals(OperatorRole.ADMIN, created.getRole());
        org.junit.jupiter.api.Assertions.assertTrue(created.getOrganizationId().startsWith("org-"));
        assertEquals("@octocat workspace (42)", created.getOrganizationName());
        verify(policies).save(argThat(policy -> policy.getOrganizationId().equals(created.getOrganizationId()) && policy.isRequireHumanApproval() && !policy.isAutoMergeEnabled()));
        verify(harnesses).save(argThat(harness -> harness.getOrganizationId().equals(created.getOrganizationId()) && harness.getName().equals("GENERIC")));
        verify(jdbc).queryForList("select pg_advisory_xact_lock(hashtextextended(?, 0))", "github:42");
    }

    @Test void aSecondNewAccountGetsItsOwnWorkspace() {
        when(memberships.findBySubjectOrderByIdAsc("github:99")).thenReturn(List.of());
        when(organizations.save(any())).thenAnswer(call -> call.getArgument(0));
        when(memberships.save(any())).thenAnswer(call -> call.getArgument(0));
        OrganizationMembership created = provisioner.requireMembership("99", "second-user");
        assertEquals("github:99", created.getSubject());
        org.junit.jupiter.api.Assertions.assertTrue(created.isAdministrator());
        org.junit.jupiter.api.Assertions.assertNotEquals("local-development", created.getOrganizationId());
    }

    @Test void refusesInvalidGitHubIdentityBeforeCreatingTenant() {
        assertThrows(AccessDeniedException.class, () -> provisioner.requireMembership("0", "octocat"));
        verifyNoInteractions(organizations, policies, harnesses, jdbc);
    }
}
