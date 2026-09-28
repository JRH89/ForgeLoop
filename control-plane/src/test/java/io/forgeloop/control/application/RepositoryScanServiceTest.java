package io.forgeloop.control.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.forgeloop.control.domain.RepositoryConnection;
import io.forgeloop.control.domain.RepositoryConnectionRepository;
import io.forgeloop.control.domain.RepositoryScan;
import io.forgeloop.control.domain.RepositoryScanFinding;
import io.forgeloop.control.domain.RepositoryScanRepository;
import io.forgeloop.control.integrations.github.GithubApi;
import io.forgeloop.control.integrations.github.GithubIssueReceipt;
import io.forgeloop.control.security.OperatorContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class RepositoryScanServiceTest {
    private final RepositoryScanRepository scans = Mockito.mock(RepositoryScanRepository.class);
    private final RepositoryConnectionRepository connections = Mockito.mock(RepositoryConnectionRepository.class);
    private final OperatorContext operators = Mockito.mock(OperatorContext.class);
    private final GithubApi github = Mockito.mock(GithubApi.class);
    private final AuditLedgerService audit = Mockito.mock(AuditLedgerService.class);
    private final RepositoryScanService service = new RepositoryScanService(scans, connections, operators, github, audit);

    @Test void manualScanRequestRequiresAdministratorAndStoresOrganizationScope() {
        when(operators.organizationId()).thenReturn("org-1");
        when(operators.subject()).thenReturn("admin-1");
        when(connections.findByRepository("acme/project")).thenReturn(Optional.of(repository("org-1")));
        when(scans.save(any(RepositoryScan.class))).thenAnswer(call -> call.getArgument(0));

        RepositoryScan requested = service.request("acme/project");

        verify(operators).requireAdministrator();
        verify(scans).existsByOrganizationIdAndRepositoryAndStatusIn("org-1", "acme/project", List.of("PENDING", "RUNNING"));
        assertEquals("org-1", requested.getOrganizationId());
        assertEquals("PENDING", requested.getStatus());
    }

    @Test void reviewedFindingCanCreateOneGitHubIssueOnly() {
        when(operators.organizationId()).thenReturn("org-1");
        when(scans.lockByIdAndOrganizationId("scan-1", "org-1")).thenReturn(Optional.of(completedScan()));
        when(connections.findByRepository("acme/project")).thenReturn(Optional.of(repository("org-1")));
        when(github.createIssue(Mockito.eq(44L), Mockito.eq("acme/project"), Mockito.eq("Handle expired sessions"), any()))
                .thenReturn(new GithubIssueReceipt(29, "https://github.com/acme/project/issues/29"));

        RepositoryScanFinding created = service.createIssue("scan-1", "finding-1");

        verify(operators).requireAdministrator();
        verify(github).createIssue(Mockito.eq(44L), Mockito.eq("acme/project"), Mockito.eq("Handle expired sessions"), Mockito.argThat(body ->
                body.contains("Repository snapshot") && body.contains("### Acceptance criteria") && body.contains("not labeled or assigned automatically")));
        verify(audit).record("REPOSITORY_SCAN_ISSUE_CREATED", "REPOSITORY_SCAN", "scan-1", "finding-1|29");
        assertEquals(29, created.getIssueNumber());

        assertThrows(IllegalStateException.class, () -> service.createIssue("scan-1", "finding-1"));
        verify(github, Mockito.times(1)).createIssue(Mockito.anyLong(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
    }

    @Test void findingFromAnotherOrganizationCannotBePublished() {
        when(operators.organizationId()).thenReturn("org-2");
        when(scans.lockByIdAndOrganizationId("scan-1", "org-2")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.createIssue("scan-1", "finding-1"));

        verify(github, never()).createIssue(Mockito.anyLong(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
    }

    private static RepositoryConnection repository(String organizationId) {
        return new RepositoryConnection(organizationId, "acme/project", 44, "main", "forgeloop", "JVM_REACT", List.of("compile"), 10);
    }

    private static RepositoryScan completedScan() {
        RepositoryScan scan = new RepositoryScan("org-1", "acme/project", "main", "admin-1");
        ReflectionTestUtils.setField(scan, "id", "scan-1");
        scan.claim("runner-1");
        RepositoryScanFinding finding = new RepositoryScanFinding("HIGH", "Handle expired sessions", "Expired sessions remain valid.",
                "An old session may continue to access protected data.", "SessionService.java does not check expiry.",
                "SessionService.java", "Reject sessions after expiration.");
        ReflectionTestUtils.setField(finding, "id", "finding-1");
        scan.complete("runner-1", "a".repeat(40), "openai", "gpt-test", 100, 20, 50, true,
                List.of(finding));
        return scan;
    }
}
