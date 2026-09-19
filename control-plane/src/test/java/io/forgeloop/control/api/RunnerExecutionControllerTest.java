package io.forgeloop.control.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.forgeloop.control.application.RunnerService;
import io.forgeloop.control.application.TaskLeaseService;
import org.junit.jupiter.api.Test;

class RunnerExecutionControllerTest {
    private final TaskLeaseService leases = mock(TaskLeaseService.class);
    private final RunnerService runners = mock(RunnerService.class);
    private final RunnerExecutionController controller = new RunnerExecutionController(leases, runners);

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
}
