package io.forgeloop.control.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.forgeloop.control.domain.RepositoryConnection;
import io.forgeloop.control.domain.RepositoryConnectionRepository;
import io.forgeloop.control.domain.RepositoryIssueProposal;
import io.forgeloop.control.domain.RepositoryIssueProposalRepository;
import io.forgeloop.control.domain.RepositoryScan;
import io.forgeloop.control.domain.RepositoryScanFinding;
import io.forgeloop.control.domain.RepositoryScanRepository;
import io.forgeloop.control.domain.Runner;
import io.forgeloop.control.integrations.github.GithubApi;
import io.forgeloop.control.integrations.github.GithubIssueReceipt;
import io.forgeloop.control.security.OperatorContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class RepositoryIssueProposalServiceTest {
    private final RepositoryScanRepository scans = Mockito.mock(RepositoryScanRepository.class);
    private final RepositoryIssueProposalRepository proposals = Mockito.mock(RepositoryIssueProposalRepository.class);
    private final RepositoryConnectionRepository connections = Mockito.mock(RepositoryConnectionRepository.class);
    private final OperatorContext operators = Mockito.mock(OperatorContext.class);
    private final GithubApi github = Mockito.mock(GithubApi.class);
    private final AuditLedgerService audit = Mockito.mock(AuditLedgerService.class);
    private final ProviderActivityService activities = Mockito.mock(ProviderActivityService.class);
    private final RepositoryIssueProposalService service = new RepositoryIssueProposalService(
            scans, proposals, connections, operators, github, audit, activities);

    @Test void generationIsExplicitAndQueuedOnlyForACompletedTenantFinding() {
        RepositoryScan scan = completedScan();
        when(operators.organizationId()).thenReturn("org-1");
        when(operators.subject()).thenReturn("admin-1");
        when(scans.lockByIdAndOrganizationId("scan-1", "org-1")).thenReturn(Optional.of(scan));
        when(proposals.save(any(RepositoryIssueProposal.class))).thenAnswer(call -> {
            RepositoryIssueProposal proposal = call.getArgument(0);
            ReflectionTestUtils.setField(proposal, "id", "proposal-1");
            return proposal;
        });

        RepositoryIssueProposal result = service.request("scan-1", "finding-1");

        verify(operators).requireAdministrator();
        verify(proposals).existsByFinding_IdAndStatusIn("finding-1", List.of("PENDING", "RUNNING", "READY"));
        assertEquals("PENDING", result.getStatus());
        assertEquals("acme/project", result.getRepository());
        verify(github, never()).createIssue(Mockito.anyLong(), any(String.class), any(String.class), any(String.class));
    }

    @Test void runnerClaimIsOrganizationScopedAndExpiredClaimsCanBeRetried() {
        RepositoryIssueProposal proposal = proposal();
        Runner runner = runner("org-1", "runner-1");
        when(proposals.lockClaimableForOrganization(Mockito.eq("org-1"), any(), any())).thenReturn(List.of(proposal));

        RepositoryIssueProposalGrant grant = service.claim(runner);

        assertEquals("proposal-1", grant.id());
        assertEquals("a".repeat(40), grant.commitSha());
        assertEquals("The validator accepts an empty string.", grant.evidence());
        assertEquals("RUNNING", proposal.getStatus());
    }

    @Test void staleProposalLeaseIsRequeuedToAnotherRunner() {
        RepositoryIssueProposal proposal = proposal();
        proposal.claim("runner-old");
        ReflectionTestUtils.setField(proposal, "startedAt", Instant.now().minus(31, ChronoUnit.MINUTES));
        when(proposals.lockClaimableForOrganization(Mockito.eq("org-1"), any(), any())).thenReturn(List.of(proposal));

        RepositoryIssueProposalGrant grant = service.claim(runner("org-1", "runner-new"));

        assertEquals("proposal-1", grant.id());
        assertEquals("RUNNING", proposal.getStatus());
        verify(audit).record("ISSUE_PROPOSAL_REQUEUED", "REPOSITORY_ISSUE_PROPOSAL", "proposal-1", "expired-runner-claim");
    }

    @Test void successfulGenerationRecordsCostMetadataBeforeAnyIssueIsPublished() {
        RepositoryIssueProposal proposal = proposal();
        proposal.claim("runner-1");
        when(proposals.lockByIdAndOrganizationId("proposal-1", "org-1")).thenReturn(Optional.of(proposal));
        RepositoryIssueProposalResultInput input = new RepositoryIssueProposalResultInput(true, "Reject empty values",
                "Empty input reaches persistence without validation.", List.of("Reject empty input.", "Add a regression test."),
                "openai", "gpt-test", 120, 30, 500, true);

        RepositoryIssueProposal result = service.complete("proposal-1", runner("org-1", "runner-1"), input);

        assertEquals("READY", result.getStatus());
        assertEquals("Reject empty values", result.getProposedTitle());
        assertEquals(2, result.getAcceptanceCriteria().size());
        assertEquals(null, result.getIssueUrl());
        verify(activities).record("org-1", "ISSUE_SPECIFICATION", "acme/project", "proposal-1",
                "openai", "gpt-test", 120, 30, 500, true);
        verify(github, never()).createIssue(Mockito.anyLong(), any(String.class), any(String.class), any(String.class));
    }

    @Test void billableMalformedDraftStillAppearsAsUnpricedOrPricedUsage() {
        RepositoryIssueProposal proposal = proposal();
        proposal.claim("runner-1");
        when(proposals.lockByIdAndOrganizationId("proposal-1", "org-1")).thenReturn(Optional.of(proposal));

        RepositoryIssueProposal failed = service.complete("proposal-1", runner("org-1", "runner-1"),
                new RepositoryIssueProposalResultInput(false, null, null, null, "anthropic", "claude-test", 90, 10, 0, false));

        assertEquals("FAILED", failed.getStatus());
        assertEquals("The runner could not generate this issue proposal.", failed.getFailureSummary());
        verify(activities).record("org-1", "ISSUE_SPECIFICATION", "acme/project", "proposal-1",
                "anthropic", "claude-test", 90, 10, 0, false);
    }

    @Test void failedOrRejectedProposalCanBeRegeneratedAsANewBillableAttempt() {
        RepositoryScan scan = completedScan();
        when(operators.organizationId()).thenReturn("org-1");
        when(operators.subject()).thenReturn("admin-1");
        when(scans.lockByIdAndOrganizationId("scan-1", "org-1")).thenReturn(Optional.of(scan));
        when(proposals.existsByFinding_IdAndStatusIn("finding-1", List.of("PENDING", "RUNNING", "READY"))).thenReturn(false);
        when(proposals.save(any(RepositoryIssueProposal.class))).thenAnswer(call -> {
            RepositoryIssueProposal next = call.getArgument(0);
            ReflectionTestUtils.setField(next, "id", "proposal-2");
            return next;
        });

        RepositoryIssueProposal retry = service.request("scan-1", "finding-1");

        assertEquals("PENDING", retry.getStatus());
        assertEquals("proposal-2", retry.getId());
        verify(proposals).existsByFinding_IdAndStatusIn("finding-1", List.of("PENDING", "RUNNING", "READY"));
    }

    @Test void onlyAnEditedReadyProposalCanPublishAndRejectingNeverCreatesAnIssue() {
        RepositoryIssueProposal proposal = readyProposal();
        when(operators.organizationId()).thenReturn("org-1");
        when(proposals.lockByIdAndOrganizationId("proposal-1", "org-1")).thenReturn(Optional.of(proposal));
        when(connections.findByRepository("acme/project")).thenReturn(Optional.of(repository()));
        when(github.createIssue(Mockito.eq(44L), Mockito.eq("acme/project"), Mockito.eq("Reviewed issue title"), any()))
                .thenReturn(new GithubIssueReceipt(29, "https://github.com/acme/project/issues/29"));

        RepositoryIssueProposal published = service.approve("proposal-1", "Reviewed issue title", "Edited and approved body.",
                List.of("Keep the issue scoped.", "Verify with an integration test."));

        assertEquals("APPROVED", published.getStatus());
        assertEquals("https://github.com/acme/project/issues/29", published.getIssueUrl());
        verify(github).createIssue(Mockito.eq(44L), Mockito.eq("acme/project"), Mockito.eq("Reviewed issue title"), Mockito.argThat(body ->
                body.contains("Edited and approved body.") && body.contains("Repository snapshot")
                        && body.contains("- [ ] Keep the issue scoped.")));
        verify(audit).record("ISSUE_PROPOSAL_APPROVED", "REPOSITORY_ISSUE_PROPOSAL", "proposal-1",
                "29|https://github.com/acme/project/issues/29");

        RepositoryIssueProposal rejected = readyProposal();
        when(proposals.lockByIdAndOrganizationId("proposal-1", "org-1")).thenReturn(Optional.of(rejected));
        service.reject("proposal-1", "Not in scope");
        assertEquals("REJECTED", rejected.getStatus());
        verify(github, Mockito.times(1)).createIssue(Mockito.anyLong(), any(String.class), any(String.class), any(String.class));
    }

    @Test void rejectsCrossOrganizationProposalAndUnsafeApprovalInputs() {
        when(operators.organizationId()).thenReturn("org-2");
        when(proposals.lockByIdAndOrganizationId("proposal-1", "org-2")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.approve("proposal-1", "title", "body", List.of("criteria")));
        verify(github, never()).createIssue(Mockito.anyLong(), any(String.class), any(String.class), any(String.class));

        RepositoryIssueProposal ready = readyProposal();
        when(operators.organizationId()).thenReturn("org-1");
        when(proposals.lockByIdAndOrganizationId("proposal-1", "org-1")).thenReturn(Optional.of(ready));
        assertThrows(IllegalArgumentException.class, () -> service.approve("proposal-1", "title", "body", List.of()));
        verify(github, never()).createIssue(Mockito.anyLong(), any(String.class), any(String.class), any(String.class));
    }

    @Test void runnerCannotCompleteAnotherOrganizationsProposal() {
        when(proposals.lockByIdAndOrganizationId("proposal-1", "org-2")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.complete("proposal-1", runner("org-2", "runner-2"),
                new RepositoryIssueProposalResultInput(false, null, null, null, null, null, 0, 0, 0, false)));

        verify(activities, never()).record(any(String.class), any(String.class), any(String.class), any(String.class),
                any(String.class), any(String.class), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyBoolean());
    }

    private static RepositoryIssueProposal proposal() {
        RepositoryIssueProposal proposal = new RepositoryIssueProposal("org-1", finding(completedScan()), "admin-1");
        ReflectionTestUtils.setField(proposal, "id", "proposal-1");
        return proposal;
    }

    private static RepositoryIssueProposal readyProposal() {
        RepositoryIssueProposal proposal = proposal();
        proposal.claim("runner-1");
        proposal.complete("runner-1", "Reject empty input", "Empty values pass validation.", List.of("Reject empty input."),
                "openai", "gpt-test", 20, 10, 100, true);
        return proposal;
    }

    private static RepositoryScan completedScan() {
        RepositoryScan scan = new RepositoryScan("org-1", "acme/project", "main", "admin-1");
        ReflectionTestUtils.setField(scan, "id", "scan-1");
        scan.claim("runner-1");
        scan.complete("runner-1", "a".repeat(40), "openai", "gpt-test", 10, 5, 20, true,
                List.of(new RepositoryScanFinding("HIGH", "Reject empty input", "Empty values pass validation.",
                        "Invalid records may be stored.", "The validator accepts an empty string.", "src/Validator.java", "Reject empty input.")));
        ReflectionTestUtils.setField(scan.getFindings().getFirst(), "id", "finding-1");
        return scan;
    }

    private static RepositoryScanFinding finding(RepositoryScan scan) { return scan.getFindings().getFirst(); }
    private static Runner runner(String organizationId, String id) {
        Runner runner = new Runner(organizationId, "desktop", "0.1.0", List.of("provider", "git"), "a".repeat(64));
        ReflectionTestUtils.setField(runner, "id", id);
        return runner;
    }
    private static RepositoryConnection repository() {
        return new RepositoryConnection("org-1", "acme/project", 44, "main", "forgeloop", "JVM_REACT", List.of("compile"), 10);
    }
}
