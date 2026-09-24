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
    private final RunnerExecutionController controller = new RunnerExecutionController(leases, runners, dispatch,
            mock(io.forgeloop.control.application.TaskPlanningService.class), githubPush,
            mock(io.forgeloop.control.integrations.github.GithubRunnerCheckoutService.class), reviews);

    @Test
    void authenticatesRunnerBeforeClaimingLease() {
        controller.claimTaskLease("task-1", "runner-1", "runner-credential");

        verify(runners).authenticated("runner-1", "runner-credential");
        verify(leases).claim("task-1", "runner-1");
    }

    @Test
    void authenticatesRunnerBeforeAcknowledgingLease() {
        controller.acknowledgeTaskLease("lease-1", "runner-1", "nonce", "runner-credential");

        verify(runners).authenticated("runner-1", "runner-credential");
        verify(leases).acknowledge("lease-1", "runner-1", "nonce");
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
