package io.forgeloop.control.integrations.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.forgeloop.control.application.AuditLedgerService;
import io.forgeloop.control.application.RepositoryConnectionService;
import io.forgeloop.control.application.TaskLeaseService;
import io.forgeloop.control.domain.DeliveryTask;
import io.forgeloop.control.domain.DeliveryTaskRepository;
import io.forgeloop.control.domain.FeatureRun;
import io.forgeloop.control.domain.GithubPublication;
import io.forgeloop.control.domain.GithubPublicationRepository;
import io.forgeloop.control.domain.RepositoryConnection;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GithubRunnerPushServiceTest {
    private final TaskLeaseService leases = mock(TaskLeaseService.class);
    private final DeliveryTaskRepository tasks = mock(DeliveryTaskRepository.class);
    private final RepositoryConnectionService connections = mock(RepositoryConnectionService.class);
    private final GithubPublicationRepository publications = mock(GithubPublicationRepository.class);
    private final GithubApi github = mock(GithubApi.class);
    private final AuditLedgerService audit = mock(AuditLedgerService.class);
    private final FeatureRun run = mock(FeatureRun.class);
    private final DeliveryTask task = mock(DeliveryTask.class);
    private final RepositoryConnection connection = mock(RepositoryConnection.class);
    private final GithubRunnerPushService service = new GithubRunnerPushService(leases, tasks, connections, publications, github, audit);

    @BeforeEach
    void prepareIntegrationLease() {
        when(leases.requireActiveTaskId("lease-1", "runner-1", "nonce")).thenReturn("task-1");
        when(tasks.findById("task-1")).thenReturn(Optional.of(task));
        when(task.getRole()).thenReturn("INTEGRATION");
        when(task.getRepository()).thenReturn("acme/ticketly");
        when(task.getRun()).thenReturn(run);
        when(run.getId()).thenReturn("run-1");
        when(connections.requireEnabled("acme/ticketly")).thenReturn(connection);
        when(connection.getInstallationId()).thenReturn(7L);
    }

    @Test
    void grantBindsRetryToPreviouslyRecordedRemoteHead() {
        GithubPublication publication = new GithubPublication("run-1", "acme/ticketly", "forgeloop/run-1", "key");
        publication.recordHeadSha("a".repeat(40));
        when(publications.findByFeatureRunId("run-1")).thenReturn(Optional.of(publication));
        when(github.getBranchHead(7L, "acme/ticketly", "forgeloop/run-1")).thenReturn("a".repeat(40));
        when(github.issueInstallationToken(7L)).thenReturn("short-lived-token");

        GithubPushGrant grant = service.grant("lease-1", "runner-1", "nonce");

        assertEquals("a".repeat(40), grant.expectedHeadSha());
        assertEquals("short-lived-token", grant.token());
    }

    @Test
    void completesOnlyWhenGithubReportsTheExactIntegratedCommit() {
        String integratedSha = "b".repeat(40);
        GithubPublication publication = new GithubPublication("run-1", "acme/ticketly", "forgeloop/run-1", "key");
        when(publications.findByFeatureRunId("run-1")).thenReturn(Optional.of(publication));
        when(github.getBranchHead(7L, "acme/ticketly", "forgeloop/run-1")).thenReturn(integratedSha);

        service.complete("lease-1", "runner-1", "nonce", integratedSha);

        verify(leases).completeIntegration("lease-1", "runner-1", "nonce", integratedSha);
        assertEquals(integratedSha, publication.getHeadSha());
    }

    @Test
    void rejectsAReportedCommitThatWasNotPushed() {
        GithubPublication publication = new GithubPublication("run-1", "acme/ticketly", "forgeloop/run-1", "key");
        when(publications.findByFeatureRunId("run-1")).thenReturn(Optional.of(publication));
        when(github.getBranchHead(7L, "acme/ticketly", "forgeloop/run-1")).thenReturn("c".repeat(40));

        assertThrows(IllegalStateException.class, () -> service.complete("lease-1", "runner-1", "nonce", "b".repeat(40)));

        verify(leases, never()).completeIntegration("lease-1", "runner-1", "nonce", "b".repeat(40));
    }
}
