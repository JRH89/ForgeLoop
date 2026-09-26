package io.forgeloop.control.integrations.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import io.forgeloop.control.application.AuditLedgerService;
import io.forgeloop.control.domain.FeatureRun;
import io.forgeloop.control.domain.FeatureRunRepository;
import io.forgeloop.control.domain.GithubPublication;
import io.forgeloop.control.domain.GithubPublicationRepository;
import io.forgeloop.control.domain.RepositoryConnection;
import io.forgeloop.control.domain.RepositoryConnectionRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GithubAutoMergeServiceTest {
    private final GithubPublicationRepository publications = mock(GithubPublicationRepository.class);
    private final FeatureRunRepository runs = mock(FeatureRunRepository.class);
    private final RepositoryConnectionRepository connections = mock(RepositoryConnectionRepository.class);
    private final GithubApi github = mock(GithubApi.class);
    private final AuditLedgerService audit = mock(AuditLedgerService.class);
    private final GithubAutoMergeService service = new GithubAutoMergeService(publications, runs, connections, github, audit);

    @Test void mergesOnlyTheRecordedHeadAfterEveryCheckPasses() {
        GithubPublication publication = pendingPublication();
        RepositoryConnection connection = mock(RepositoryConnection.class);
        FeatureRun run = mock(FeatureRun.class);
        when(run.getId()).thenReturn("run-1");
        when(publications.findByRepositoryAndHeadSha("acme/app", "a".repeat(40))).thenReturn(Optional.of(publication));
        when(connections.findByRepository("acme/app")).thenReturn(Optional.of(connection));
        when(connection.isEnabled()).thenReturn(true);
        when(connection.isInstalledAs(7)).thenReturn(true);
        when(github.checksPass(7, "acme/app", "a".repeat(40))).thenReturn(true);
        when(github.getPullRequestHead(7, "acme/app", 42)).thenReturn("a".repeat(40));
        when(github.mergePullRequest(7, "acme/app", 42, "a".repeat(40))).thenReturn("b".repeat(40));
        when(runs.findById("run-1")).thenReturn(Optional.of(run));

        service.reconcile("acme/app", "a".repeat(40), 7);

        assertEquals("b".repeat(40), publication.getMergeSha());
        verify(run).completeDelivery();
        verify(audit).record("GITHUB_PR_AUTO_MERGED", "FEATURE_RUN", "run-1", "b".repeat(40));
    }

    @Test void leavesThePullRequestOpenWhileChecksArePending() {
        GithubPublication publication = pendingPublication();
        RepositoryConnection connection = mock(RepositoryConnection.class);
        when(publications.findByRepositoryAndHeadSha("acme/app", "a".repeat(40))).thenReturn(Optional.of(publication));
        when(connections.findByRepository("acme/app")).thenReturn(Optional.of(connection));
        when(connection.isEnabled()).thenReturn(true);
        when(connection.isInstalledAs(7)).thenReturn(true);
        when(github.checksPass(7, "acme/app", "a".repeat(40))).thenReturn(false);

        service.reconcile("acme/app", "a".repeat(40), 7);

        verify(github, never()).mergePullRequest(anyLong(), anyString(), anyLong(), anyString());
    }

    @Test void recordsHumanMergedPullRequestAndCompletesApprovedRunIdempotently() {
        GithubPublication publication = new GithubPublication("run-1", "acme/app", "forgeloop/run-1", "run-1");
        publication.recordPullRequest(42, false);
        RepositoryConnection connection = mock(RepositoryConnection.class);
        FeatureRun run = mock(FeatureRun.class);
        when(run.getId()).thenReturn("run-1");
        when(run.getState()).thenReturn(io.forgeloop.control.domain.RunState.READY_FOR_REVIEW);
        when(run.isApproved()).thenReturn(true);
        when(connections.findByRepository("acme/app")).thenReturn(Optional.of(connection));
        when(connection.isEnabled()).thenReturn(true);
        when(connection.isInstalledAs(7)).thenReturn(true);
        when(publications.findByRepositoryAndPullRequestNumber("acme/app", 42L)).thenReturn(Optional.of(publication));
        when(runs.findById("run-1")).thenReturn(Optional.of(run));

        service.recordMergedPullRequest("acme/app", 42, 7, "merge-sha");
        service.recordMergedPullRequest("acme/app", 42, 7, "merge-sha");

        assertEquals("merge-sha", publication.getMergeSha());
        verify(run, times(1)).completeDelivery();
        verify(audit).record("GITHUB_PR_MERGED", "FEATURE_RUN", "run-1", "merge-sha");
    }

    private static GithubPublication pendingPublication() {
        GithubPublication publication = new GithubPublication("run-1", "acme/app", "forgeloop/run-1", "run-1");
        publication.recordHeadSha("a".repeat(40));
        publication.recordPullRequest(42, true);
        return publication;
    }
}
