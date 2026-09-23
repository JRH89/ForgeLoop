package io.forgeloop.control.integrations.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.forgeloop.control.application.AuditLedgerService;
import io.forgeloop.control.domain.FeatureRun;
import io.forgeloop.control.domain.GithubPublication;
import io.forgeloop.control.domain.GithubPublicationRepository;
import io.forgeloop.control.domain.RunState;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GithubDeliveryServiceTest {
    @Test void rejectsVerifiedButUnapprovedRun() {
        GithubPublicationRepository publications = mock(GithubPublicationRepository.class); GithubApi api = mock(GithubApi.class); FeatureRun run = mock(FeatureRun.class);
        when(run.getId()).thenReturn("run-2"); when(run.getRepository()).thenReturn("acme/ticketly"); when(run.getState()).thenReturn(RunState.READY_FOR_REVIEW); when(publications.findByFeatureRunId("run-2")).thenReturn(Optional.of(new GithubPublication("run-2", "acme/ticketly", "forgeloop/run-2", "key")));
        GithubDeliveryService service = new GithubDeliveryService(publications, api, mock(AuditLedgerService.class));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> service.deliverPushed(run, 7L, "summary"));
        verifyNoInteractions(api);
    }
    @Test void finalizesOnlyTheVerifiedRunnerPushedHead() {
        GithubPublicationRepository publications=mock(GithubPublicationRepository.class);GithubApi api=mock(GithubApi.class);AuditLedgerService audit=mock(AuditLedgerService.class);FeatureRun run=mock(FeatureRun.class);
        when(run.getId()).thenReturn("run-3");when(run.getRepository()).thenReturn("acme/ticketly");when(run.getBaseBranch()).thenReturn("main");when(run.getSourceRef()).thenReturn("issue-42");when(run.getTitle()).thenReturn("Fix ticket");when(run.getState()).thenReturn(RunState.READY_FOR_REVIEW);when(run.isApproved()).thenReturn(true);
        GithubPublication publication=new GithubPublication("run-3","acme/ticketly","forgeloop/run-3","key");publication.recordHeadSha("a".repeat(40));when(publications.findByFeatureRunId("run-3")).thenReturn(Optional.of(publication));when(publications.save(any())).thenAnswer(call->call.getArgument(0));when(api.getBranchHead(7,"acme/ticketly","forgeloop/run-3")).thenReturn("a".repeat(40));when(api.createCompletedCheck(anyLong(),anyString(),anyString(),anyString(),anyString())).thenReturn(4L);when(api.createDraftPullRequest(anyLong(),anyString(),anyString(),anyString(),anyString(),anyString())).thenReturn(5L);
        GithubPublication delivered=new GithubDeliveryService(publications,api,audit).deliverPushed(run,7,"verified");
        assertEquals(5L,delivered.getPullRequestNumber());verify(api).createDraftPullRequest(7,"acme/ticketly","forgeloop/run-3","main","Fix ticket","verified\n\nCloses #42");
    }
    @Test void doesNotInventIssueLinksForNonIssueSources() {
        FeatureRun run=mock(FeatureRun.class);when(run.getSourceRef()).thenReturn("manual-1");
        assertEquals("verified",GithubDeliveryService.pullRequestBody(run,"verified"));
    }
}
