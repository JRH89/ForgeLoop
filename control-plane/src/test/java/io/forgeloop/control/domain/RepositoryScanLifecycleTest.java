package io.forgeloop.control.domain;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class RepositoryScanLifecycleTest {
    @Test void scanIsBoundToItsClaimingRunnerAndStoresOnlyProposals() {
        RepositoryScan scan = new RepositoryScan("org", "owner/repo", "main", "admin");
        scan.claim("runner-a");
        assertThrows(IllegalStateException.class, () -> scan.complete("runner-b", "a".repeat(40), "openai", "model", 1, 1, 0, false, List.of()));
        RepositoryScanFinding finding = new RepositoryScanFinding("HIGH", "Validate input", "Reject invalid values.",
                "Malformed values can break the workflow.", "The validator returns true for an empty string.",
                "src/Validator.java", "Add a failing test for an empty value.");
        scan.complete("runner-a", "a".repeat(40), "openai", "model", 100, 20, 12, true, List.of(finding));
        assertEquals("COMPLETE", scan.getStatus());
        assertEquals(1, scan.getFindings().size());
        assertEquals("src/Validator.java", scan.getFindings().getFirst().getAffectedFiles().getFirst());
        assertTrue(scan.getFindings().getFirst().getIssueUrl() == null);
    }

    @Test void findingCanReceiveOnlyOneVerifiedGithubIssueReceipt() {
        RepositoryScanFinding finding = new RepositoryScanFinding("LOW", "Title", "Description", "Impact", "Evidence", "README.md", "Check it");
        finding.recordGithubIssue(7, "https://github.com/owner/repo/issues/7");
        assertThrows(IllegalStateException.class, () -> finding.recordGithubIssue(8, "https://github.com/owner/repo/issues/8"));
    }

    @Test void expiredRunnerClaimCanBeRequeuedButFreshClaimCannot() {
        RepositoryScan scan = new RepositoryScan("org", "owner/repo", "main", "admin");
        scan.claim("runner-a");
        assertFalse(scan.requeueExpiredClaim(java.time.Instant.now().minusSeconds(1)));
        assertTrue(scan.requeueExpiredClaim(java.time.Instant.now().plusSeconds(1)));
        assertEquals("PENDING", scan.getStatus());
        scan.claim("runner-b");
        assertEquals("RUNNING", scan.getStatus());
    }
}
