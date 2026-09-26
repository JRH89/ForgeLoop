package io.forgeloop.control.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import io.forgeloop.control.domain.Organization;
import io.forgeloop.control.domain.OrganizationMembership;
import io.forgeloop.control.domain.OrganizationMembershipRepository;
import io.forgeloop.control.domain.OrganizationRepository;
import io.forgeloop.control.domain.OperatorRole;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class GithubLoginProvisionerTest {
    private final OrganizationRepository organizations = mock(OrganizationRepository.class);
    private final OrganizationMembershipRepository memberships = mock(OrganizationMembershipRepository.class);
    private final GithubLoginProvisioner provisioner = new GithubLoginProvisioner(organizations, memberships, "local-development");

    @Test void preservesAnInvitedUsersPersistedRole() {
        OrganizationMembership viewer = new OrganizationMembership(new Organization("acme", "Acme"), "github:42", OperatorRole.VIEWER);
        when(memberships.findBySubjectOrderByIdAsc("github:42")).thenReturn(List.of(viewer));
        assertEquals(OperatorRole.VIEWER, provisioner.requireMembership("42").getRole());
        verifyNoInteractions(organizations);
    }

    @Test void bootstrapsOnlyTheFirstOrganizationMemberAsAdministrator() {
        Organization organization = new Organization("local-development", "Local development");
        when(memberships.findBySubjectOrderByIdAsc("github:42")).thenReturn(List.of());
        when(organizations.findForUpdateById("local-development")).thenReturn(Optional.of(organization));
        when(memberships.countByOrganization_Id("local-development")).thenReturn(0L);
        when(memberships.save(any())).thenAnswer(call -> call.getArgument(0));
        assertEquals(OperatorRole.ADMIN, provisioner.requireMembership("42").getRole());
    }

    @Test void rejectsAnUninvitedUserAfterBootstrap() {
        when(memberships.findBySubjectOrderByIdAsc("github:99")).thenReturn(List.of());
        when(organizations.findForUpdateById("local-development")).thenReturn(Optional.of(new Organization("local-development", "Local development")));
        when(memberships.countByOrganization_Id("local-development")).thenReturn(1L);
        assertThrows(AccessDeniedException.class, () -> provisioner.requireMembership("99"));
    }
}
