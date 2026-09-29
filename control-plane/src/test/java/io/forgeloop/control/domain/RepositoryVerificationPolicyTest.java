package io.forgeloop.control.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import java.util.List;
import org.junit.jupiter.api.Test;

class RepositoryVerificationPolicyTest {
    @Test
    void replacesExistingPolicyInPlaceAndAdvancesRevision() {
        String image = "node@sha256:" + "a".repeat(64);
        RepositoryConnection connection = new RepositoryConnection("org", "acme/repo", 1, "main", "forgeloop", "GENERIC", List.of("unit"), 10);
        connection.replaceVerificationPolicies(List.of(new VerificationPolicySpec("unit", "CONTAINER", image, List.of("npm", "test"), "NONE", 300, true, "ALL")));

        connection.replaceVerificationPolicies(List.of(new VerificationPolicySpec("unit", "CONTAINER", image, List.of("npm", "run", "check"), "EGRESS", 600, true, "ALL")));

        assertEquals(3, connection.getPolicyRevision());
        assertEquals(List.of("npm", "run", "check"), connection.getVerificationPolicies().getFirst().command());
        assertNull(connection.getVerificationPolicies().getFirst().testReport());
        assertEquals(1, connection.getVerificationPolicies().size());
    }

    @Test
    void testFirstRequiresRequiredJUnitReportingGateAndCannotWeakenIt() {
        String image = "node@sha256:" + "a".repeat(64);
        RepositoryConnection connection = new RepositoryConnection("org", "acme/repo", 1, "main", "forgeloop", "GENERIC", List.of("unit"), 10);
        connection.replaceVerificationPolicies(List.of(new VerificationPolicySpec("unit", "CONTAINER", image,
                List.of("npm", "test", "--junitxml=/forgeloop/test-report/unit.xml"), "NONE", 300, true, "ALL", "JUNIT_XML")));
        assertEquals("JUNIT_XML", connection.getVerificationPolicies().getFirst().testReport());
        int beforeEnable = connection.getPolicyRevision();

        connection.configureTestFirst("unit", List.of("src/test/**", "**/*.test.ts"));

        assertEquals("unit", connection.getTestFirstGate());
        assertEquals(List.of("src/test/**", "**/*.test.ts"), connection.getTestPathGlobs());
        assertEquals(beforeEnable + 1, connection.getPolicyRevision());
        assertThrows(IllegalStateException.class, () -> connection.replaceVerificationPolicies(List.of(
                new VerificationPolicySpec("unit", "CONTAINER", image, List.of("npm", "test"), "NONE", 300, true, "ALL"))));
        connection.configureTestFirst(null, List.of("ignored"));
        assertEquals(List.of(), connection.getTestPathGlobs());
    }

    @Test
    void testFirstRejectsInvalidGlobsAndOptionalOrNonReportingGates() {
        String image = "node@sha256:" + "a".repeat(64);
        RepositoryConnection connection = new RepositoryConnection("org", "acme/repo", 1, "main", "forgeloop", "GENERIC", List.of("unit"), 10);
        connection.replaceVerificationPolicies(List.of(new VerificationPolicySpec("unit", "CONTAINER", image,
                List.of("npm", "test"), "NONE", 300, true, "ALL")));
        assertThrows(IllegalArgumentException.class, () -> connection.configureTestFirst("unit", List.of("../tests/**")));
        assertThrows(IllegalArgumentException.class, () -> connection.configureTestFirst("unit", List.of("src/**", "src/**")));
        assertThrows(IllegalArgumentException.class, () -> connection.configureTestFirst("missing", List.of("tests/**")));
    }
}
