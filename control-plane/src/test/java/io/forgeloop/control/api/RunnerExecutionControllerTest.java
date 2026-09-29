package io.forgeloop.control.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.forgeloop.control.application.RunnerService;
import io.forgeloop.control.application.RunnerDispatchService;
import io.forgeloop.control.application.TaskLeaseService;
import io.forgeloop.control.application.VerificationEvidenceSubmission;
import io.forgeloop.control.application.ProviderAttemptSubmission;
import io.forgeloop.control.application.ReviewCriterionSubmission;
import io.forgeloop.control.application.ReviewEvidenceSubmission;
import io.forgeloop.control.domain.VerificationEvidence;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunnerExecutionControllerTest {
    private final TaskLeaseService leases = mock(TaskLeaseService.class);
    private final RunnerService runners = mock(RunnerService.class);
    private final RunnerDispatchService dispatch = mock(RunnerDispatchService.class);
    private final io.forgeloop.control.integrations.github.GithubRunnerPushService githubPush = mock(io.forgeloop.control.integrations.github.GithubRunnerPushService.class);
    private final io.forgeloop.control.application.ReviewEvidenceService reviews = mock(io.forgeloop.control.application.ReviewEvidenceService.class);
    private final io.forgeloop.control.application.RepositoryScanService scans = mock(io.forgeloop.control.application.RepositoryScanService.class);
    private final io.forgeloop.control.application.RepositoryIssueProposalService issueProposals = mock(io.forgeloop.control.application.RepositoryIssueProposalService.class);
    private final io.forgeloop.control.application.IssueConversationService issueConversations = mock(io.forgeloop.control.application.IssueConversationService.class);
    private final io.forgeloop.control.application.TestCheckEvidenceService testChecks = mock(io.forgeloop.control.application.TestCheckEvidenceService.class);
    private final RunnerExecutionController controller = new RunnerExecutionController(leases, runners, dispatch,
            mock(io.forgeloop.control.application.TaskPlanningService.class), githubPush,
            mock(io.forgeloop.control.integrations.github.GithubRunnerCheckoutService.class), reviews,
            scans, issueProposals, issueConversations, testChecks);

    @Test void authenticatesRunnerBeforeClaimingRepositoryScan() {
        io.forgeloop.control.domain.Runner runner = mock(io.forgeloop.control.domain.Runner.class);
        org.mockito.Mockito.when(runners.authenticated("runner-1", "runner-credential")).thenReturn(runner);
        controller.claimRepositoryScan("runner-1", "runner-credential");
        verify(runners).authenticated("runner-1", "runner-credential");
        verify(scans).claim(runner);
    }

    @Test void authenticatesRunnerBeforeCompletingRepositoryScan() {
        io.forgeloop.control.domain.Runner runner = mock(io.forgeloop.control.domain.Runner.class);
        org.mockito.Mockito.when(runners.authenticated("runner-1", "runner-credential")).thenReturn(runner);
        var result = new io.forgeloop.control.application.RepositoryScanResultInput(false, null, null, null,
                0, 0, 0, false, "safe", List.of());
        controller.completeRepositoryScan("scan-1", "runner-1", "runner-credential", result);
        verify(runners).authenticated("runner-1", "runner-credential");
        verify(scans).complete("scan-1", runner, result);
    }

    @Test void authenticatesRunnerBeforeClaimingAnExplicitIssueProposal() {
        io.forgeloop.control.domain.Runner runner = mock(io.forgeloop.control.domain.Runner.class);
        org.mockito.Mockito.when(runners.authenticated("runner-1", "runner-credential")).thenReturn(runner);
        controller.claimRepositoryIssueProposal("runner-1", "runner-credential");
        verify(runners).authenticated("runner-1", "runner-credential");
        verify(issueProposals).claim(runner);
    }

    @Test void authenticatesRunnerBeforeCompletingAnIssueProposal() {
        io.forgeloop.control.domain.Runner runner = mock(io.forgeloop.control.domain.Runner.class);
        org.mockito.Mockito.when(runners.authenticated("runner-1", "runner-credential")).thenReturn(runner);
        var input = new io.forgeloop.control.application.RepositoryIssueProposalResultInput(false, null, null, null,
                null, null, 0, 0, 0, false);
        controller.completeRepositoryIssueProposal("proposal-1", "runner-1", "runner-credential", input);
        verify(runners).authenticated("runner-1", "runner-credential");
        verify(issueProposals).complete("proposal-1", runner, input);
    }

    @Test void authenticatesRunnerBeforeClaimingAnIssueChatTurn() {
        io.forgeloop.control.domain.Runner runner = mock(io.forgeloop.control.domain.Runner.class);
        org.mockito.Mockito.when(runners.authenticated("runner-1", "runner-credential")).thenReturn(runner);
        controller.claimIssueChatTurn("runner-1", "runner-credential");
        verify(runners).authenticated("runner-1", "runner-credential");
        verify(issueConversations).claim(runner);
    }

    @Test void authenticatesRunnerBeforeCompletingAnIssueChatTurn() {
        io.forgeloop.control.domain.Runner runner = mock(io.forgeloop.control.domain.Runner.class);
        org.mockito.Mockito.when(runners.authenticated("runner-1", "runner-credential")).thenReturn(runner);
        var input = new io.forgeloop.control.application.IssueChatTurnResultInput(false, null, null, null, null,
                null, null, 0, 0, 0, false);
        controller.completeIssueChatTurn("conversation-1", "runner-1", "runner-credential", input);
        verify(runners).authenticated("runner-1", "runner-credential");
        verify(issueConversations).complete("conversation-1", runner, input);
    }

    @Test
    void authenticatesRunnerBeforeClaimingLease() {
        controller.claimTaskLease("task-1", "runner-1", "runner-credential");

        verify(runners).authenticated("runner-1", "runner-credential");
        verify(leases).claim("task-1", "runner-1");
    }

    @Test
    void authenticatesRunnerBeforeRecordingTestCheckEvidence() {
        var input = new io.forgeloop.control.application.TestCheckEvidenceSubmission("artifact://org/run/task/lease/red-evidence.json", "a".repeat(64));
        org.mockito.Mockito.when(runners.authenticated("runner-1", "runner-credential")).thenReturn(mock(io.forgeloop.control.domain.Runner.class));

        controller.recordTestCheckEvidence("lease-1", "runner-1", "nonce-1", "runner-credential", input);

        verify(runners).authenticated("runner-1", "runner-credential");
        verify(testChecks).record("lease-1", "runner-1", "nonce-1", input);
    }

    @Test
    void authenticatesRunnerBeforeAcknowledgingLease() {
        controller.acknowledgeTaskLease("lease-1", "runner-1", "nonce", "runner-credential");

        verify(runners).authenticated("runner-1", "runner-credential");
        verify(leases).acknowledge("lease-1", "runner-1", "nonce");
    }

    @Test
    void authenticatesRunnerBeforeRenewingLease() {
        controller.renewTaskLease("lease-1", "runner-1", "nonce", "runner-credential");

        verify(runners).authenticated("runner-1", "runner-credential");
        verify(leases).renew("lease-1", "runner-1", "nonce");
    }

    @Test
    void authenticatesRunnerBeforeHoldingLease() {
        controller.holdTaskLease("lease-1", "runner-1", "nonce", "runner-credential",
                "LOOP_BUDGET_EXHAUSTED", "The loop reached its configured limit.");

        verify(runners).authenticated("runner-1", "runner-credential");
        verify(leases).hold("lease-1", "runner-1", "nonce", "LOOP_BUDGET_EXHAUSTED",
                "The loop reached its configured limit.");
    }

    @Test
    void forwardsAnOptionalCompletionCategoryAfterRunnerAuthentication() {
        controller.completeTaskLease("lease-1", "runner-1", "nonce", "runner-credential", false,
                "LOOP_HARNESS_FAILURE");

        verify(runners).authenticated("runner-1", "runner-credential");
        verify(leases).complete("lease-1", "runner-1", "nonce", false, "LOOP_HARNESS_FAILURE");
    }

    @Test
    void authenticatesRunnerBeforeRecordingEvidence() {
        Instant time = Instant.parse("2026-01-01T00:00:00Z");
        String outputDigest = VerificationEvidence.digest("ok");
        String bundleDigest = VerificationEvidence.bundleDigest("CONTAINER", "unit", "node@sha256:" + "a".repeat(64), List.of("node", "--version"), 0, false, outputDigest, time, time, null);
        VerificationEvidenceSubmission report = new VerificationEvidenceSubmission("CONTAINER", "unit", "node@sha256:" + "a".repeat(64), List.of("node", "--version"), 0, false, "ok", time, time, null, outputDigest, bundleDigest);

        controller.recordVerificationEvidence("lease-1", "runner-1", "nonce", "runner-credential", report);

        verify(runners).authenticated("runner-1", "runner-credential");
        verify(leases).recordEvidence("lease-1", "runner-1", "nonce", report);
    }

    @Test
    void authenticatesRunnerBeforeRecordingProviderAttempt() {
        ProviderAttemptSubmission report = new ProviderAttemptSubmission("anthropic", "claude", "a".repeat(64), 1, 2, 1, "SUCCEEDED", 0, false, false, "COMPLETED");

        controller.recordProviderAttempt("lease-1", "runner-1", "nonce", "runner-credential", report);

        verify(runners).authenticated("runner-1", "runner-credential");
        verify(leases).recordProviderAttempt("lease-1", "runner-1", "nonce", report);
    }

    @Test
    void authenticatesRunnerBeforeCompletingProviderWork() {
        controller.completeProviderTaskLease("lease-1", "runner-1", "nonce", "runner-credential", "a".repeat(40));

        verify(runners).authenticated("runner-1", "runner-credential");
        verify(leases).completeProviderWork("lease-1", "runner-1", "nonce", "a".repeat(40));
    }

    @Test void authenticatesRunnerBeforeIssuingPushCredential() {
        controller.issueGithubPushGrant("lease-1", "runner-1", "nonce", "runner-credential");
        verify(runners).authenticated("runner-1", "runner-credential"); verify(githubPush).grant("lease-1", "runner-1", "nonce");
    }

    @Test
    void authenticatesRunnerBeforeRecordingReviewEvidence() {
        ReviewEvidenceSubmission report = new ReviewEvidenceSubmission(
                true,
                "Implementation satisfies the criterion.",
                List.of(new ReviewCriterionSubmission(
                        "A user can create a ticket.",
                        "PASS",
                        "The create-ticket path is covered by an automated test.")));

        controller.recordReviewEvidence("lease-1", "runner-1", "nonce", "runner-credential", report);

        verify(runners).authenticated("runner-1", "runner-credential");
        verify(reviews).record("lease-1", "runner-1", "nonce", report);
    }

    @Test void authenticatesRunnerBeforeRecordingPushedCommit() {
        controller.completeGithubPush("lease-1", "runner-1", "nonce", "runner-credential", "b".repeat(40));
        verify(runners).authenticated("runner-1", "runner-credential");
        verify(githubPush).complete("lease-1", "runner-1", "nonce", "b".repeat(40));
    }
}
