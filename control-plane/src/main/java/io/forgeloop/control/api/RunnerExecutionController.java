package io.forgeloop.control.api;

import io.forgeloop.control.application.LeaseGrant;
import io.forgeloop.control.application.RunnerDispatchService;
import io.forgeloop.control.application.RunnerService;
import io.forgeloop.control.application.TaskLeaseService;
import io.forgeloop.control.application.VerificationEvidenceSubmission;
import io.forgeloop.control.domain.DeliveryTask;
import io.forgeloop.control.domain.TaskLease;
import io.forgeloop.control.domain.VerificationEvidence;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/** Runner-facing operations remain separate from operator control-plane mutations. */
@Controller
public class RunnerExecutionController {
    private final TaskLeaseService leases;
    private final RunnerService runners;
    private final RunnerDispatchService dispatch;

    public RunnerExecutionController(TaskLeaseService leases, RunnerService runners, RunnerDispatchService dispatch) {
        this.leases = leases; this.runners = runners; this.dispatch = dispatch;
    }

    @QueryMapping public List<DeliveryTask> availableRunnerTasks(@Argument String runnerId, @Argument String credential) {
        return dispatch.available(runners.authenticated(runnerId, credential));
    }
    @MutationMapping public LeaseGrant claimTaskLease(@Argument String taskId, @Argument String runnerId, @Argument String credential) {
        runners.authenticated(runnerId, credential); return leases.claim(taskId, runnerId);
    }
    @MutationMapping public TaskLease acknowledgeTaskLease(@Argument String leaseId, @Argument String runnerId, @Argument String nonce, @Argument String credential) {
        runners.authenticated(runnerId, credential); return leases.acknowledge(leaseId, runnerId, nonce);
    }
    @MutationMapping public TaskLease completeTaskLease(@Argument String leaseId, @Argument String runnerId, @Argument String nonce, @Argument String credential, @Argument boolean passed) {
        runners.authenticated(runnerId, credential); return leases.complete(leaseId, runnerId, nonce, passed);
    }
    @MutationMapping public VerificationEvidence recordVerificationEvidence(@Argument String leaseId, @Argument String runnerId,
                                                                              @Argument String nonce, @Argument String credential,
                                                                              @Argument VerificationEvidenceSubmission input) {
        runners.authenticated(runnerId, credential);
        return leases.recordEvidence(leaseId, runnerId, nonce, input);
    }
}
