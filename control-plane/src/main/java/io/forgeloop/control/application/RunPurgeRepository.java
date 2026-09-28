package io.forgeloop.control.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Deletes run-owned persistence in foreign-key order while leaving the append-only audit ledger intact. */
@Repository
public class RunPurgeRepository {
    private final JdbcTemplate db;

    public RunPurgeRepository(JdbcTemplate db) { this.db = db; }

    public boolean delete(String runId) {
        db.update("delete from runner_event where run_id = ?", runId);
        db.update("delete from human_escalation where run_id = ?", runId);
        // Metadata removal makes uploaded objects unreachable; binary bytes follow the configured artifact retention policy.
        db.update("delete from artifact_metadata where run_id = ?", runId);
        db.update("delete from github_publication where feature_run_id = ?", runId);
        db.update("delete from verification_evidence where task_id in (select id from delivery_task where run_id = ?)", runId);
        db.update("delete from review_evidence where task_id in (select id from delivery_task where run_id = ?)", runId);
        db.update("delete from provider_attempt where task_id in (select id from delivery_task where run_id = ?)", runId);
        db.update("delete from repair_package where task_id in (select id from delivery_task where run_id = ?)", runId);
        db.update("delete from task_lease where task_id in (select id from delivery_task where run_id = ?)", runId);
        db.update("delete from delivery_task_dependency where task_id in (select id from delivery_task where run_id = ?) "
                + "or dependency_id in (select id from delivery_task where run_id = ?)", runId, runId);
        db.update("update delivery_task set verification_gate_id = null where run_id = ?", runId);
        db.update("delete from delivery_task where run_id = ?", runId);
        db.update("delete from verification_gate where run_id = ?", runId);
        db.update("delete from acceptance_criterion where run_id = ?", runId);
        return db.update("delete from feature_run where id = ?", runId) == 1;
    }
}
