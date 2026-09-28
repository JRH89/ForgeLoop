package io.forgeloop.control.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.forgeloop.control.domain.RepositoryConnection;
import io.forgeloop.control.domain.RepositoryConnectionRepository;
import io.forgeloop.control.domain.RepositoryScan;
import io.forgeloop.control.domain.RepositoryScanFinding;
import io.forgeloop.control.domain.RepositoryScanRepository;
import io.forgeloop.control.domain.Runner;
import io.forgeloop.control.integrations.github.GithubApi;
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
    private final ProviderActivityService providerActivities = Mockito.mock(ProviderActivityService.class);
    private final RepositoryScanService service = new RepositoryScanService(scans, connections, operators, github, audit, providerActivities);

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

    @Test void successfulRunnerScanRecordsOneMetadataOnlyProviderActivity() {
        RepositoryScan scan = new RepositoryScan("org-1", "acme/project", "main", "admin-1");
        ReflectionTestUtils.setField(scan, "id", "scan-1");
        scan.claim("runner-1");
        when(scans.lockById("scan-1")).thenReturn(Optional.of(scan));
        Runner runner = new Runner("org-1", "customer-runner", "0.1.0", List.of("git", "provider"), "a".repeat(64));
        ReflectionTestUtils.setField(runner, "id", "runner-1");
        RepositoryScanResultInput result = new RepositoryScanResultInput(true, "a".repeat(40), "openai", "gpt-test",
                25, 10, 100, true, null, List.of());

        service.complete("scan-1", runner, result);

        verify(providerActivities).record("org-1", "REPOSITORY_SCAN", "acme/project", "scan-1", "openai", "gpt-test", 25, 10, 100, true);
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
