package io.forgeloop.control.integrations.github;

import io.forgeloop.control.application.AuditLedgerService;
import io.forgeloop.control.domain.FeatureRun;
import io.forgeloop.control.domain.GithubPublication;
import io.forgeloop.control.domain.GithubPublicationRepository;
import io.forgeloop.control.domain.RunState;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates exactly one GitHub branch, check run, and draft PR for a verified run. */
@Service
public class GithubDeliveryService {
    private final GithubPublicationRepository publications;
    private final GithubApi github;
    private final AuditLedgerService audit;
    public GithubDeliveryService(GithubPublicationRepository publications, GithubApi github, AuditLedgerService audit) { this.publications = publications; this.github = github; this.audit = audit; }

    @Transactional
    public GithubPublication deliver(FeatureRun run, long installationId, String baseSha, List<GithubChange> changes, String summary) {
        GithubPublication publication = publications.findByFeatureRunId(run.getId()).orElseGet(() -> publications.save(new GithubPublication(run.getId(), run.getRepository(), branch(run), key(run))));
        if (publication.isDelivered()) return publication;
        if (run.getState() != RunState.READY_FOR_REVIEW) throw new IllegalStateException("Only a fully verified run can be delivered to GitHub");
        if (baseSha == null || baseSha.isBlank() || changes == null || changes.isEmpty()) throw new IllegalArgumentException("A base commit and at least one verified change are required");
        if (publication.getHeadSha() == null) {
            github.createBranch(installationId, run.getRepository(), publication.getBranch(), baseSha);
            String head = baseSha;
            for (GithubChange change : changes) head = github.putFile(installationId, run.getRepository(), publication.getBranch(), change);
            publication.recordHeadSha(head);
            audit.record("GITHUB_BRANCH_DELIVERED", "FEATURE_RUN", run.getId(), publication.getBranch());
        }
        if (publication.getCheckRunId() == null) {
            publication.recordCheckRun(github.createCompletedCheck(installationId, run.getRepository(), publication.getHeadSha(), "ForgeLoop verification", summary));
            audit.record("GITHUB_CHECK_RUN_CREATED", "FEATURE_RUN", run.getId(), publication.getHeadSha());
        }
        if (publication.getPullRequestNumber() == null) {
            publication.recordPullRequest(github.createDraftPullRequest(installationId, run.getRepository(), publication.getBranch(), run.getSourceRef(), run.getTitle(), summary));
            audit.record("GITHUB_DRAFT_PR_CREATED", "FEATURE_RUN", run.getId(), String.valueOf(publication.getPullRequestNumber()));
        }
        return publications.save(publication);
    }
    private static String branch(FeatureRun run) { return "forgeloop/" + run.getId(); }
    private static String key(FeatureRun run) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((run.getRepository() + "|" + run.getSourceRef()).getBytes(StandardCharsets.UTF_8))); } catch (Exception exception) { throw new IllegalStateException("SHA-256 unavailable", exception); } }
}
