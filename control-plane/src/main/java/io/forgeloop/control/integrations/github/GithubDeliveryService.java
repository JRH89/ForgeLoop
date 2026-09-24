package io.forgeloop.control.integrations.github;

import io.forgeloop.control.application.AuditLedgerService;
import io.forgeloop.control.domain.FeatureRun;
import io.forgeloop.control.domain.GithubPublication;
import io.forgeloop.control.domain.GithubPublicationRepository;
import io.forgeloop.control.domain.RunState;
import io.forgeloop.control.domain.OrganizationPolicyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates exactly one GitHub branch, check run, and draft PR for a verified run. */
@Service
public class GithubDeliveryService {
    private final GithubPublicationRepository publications;
    private final GithubApi github;
    private final AuditLedgerService audit;
    private final OrganizationPolicyRepository policies;
    @Autowired public GithubDeliveryService(GithubPublicationRepository publications, GithubApi github, AuditLedgerService audit, OrganizationPolicyRepository policies) { this.publications = publications; this.github = github; this.audit = audit; this.policies = policies; }
    /** Test-compatible constructor; production always supplies organization policy storage. */
    GithubDeliveryService(GithubPublicationRepository publications, GithubApi github, AuditLedgerService audit) { this(publications, github, audit, null); }

    /** Finalizes a branch pushed directly by the authenticated runner without uploading source through ForgeLoop. */
    @Transactional
    public GithubPublication deliverPushed(FeatureRun run, long installationId, String summary) {
        GithubPublication publication = publications.findByFeatureRunId(run.getId()).orElseThrow(() -> new IllegalStateException("Runner has not pushed an integrated branch"));
        if (publication.isDelivered()) return publication;
        if (run.getState() != RunState.READY_FOR_REVIEW || !run.isApproved()) throw new IllegalStateException("Only an approved, fully verified run can be delivered to GitHub");
        if (publication.getHeadSha() == null || !publication.getHeadSha().equals(github.getBranchHead(installationId, run.getRepository(), publication.getBranch()))) throw new IllegalStateException("Runner-pushed branch head does not match the integrated commit");
        if (publication.getCheckRunId() == null) { publication.recordCheckRun(github.createCompletedCheck(installationId, run.getRepository(), publication.getHeadSha(), "ForgeLoop verification", summary)); audit.record("GITHUB_CHECK_RUN_CREATED", "FEATURE_RUN", run.getId(), publication.getHeadSha()); }
        boolean autoMerge = policies != null && policies.findById(run.getOrganizationId()).map(policy -> policy.isAutoMergeEnabled()).orElse(false);
        if (publication.getPullRequestNumber() == null) { publication.recordPullRequest(github.createPullRequest(installationId, run.getRepository(), publication.getBranch(), run.getBaseBranch(), run.getTitle(), pullRequestBody(run, summary), !autoMerge), autoMerge); audit.record(autoMerge ? "GITHUB_AUTO_MERGE_PR_CREATED" : "GITHUB_DRAFT_PR_CREATED", "FEATURE_RUN", run.getId(), String.valueOf(publication.getPullRequestNumber())); }
        return publications.save(publication);
    }

    /** Links issue-originated work so GitHub closes the source issue when the verified PR merges. */
    static String pullRequestBody(FeatureRun run, String summary) {
        if (run.getSourceRef() != null && run.getSourceRef().matches("issue-[1-9][0-9]*")) {
            return summary + "\n\nCloses #" + run.getSourceRef().substring("issue-".length());
        }
        return summary;
    }
}
