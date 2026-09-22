package io.forgeloop.control.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.forgeloop.control.application.RunnerService;
import io.forgeloop.control.application.RunnerDispatchService;
import io.forgeloop.control.application.TaskLeaseService;
import io.forgeloop.control.application.VerificationEvidenceSubmission;
import io.forgeloop.control.application.ProviderAttemptSubmission;
import io.forgeloop.control.domain.VerificationEvidence;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunnerExecutionControllerTest {
    private final TaskLeaseService leases = mock(TaskLeaseService.class);
    private final RunnerService runners = mock(RunnerService.class);
    private final RunnerDispatchService dispatch = mock(RunnerDispatchService.class);
    private final RunnerExecutionController controller = new RunnerExecutionController(leases, runners, dispatch,
            mock(io.forgeloop.control.application.TaskPlanningService.class));

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

    @Test
    void authenticatesRunnerBeforeIntegrationCompletion() {
        controller.completeIntegrationTaskLease("lease-1", "runner-1", "nonce", "runner-credential", "b".repeat(40));

        verify(runners).authenticated("runner-1", "runner-credential");
        verify(leases).completeIntegration("lease-1", "runner-1", "nonce", "b".repeat(40));
    }
}
