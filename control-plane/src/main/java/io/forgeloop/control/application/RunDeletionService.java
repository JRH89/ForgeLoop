package io.forgeloop.control.application;

import io.forgeloop.control.domain.FeatureRun;
import io.forgeloop.control.domain.FeatureRunRepository;
import io.forgeloop.control.domain.RunState;
import io.forgeloop.control.domain.TaskLeaseRepository;
import io.forgeloop.control.security.OperatorContext;
import java.time.Instant;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Permanently removes an archived run only after tenant, lifecycle, and live-lease checks pass. */
@Service
public class RunDeletionService {
    private static final Set<RunState> DELETABLE_STATES = Set.of(
            RunState.COMPLETE, RunState.CANCELLED, RunState.FAILED, RunState.REJECTED,
            RunState.READY_FOR_REVIEW, RunState.PR_OPEN);

    private final FeatureRunRepository runs;
    private final TaskLeaseRepository leases;
    private final RunPurgeRepository purge;
    private final OperatorContext operators;
    private final AuditLedgerService audit;

    public RunDeletionService(FeatureRunRepository runs, TaskLeaseRepository leases, RunPurgeRepository purge,
                              OperatorContext operators, AuditLedgerService audit) {
        this.runs = runs;
        this.leases = leases;
        this.purge = purge;
        this.operators = operators;
        this.audit = audit;
    }

    @Transactional
    public boolean delete(String id) {
        operators.requireOperator();
        FeatureRun run = runs.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Feature run not found"));
        operators.requireOrganization(run.getOrganizationId());
        if (!run.isArchived() || !DELETABLE_STATES.contains(run.getState())) {
            throw new IllegalStateException("Archive a completed run before deleting it");
        }
        if (leases.countByTask_Run_IdAndCompletedAtIsNullAndExpiresAtAfter(id, Instant.now()) > 0) {
            throw new IllegalStateException("Wait for active runner leases to expire before deleting this run");
        }

        audit.record("RUN_DELETED", "FEATURE_RUN", id, run.getState().name());
        if (!purge.delete(id)) throw new IllegalStateException("Feature run could not be deleted");
        return true;
    }
}
