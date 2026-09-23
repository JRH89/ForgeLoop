package io.forgeloop.control.integrations.github;

import io.forgeloop.control.application.AuditLedgerService;
import io.forgeloop.control.domain.FeatureRun;
import io.forgeloop.control.domain.GithubPublication;
import io.forgeloop.control.domain.GithubPublicationRepository;
import io.forgeloop.control.domain.RunState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates exactly one GitHub branch, check run, and draft PR for a verified run. */
@Service
public class GithubDeliveryService {
    private final GithubPublicationRepository publications;
    private final GithubApi github;
    private final AuditLedgerService audit;
    public GithubDeliveryService(GithubPublicationRepository publications, GithubApi github, AuditLedgerService audit) { this.publications = publications; this.github = github; this.audit = audit; }

    /** Finalizes a branch pushed directly by the authenticated runner without uploading source through ForgeLoop. */
    @Transactional
    public GithubPublication deliverPushed(FeatureRun run, long installationId, String summary) {
        GithubPublication publication = publications.findByFeatureRunId(run.getId()).orElseThrow(() -> new IllegalStateException("Runner has not pushed an integrated branch"));
        if (publication.isDelivered()) return publication;
        if (run.getState() != RunState.READY_FOR_REVIEW || !run.isApproved()) throw new IllegalStateException("Only an approved, fully verified run can be delivered to GitHub");
        if (publication.getHeadSha() == null || !publication.getHeadSha().equals(github.getBranchHead(installationId, run.getRepository(), publication.getBranch()))) throw new IllegalStateException("Runner-pushed branch head does not match the integrated commit");
        if (publication.getCheckRunId() == null) { publication.recordCheckRun(github.createCompletedCheck(installationId, run.getRepository(), publication.getHeadSha(), "ForgeLoop verification", summary)); audit.record("GITHUB_CHECK_RUN_CREATED", "FEATURE_RUN", run.getId(), publication.getHeadSha()); }
        if (publication.getPullRequestNumber() == null) { publication.recordPullRequest(github.createDraftPullRequest(installationId, run.getRepository(), publication.getBranch(), run.getBaseBranch(), run.getTitle(), summary)); audit.record("GITHUB_DRAFT_PR_CREATED", "FEATURE_RUN", run.getId(), String.valueOf(publication.getPullRequestNumber())); }
        return publications.save(publication);
    }
}
