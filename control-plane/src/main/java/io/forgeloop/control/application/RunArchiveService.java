package io.forgeloop.control.application;

import io.forgeloop.control.domain.FeatureRun;
import io.forgeloop.control.security.OperatorContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Keeps queue housekeeping separate from execution and destructive retention policies. */
@Service
public class RunArchiveService {
    private final FeatureRunService runs;
    private final OperatorContext operators;
    private final AuditLedgerService audit;
    private final io.forgeloop.control.domain.GithubPublicationRepository publications;
    private final io.forgeloop.control.integrations.github.GithubPublicationStatusService publicationStatus;
    public RunArchiveService(FeatureRunService runs, OperatorContext operators, AuditLedgerService audit,
                            io.forgeloop.control.domain.GithubPublicationRepository publications,
                            io.forgeloop.control.integrations.github.GithubPublicationStatusService publicationStatus) {
        this.runs = runs; this.operators = operators; this.audit = audit; this.publications=publications; this.publicationStatus=publicationStatus;
    }
    @Transactional
    public FeatureRun archive(String id, boolean archived) {
        operators.requireOperator();
        FeatureRun run = runs.get(id); // Tenant authorization precedes mutation.
        // Old deliveries may predate merge webhooks. Confirm the PR without rewriting delivery evidence.
        boolean merged = archived && publications.findByFeatureRunId(id).map(item->"MERGED".equals(publicationStatus.state(item))).orElse(false);
        run.setArchived(archived, merged);
        audit.record(archived ? "RUN_ARCHIVED" : "RUN_RESTORED", "FEATURE_RUN", id, Boolean.toString(archived));
        return run;
    }
}
