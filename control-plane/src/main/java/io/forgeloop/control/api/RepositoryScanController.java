package io.forgeloop.control.api;

import io.forgeloop.control.application.RepositoryScanService;
import io.forgeloop.control.application.RepositoryIssueProposalService;
import io.forgeloop.control.domain.RepositoryScan;
import io.forgeloop.control.domain.RepositoryIssueProposal;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/** Browser operations for explicit repository scans and reviewed issue proposals. */
@Controller
public class RepositoryScanController {
    private final RepositoryScanService scans;
    private final RepositoryIssueProposalService proposals;
    public RepositoryScanController(RepositoryScanService scans, RepositoryIssueProposalService proposals) { this.scans = scans; this.proposals = proposals; }
    @QueryMapping public List<RepositoryScan> repositoryScans(@Argument String repository) { return scans.list(repository); }
    @MutationMapping public RepositoryScan requestRepositoryScan(@Argument String repository) { return scans.request(repository); }
    @MutationMapping public RepositoryIssueProposal requestRepositoryIssueProposal(@Argument String scanId, @Argument String findingId) {
        return proposals.request(scanId, findingId);
    }
    @MutationMapping public RepositoryIssueProposal approveRepositoryIssueProposal(@Argument String proposalId,
            @Argument String title, @Argument String body, @Argument List<String> acceptanceCriteria) {
        return proposals.approve(proposalId, title, body, acceptanceCriteria);
    }
    @MutationMapping public RepositoryIssueProposal rejectRepositoryIssueProposal(@Argument String proposalId, @Argument String reason) {
        return proposals.reject(proposalId, reason);
    }
}
