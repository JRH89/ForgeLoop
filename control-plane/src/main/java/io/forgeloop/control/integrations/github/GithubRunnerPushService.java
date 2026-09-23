package io.forgeloop.control.integrations.github;

import io.forgeloop.control.application.AuditLedgerService;
import io.forgeloop.control.application.RepositoryConnectionService;
import io.forgeloop.control.application.TaskLeaseService;
import io.forgeloop.control.domain.DeliveryTask;
import io.forgeloop.control.domain.DeliveryTaskRepository;
import io.forgeloop.control.domain.GithubPublication;
import io.forgeloop.control.domain.GithubPublicationRepository;
import io.forgeloop.control.domain.RepositoryConnection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Binds a short-lived GitHub push credential and reported head to one active integration lease. */
@Service
public class GithubRunnerPushService {
    private final TaskLeaseService leases; private final DeliveryTaskRepository tasks; private final RepositoryConnectionService connections;
    private final GithubPublicationRepository publications; private final GithubApi github; private final AuditLedgerService audit;
    public GithubRunnerPushService(TaskLeaseService leases, DeliveryTaskRepository tasks, RepositoryConnectionService connections, GithubPublicationRepository publications, GithubApi github, AuditLedgerService audit) { this.leases=leases;this.tasks=tasks;this.connections=connections;this.publications=publications;this.github=github;this.audit=audit; }
    @Transactional public GithubPushGrant grant(String leaseId,String runnerId,String nonce){DeliveryTask task=integrationTask(leases.requireActiveTaskId(leaseId,runnerId,nonce));RepositoryConnection connection=connections.requireEnabled(task.getRepository());GithubPublication publication=publications.findByFeatureRunId(task.getRun().getId()).orElseGet(()->publications.save(new GithubPublication(task.getRun().getId(),task.getRepository(),"forgeloop/"+task.getRun().getId(),task.getRun().getId())));String remoteHead=github.getBranchHead(connection.getInstallationId(),task.getRepository(),publication.getBranch());if(publication.getHeadSha()!=null&&!publication.getHeadSha().equals(remoteHead))throw new IllegalStateException("GitHub publication branch changed outside ForgeLoop");audit.record("RUNNER_GITHUB_PUSH_GRANTED","FEATURE_RUN",task.getRun().getId(),runnerId+"|"+publication.getBranch());return new GithubPushGrant(task.getRepository(),publication.getBranch(),remoteHead,github.issueInstallationToken(connection.getInstallationId()));}
    @Transactional public io.forgeloop.control.domain.TaskLease complete(String leaseId,String runnerId,String nonce,String integratedSha){DeliveryTask task=integrationTask(leases.requireActiveTaskId(leaseId,runnerId,nonce));RepositoryConnection connection=connections.requireEnabled(task.getRepository());GithubPublication publication=publications.findByFeatureRunId(task.getRun().getId()).orElseThrow(()->new IllegalStateException("GitHub push grant was not issued"));String remoteHead=github.getBranchHead(connection.getInstallationId(),task.getRepository(),publication.getBranch());if(!integratedSha.equals(remoteHead))throw new IllegalStateException("GitHub branch head does not match the integrated commit");io.forgeloop.control.domain.TaskLease completed=leases.completeIntegration(leaseId,runnerId,nonce,integratedSha);publication.recordHeadSha(integratedSha);audit.record("RUNNER_GITHUB_BRANCH_PUSHED","FEATURE_RUN",task.getRun().getId(),publication.getBranch()+"|"+integratedSha);return completed;}
    private DeliveryTask integrationTask(String taskId){DeliveryTask task=tasks.findById(taskId).orElseThrow(()->new IllegalArgumentException("Task not found"));if(!"INTEGRATION".equals(task.getRole()))throw new IllegalStateException("GitHub push grants require an integration task");return task;}
}
