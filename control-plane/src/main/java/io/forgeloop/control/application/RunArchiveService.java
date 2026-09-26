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
    public RunArchiveService(FeatureRunService runs, OperatorContext operators, AuditLedgerService audit) {
        this.runs = runs; this.operators = operators; this.audit = audit;
    }
    @Transactional
    public FeatureRun archive(String id, boolean archived) {
        operators.requireOperator();
        FeatureRun run = runs.get(id); // Tenant authorization precedes mutation.
        run.setArchived(archived);
        audit.record(archived ? "RUN_ARCHIVED" : "RUN_RESTORED", "FEATURE_RUN", id, Boolean.toString(archived));
        return run;
    }
}
