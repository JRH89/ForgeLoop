package io.forgeloop.control.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertEquals(1, connection.getVerificationPolicies().size());
    }
}
