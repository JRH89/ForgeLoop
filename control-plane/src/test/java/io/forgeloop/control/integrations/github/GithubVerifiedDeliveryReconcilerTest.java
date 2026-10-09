package io.forgeloop.control.integrations.github;

import io.forgeloop.control.domain.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GithubVerifiedDeliveryReconcilerTest {
    @Test void publishesOnlyVerifiedAuthorizedRunsOwnedByTheConnection() {
        var publications=mock(GithubPublicationRepository.class);
        var runs=mock(FeatureRunRepository.class);
        var connections=mock(RepositoryConnectionRepository.class);
        var delivery=mock(GithubDeliveryService.class);
        var publication=new GithubPublication("run","acme/app","forgeloop/run","key");
        publication.recordHeadSha("a".repeat(40));
        var run=mock(FeatureRun.class);
        var connection=mock(RepositoryConnection.class);
        when(publications.findByPullRequestNumberIsNullAndHeadShaIsNotNull()).thenReturn(List.of(publication));
        when(runs.findById("run")).thenReturn(Optional.of(run));
        when(run.getRepository()).thenReturn("acme/app");
        when(run.getOrganizationId()).thenReturn("org");
        when(connections.findByRepository("acme/app")).thenReturn(Optional.of(connection));
        when(connection.isEnabled()).thenReturn(true);
        when(connection.getOrganizationId()).thenReturn("org");
        when(connection.getInstallationId()).thenReturn(7L);
        var reconciler=new GithubVerifiedDeliveryReconciler(publications,runs,connections,delivery);
        when(run.getState()).thenReturn(RunState.VERIFYING);
        when(run.isDeliveryAuthorized()).thenReturn(true);
        reconciler.reconcilePending();
        verifyNoInteractions(delivery);
        when(run.getState()).thenReturn(RunState.READY_FOR_REVIEW);
        when(run.isDeliveryAuthorized()).thenReturn(false);
        reconciler.reconcilePending();
        verifyNoInteractions(delivery);
        when(run.isDeliveryAuthorized()).thenReturn(true);
        when(connection.getOrganizationId()).thenReturn("different-org");
        reconciler.reconcilePending();
        verifyNoInteractions(delivery);
        when(connection.getOrganizationId()).thenReturn("org");
        reconciler.reconcilePending();
        verify(delivery).deliverPushed(eq(run),eq(7L),anyString());
    }
}
