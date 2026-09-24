package io.forgeloop.control.integrations.github;

import io.forgeloop.control.application.AuditLedgerService;
import io.forgeloop.control.domain.FeatureRun;
import io.forgeloop.control.domain.FeatureRunRepository;
import io.forgeloop.control.domain.GithubPublication;
import io.forgeloop.control.domain.GithubPublicationRepository;
import io.forgeloop.control.domain.RepositoryConnection;
import io.forgeloop.control.domain.RepositoryConnectionRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Safely reconciles opt-in merges from both webhooks and a retrying scheduled sweep. */
@Service
public class GithubAutoMergeService {
    private static final Logger log = LoggerFactory.getLogger(GithubAutoMergeService.class);
    private final GithubPublicationRepository publications;
    private final FeatureRunRepository runs;
    private final RepositoryConnectionRepository connections;
    private final GithubApi github;
    private final AuditLedgerService audit;

    public GithubAutoMergeService(GithubPublicationRepository publications, FeatureRunRepository runs,
                                  RepositoryConnectionRepository connections, GithubApi github, AuditLedgerService audit) {
        this.publications = publications; this.runs = runs; this.connections = connections; this.github = github; this.audit = audit;
    }

    @Transactional
    public void reconcile(String repository, String headSha, long installationId) {
        publications.findByRepositoryAndHeadSha(repository, headSha).ifPresent(publication -> reconcile(publication, installationId));
    }

    /** Retries pending decisions so a transient webhook or GitHub outage cannot strand an eligible PR. */
    @Scheduled(fixedDelayString = "${forgeloop.github.auto-merge-reconcile-ms:30000}")
    @Transactional
    public void reconcilePending() {
        List<GithubPublication> pending = publications.findByAutoMergeRequestedTrueAndMergedAtIsNullAndPullRequestNumberIsNotNull();
        for (GithubPublication publication : pending) {
            try {
                RepositoryConnection connection = connections.findByRepository(publication.getRepository()).orElse(null);
                if (connection != null && connection.isEnabled()) reconcile(publication, connection.getInstallationId());
            } catch (RuntimeException exception) {
                log.warn("Auto-merge reconciliation will retry for publication {}: {}", publication.getId(), exception.getMessage());
            }
        }
    }

    private void reconcile(GithubPublication publication, long installationId) {
        if (!publication.isAutoMergeRequested() || publication.getMergedAt() != null || publication.getPullRequestNumber() == null) return;
        RepositoryConnection connection = connections.findByRepository(publication.getRepository()).orElse(null);
        if (connection == null || !connection.isEnabled() || !connection.isInstalledAs(installationId)) return;
        if (!github.checksPass(installationId, publication.getRepository(), publication.getHeadSha())) return;
        String currentHead = github.getPullRequestHead(installationId, publication.getRepository(), publication.getPullRequestNumber());
        if (!publication.getHeadSha().equals(currentHead)) throw new IllegalStateException("Pull request head changed after ForgeLoop verification");
        String mergeSha = github.mergePullRequest(installationId, publication.getRepository(), publication.getPullRequestNumber(), publication.getHeadSha());
        publication.recordMerge(mergeSha);
        FeatureRun run = runs.findById(publication.getFeatureRunId()).orElseThrow(() -> new IllegalStateException("Feature run was not found"));
        run.completeDelivery();
        audit.record("GITHUB_PR_AUTO_MERGED", "FEATURE_RUN", run.getId(), mergeSha);
    }
}
