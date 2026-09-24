package io.forgeloop.control.integrations.github;
import io.forgeloop.control.application.*;import io.forgeloop.control.domain.*;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
/** Issues installation credentials only for the repository bound to an active runner lease. */
@Service public class GithubRunnerCheckoutService {
 private final TaskLeaseService leases;private final DeliveryTaskRepository tasks;private final RepositoryConnectionService connections;private final GithubApi github;private final AuditLedgerService audit;
 public GithubRunnerCheckoutService(TaskLeaseService leases,DeliveryTaskRepository tasks,RepositoryConnectionService connections,GithubApi github,AuditLedgerService audit){this.leases=leases;this.tasks=tasks;this.connections=connections;this.github=github;this.audit=audit;}
 @Transactional public GithubCheckoutGrant grant(String leaseId,String runnerId,String nonce){String taskId=leases.requireActiveTaskId(leaseId,runnerId,nonce);DeliveryTask task=tasks.findById(taskId).orElseThrow(()->new IllegalArgumentException("Task not found"));RepositoryConnection connection=connections.requireEnabled(task.getRepository());audit.record("RUNNER_GITHUB_CHECKOUT_GRANTED","FEATURE_RUN",task.getRun().getId(),runnerId+"|"+task.getRepository());return new GithubCheckoutGrant(task.getRepository(),task.getBaseBranch(),github.issueInstallationToken(connection.getInstallationId()));}
}
