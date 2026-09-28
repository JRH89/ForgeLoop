package io.forgeloop.control.application;

import io.forgeloop.control.domain.RepositoryIssueProposal;
import io.forgeloop.control.domain.RepositoryScanFinding;
import java.util.List;

/** Bounded, evidence-only context returned to an authenticated customer runner. */
public record RepositoryIssueProposalGrant(String id, String repository, String commitSha, String severity,
                                           String findingTitle, String description, String impact, String evidence,
                                           List<String> affectedFiles, List<String> acceptanceCriteria) {
    public static RepositoryIssueProposalGrant from(RepositoryIssueProposal proposal) {
        RepositoryScanFinding finding = proposal.getFinding();
        return new RepositoryIssueProposalGrant(proposal.getId(), proposal.getRepository(),
                finding.getScan().getCommitSha(), finding.getSeverity(), finding.getTitle(), finding.getDescription(),
                finding.getImpact(), finding.getEvidence(), finding.getAffectedFiles(), finding.getAcceptanceCriteria());
    }
}
