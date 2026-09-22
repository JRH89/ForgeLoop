package io.forgeloop.control.integrations.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.forgeloop.control.application.AuditLedgerService;
import io.forgeloop.control.domain.FeatureRun;
import io.forgeloop.control.domain.GithubPublication;
import io.forgeloop.control.domain.GithubPublicationRepository;
import io.forgeloop.control.domain.RunState;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GithubDeliveryServiceTest {
    @Test void deliversOneBranchCheckAndDraftPrAndThenIsIdempotent() {
        GithubPublicationRepository publications = mock(GithubPublicationRepository.class);
        GithubApi api = mock(GithubApi.class); AuditLedgerService audit = mock(AuditLedgerService.class);
        FeatureRun run = mock(FeatureRun.class);
        when(run.getId()).thenReturn("run-1"); when(run.getRepository()).thenReturn("acme/ticketly"); when(run.getSourceRef()).thenReturn("main"); when(run.getTitle()).thenReturn("Assign tickets"); when(run.getState()).thenReturn(RunState.READY_FOR_REVIEW); when(run.isApproved()).thenReturn(true);
        when(publications.findByFeatureRunId("run-1")).thenReturn(Optional.empty()); when(publications.save(any())).thenAnswer(call -> call.getArgument(0));
        when(api.putFile(eq(7L), eq("acme/ticketly"), any(), any())).thenReturn("head-2"); when(api.createCompletedCheck(eq(7L), eq("acme/ticketly"), eq("head-2"), any(), any())).thenReturn(41L); when(api.createDraftPullRequest(eq(7L), eq("acme/ticketly"), any(), any(), any(), any())).thenReturn(17L);
        GithubDeliveryService service = new GithubDeliveryService(publications, api, audit);
        GithubPublication publication = service.deliver(run, 7L, "base-1", List.of(new GithubChange("src/a.txt", "new", "Update A")), "Verified");
        assertEquals(17L, publication.getPullRequestNumber()); verify(api).createBranch(7L, "acme/ticketly", "forgeloop/run-1", "base-1"); verify(api).createCompletedCheck(7L, "acme/ticketly", "head-2", "ForgeLoop verification", "Verified");
        when(publications.findByFeatureRunId("run-1")).thenReturn(Optional.of(publication)); service.deliver(run, 7L, "base-1", List.of(), "Verified"); verify(api, times(1)).createBranch(anyLong(), anyString(), anyString(), anyString()); verify(api, times(1)).createDraftPullRequest(anyLong(), anyString(), anyString(), anyString(), anyString(), anyString());
    }
    @Test void rejectsTraversalBeforeAnyGitHubCall() { org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new GithubChange("../secrets", "no", "bad")); }
    @Test void rejectsVerifiedButUnapprovedRun() {
        GithubPublicationRepository publications = mock(GithubPublicationRepository.class); GithubApi api = mock(GithubApi.class); FeatureRun run = mock(FeatureRun.class);
        when(run.getId()).thenReturn("run-2"); when(run.getRepository()).thenReturn("acme/ticketly"); when(run.getState()).thenReturn(RunState.READY_FOR_REVIEW); when(publications.findByFeatureRunId("run-2")).thenReturn(Optional.empty()); when(publications.save(any())).thenAnswer(call -> call.getArgument(0));
        GithubDeliveryService service = new GithubDeliveryService(publications, api, mock(AuditLedgerService.class));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> service.deliver(run, 7L, "base", List.of(new GithubChange("a", "b", "c")), "summary"));
        verifyNoInteractions(api);
    }
}
