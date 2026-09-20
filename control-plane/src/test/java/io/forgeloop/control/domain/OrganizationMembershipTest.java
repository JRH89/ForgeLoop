package io.forgeloop.control.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class OrganizationMembershipTest {
    @Test void scopesMembershipToBothOrganizationAndSubject() {
        OrganizationMembership membership = new OrganizationMembership(new Organization("acme", "Acme"), "oidc-user", OperatorRole.ADMIN);
        assertTrue(membership.belongsTo("acme", "oidc-user"));
        assertFalse(membership.belongsTo("other", "oidc-user"));
        assertTrue(membership.isAdministrator());
    }
}
