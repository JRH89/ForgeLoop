package io.forgeloop.control.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.forgeloop.control.domain.*;
import io.forgeloop.control.security.OperatorContext;
import io.forgeloop.control.integrations.github.GithubUserDirectory;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import org.junit.jupiter.api.Test;

class OrganizationServiceTest {
    @Test void administratorCanGrantAuditedMembership() {
        OrganizationRepository organizations = mock(OrganizationRepository.class);
        OrganizationMembershipRepository memberships = mock(OrganizationMembershipRepository.class);
        OperatorContext operators = mock(OperatorContext.class);
        AuditLedgerService audit = mock(AuditLedgerService.class);
        OrganizationService service = new OrganizationService(organizations, memberships, operators, audit, mock(GithubUserDirectory.class));
        when(memberships.findByOrganization_IdAndSubject("acme", "github:42")).thenReturn(Optional.empty());
        when(organizations.findById("acme")).thenReturn(Optional.of(new Organization("acme", "Acme")));
        when(memberships.save(any())).thenAnswer(call -> call.getArgument(0));

        OrganizationMembership membership = service.grantMembership("acme", "github:42", OperatorRole.OPERATOR);

        assertEquals(OperatorRole.OPERATOR, membership.getRole());
        verify(operators).requireAdministrator(); verify(operators).requireOrganization("acme");
        verify(audit).record("ORGANIZATION_MEMBERSHIP_GRANTED", "ORGANIZATION", "acme", "github:42|OPERATOR");
    }

    @Test void invitationUsesImmutableIdentityAndStartsPending() {
        OrganizationRepository organizations = mock(OrganizationRepository.class);
        OrganizationMembershipRepository memberships = mock(OrganizationMembershipRepository.class);
        OperatorContext operators = mock(OperatorContext.class);
        AuditLedgerService audit = mock(AuditLedgerService.class);
        GithubUserDirectory directory = mock(GithubUserDirectory.class);
        OrganizationService service = new OrganizationService(organizations, memberships, operators, audit, directory);
        when(directory.findByLogin("Octocat")).thenReturn(new GithubUserDirectory.GithubUser("octocat", 42));
        when(organizations.findById("acme")).thenReturn(Optional.of(new Organization("acme", "Acme")));
        when(memberships.save(any())).thenAnswer(call -> call.getArgument(0));

        OrganizationMembership invited = service.inviteGithubUser("acme", "Octocat", OperatorRole.VIEWER);
        assertEquals("github:42", invited.getSubject());
        assertEquals("octocat", invited.getGithubLogin());
        assertFalse(invited.isAccepted());
        verify(operators).requireAdministrator();
        verify(audit).record("ORGANIZATION_MEMBERSHIP_INVITED", "ORGANIZATION", "acme", "github:42|VIEWER");
        when(memberships.findByOrganization_IdAndSubject("acme", "github:42")).thenReturn(Optional.of(invited));
        assertThrows(IllegalStateException.class, () -> service.inviteGithubUser("acme", "Octocat", OperatorRole.VIEWER));
    }

    @Test void cannotRemoveLastAdministratorOrCrossTenantMember() {
        OrganizationRepository organizations = mock(OrganizationRepository.class);
        OrganizationMembershipRepository memberships = mock(OrganizationMembershipRepository.class);
        OrganizationService service = new OrganizationService(organizations, memberships, mock(OperatorContext.class), mock(AuditLedgerService.class), mock(GithubUserDirectory.class));
        Organization acme = new Organization("acme", "Acme");
        OrganizationMembership admin = new OrganizationMembership(acme, "github:42", OperatorRole.ADMIN);
        when(organizations.findForUpdateById("acme")).thenReturn(Optional.of(acme));
        when(memberships.findById("one")).thenReturn(Optional.of(admin));
        when(memberships.countByOrganization_IdAndRole("acme", OperatorRole.ADMIN)).thenReturn(1L);
        assertThrows(IllegalStateException.class, () -> service.revokeMembership("acme", "one"));
        verify(memberships, never()).delete(any());
        when(memberships.countByOrganization_IdAndRole("acme", OperatorRole.ADMIN)).thenReturn(2L);
        assertTrue(service.revokeMembership("acme", "one"));
        verify(memberships).delete(admin);
        when(memberships.findById("other")).thenReturn(Optional.of(new OrganizationMembership(new Organization("elsewhere", "Other"), "github:99", OperatorRole.VIEWER)));
        assertThrows(IllegalArgumentException.class, () -> service.revokeMembership("acme", "other"));
    }
}
