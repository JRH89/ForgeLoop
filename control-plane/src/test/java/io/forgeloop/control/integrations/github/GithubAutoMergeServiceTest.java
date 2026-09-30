package io.forgeloop.control.integrations.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

import io.forgeloop.control.application.AuditLedgerService;
import io.forgeloop.control.domain.FeatureRun;
import io.forgeloop.control.domain.FeatureRunRepository;
import io.forgeloop.control.domain.GithubPublication;
import io.forgeloop.control.domain.GithubPublicationRepository;
import io.forgeloop.control.domain.RepositoryConnection;
import io.forgeloop.control.domain.RepositoryConnectionRepository;
import java.util.List;
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
        publication.linkSourceIssue(9);
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
        assertEquals(9, publication.getSourceIssueNumber());
        assertNotNull(publication.getSourceIssueClosedAt());
        verify(github).closeIssue(7, "acme/app", 9);
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
        publication.linkSourceIssue(9);
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
        assertNotNull(publication.getSourceIssueClosedAt());
        verify(github, times(1)).closeIssue(7, "acme/app", 9);
        verify(run, times(1)).completeDelivery();
        verify(audit).record("GITHUB_PR_MERGED", "FEATURE_RUN", "run-1", "merge-sha");
    }

    @Test void retriesAFailedSourceIssueClosureWithoutRepeatingTheMerge() {
        GithubPublication publication = pendingPublication();
        publication.linkSourceIssue(9);
        publication.recordMerge("b".repeat(40));
        RepositoryConnection connection = mock(RepositoryConnection.class);
        when(publications.findByMergedAtIsNotNullAndSourceIssueNumberIsNotNullAndSourceIssueClosedAtIsNull())
                .thenReturn(List.of(publication));
        when(connections.findByRepository("acme/app")).thenReturn(Optional.of(connection));
        when(connection.isEnabled()).thenReturn(true);
        when(connection.getInstallationId()).thenReturn(7L);
        doThrow(new IllegalStateException("GitHub temporarily unavailable"))
                .doNothing().when(github).closeIssue(7, "acme/app", 9);

        service.reconcilePending();
        assertNull(publication.getSourceIssueClosedAt());
        service.reconcilePending();

        assertNotNull(publication.getSourceIssueClosedAt());
        verify(github, times(2)).closeIssue(7, "acme/app", 9);
        verify(github, never()).mergePullRequest(anyLong(), anyString(), anyLong(), anyString());
    }

    private static GithubPublication pendingPublication() {
        GithubPublication publication = new GithubPublication("run-1", "acme/app", "forgeloop/run-1", "run-1");
        publication.recordHeadSha("a".repeat(40));
        publication.recordPullRequest(42, true);
        return publication;
    }
}
