package io.forgeloop.control.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.forgeloop.control.application.RunnerService;
import io.forgeloop.control.application.RunnerDispatchService;
import io.forgeloop.control.application.TaskLeaseService;
import io.forgeloop.control.application.VerificationEvidenceSubmission;
import io.forgeloop.control.application.ProviderAttemptSubmission;
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
        VerificationEvidenceSubmission report = new VerificationEvidenceSubmission("CONTAINER", "unit", "node:22-alpine", "node --version", 0, false, "ok");

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
