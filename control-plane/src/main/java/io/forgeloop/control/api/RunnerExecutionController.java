package io.forgeloop.control.api;

import io.forgeloop.control.application.LeaseGrant;
import io.forgeloop.control.application.RunnerDispatchService;
import io.forgeloop.control.application.RunnerService;
import io.forgeloop.control.application.TaskLeaseService;
import io.forgeloop.control.application.VerificationEvidenceSubmission;
import io.forgeloop.control.application.ProviderAttemptSubmission;
import io.forgeloop.control.application.TaskPlanSubmission;
import io.forgeloop.control.application.TaskPlanningService;
import io.forgeloop.control.application.ReviewEvidenceService;
import io.forgeloop.control.application.ReviewEvidenceSubmission;
import io.forgeloop.control.domain.FeatureRun;
import io.forgeloop.control.domain.DeliveryTask;
import io.forgeloop.control.domain.ProviderAttempt;
import io.forgeloop.control.domain.TaskLease;
import io.forgeloop.control.domain.VerificationEvidence;
import io.forgeloop.control.domain.ReviewEvidence;
import io.forgeloop.control.integrations.github.GithubPushGrant;
import io.forgeloop.control.integrations.github.GithubCheckoutGrant;
import io.forgeloop.control.integrations.github.GithubRunnerCheckoutService;
import io.forgeloop.control.integrations.github.GithubRunnerPushService;
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
    private final TaskPlanningService planning;
    private final GithubRunnerPushService githubPush;
    private final GithubRunnerCheckoutService githubCheckout;
    private final ReviewEvidenceService reviews;

    public RunnerExecutionController(TaskLeaseService leases, RunnerService runners, RunnerDispatchService dispatch, TaskPlanningService planning, GithubRunnerPushService githubPush, GithubRunnerCheckoutService githubCheckout, ReviewEvidenceService reviews) {
        this.leases = leases; this.runners = runners; this.dispatch = dispatch; this.planning = planning; this.githubPush = githubPush; this.githubCheckout=githubCheckout; this.reviews = reviews;
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
    @MutationMapping public TaskLease completeProviderTaskLease(@Argument String leaseId, @Argument String runnerId, @Argument String nonce, @Argument String credential, @Argument String changeSha) {
        runners.authenticated(runnerId, credential); return leases.completeProviderWork(leaseId, runnerId, nonce, changeSha);
    }
    @MutationMapping public GithubPushGrant issueGithubPushGrant(@Argument String leaseId, @Argument String runnerId, @Argument String nonce, @Argument String credential) {
        runners.authenticated(runnerId, credential); return githubPush.grant(leaseId, runnerId, nonce);
    }
    @MutationMapping public GithubCheckoutGrant issueGithubCheckoutGrant(@Argument String leaseId,@Argument String runnerId,@Argument String nonce,@Argument String credential){runners.authenticated(runnerId,credential);return githubCheckout.grant(leaseId,runnerId,nonce);}
    @MutationMapping public TaskLease completeGithubPush(@Argument String leaseId, @Argument String runnerId, @Argument String nonce, @Argument String credential, @Argument String integratedSha) {
        runners.authenticated(runnerId, credential); return githubPush.complete(leaseId, runnerId, nonce, integratedSha);
    }
    @MutationMapping public VerificationEvidence recordVerificationEvidence(@Argument String leaseId, @Argument String runnerId,
                                                                              @Argument String nonce, @Argument String credential,
                                                                              @Argument VerificationEvidenceSubmission input) {
        runners.authenticated(runnerId, credential);
        return leases.recordEvidence(leaseId, runnerId, nonce, input);
    }
    @MutationMapping public ProviderAttempt recordProviderAttempt(@Argument String leaseId, @Argument String runnerId,
                                                                   @Argument String nonce, @Argument String credential,
                                                                   @Argument ProviderAttemptSubmission input) {
        runners.authenticated(runnerId, credential);
        return leases.recordProviderAttempt(leaseId, runnerId, nonce, input);
    }
    @MutationMapping public ReviewEvidence recordReviewEvidence(@Argument String leaseId, @Argument String runnerId,
                                                                 @Argument String nonce, @Argument String credential,
                                                                 @Argument ReviewEvidenceSubmission input) {
        runners.authenticated(runnerId, credential);
        return reviews.record(leaseId, runnerId, nonce, input);
    }
    @MutationMapping public FeatureRun submitTaskPlan(@Argument String leaseId, @Argument String runnerId,
                                                       @Argument String nonce, @Argument String credential,
                                                       @Argument TaskPlanSubmission input) {
        runners.authenticated(runnerId, credential);
        return planning.submit(leases.requireActiveTaskId(leaseId, runnerId, nonce), input);
    }
}
