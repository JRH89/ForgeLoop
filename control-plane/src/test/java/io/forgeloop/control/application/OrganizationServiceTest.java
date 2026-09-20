package io.forgeloop.control.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.forgeloop.control.domain.*;
import io.forgeloop.control.security.OperatorContext;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OrganizationServiceTest {
    @Test void administratorCanGrantAuditedMembership() {
        OrganizationRepository organizations = mock(OrganizationRepository.class);
        OrganizationMembershipRepository memberships = mock(OrganizationMembershipRepository.class);
        OperatorContext operators = mock(OperatorContext.class);
        AuditLedgerService audit = mock(AuditLedgerService.class);
        OrganizationService service = new OrganizationService(organizations, memberships, operators, audit);
        when(memberships.findByOrganization_IdAndSubject("acme", "new-user")).thenReturn(Optional.empty());
        when(organizations.findById("acme")).thenReturn(Optional.of(new Organization("acme", "Acme")));
        when(memberships.save(any())).thenAnswer(call -> call.getArgument(0));

        OrganizationMembership membership = service.grantMembership("acme", "new-user", OperatorRole.OPERATOR);

        assertEquals(OperatorRole.OPERATOR, membership.getRole());
        verify(operators).requireAdministrator(); verify(operators).requireOrganization("acme");
        verify(audit).record("ORGANIZATION_MEMBERSHIP_GRANTED", "ORGANIZATION", "acme", "new-user|OPERATOR");
    }
}
