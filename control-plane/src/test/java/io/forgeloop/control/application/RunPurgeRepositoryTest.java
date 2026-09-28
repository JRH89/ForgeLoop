package io.forgeloop.control.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class RunPurgeRepositoryTest {
    private JdbcTemplate db;
    private RunPurgeRepository repository;

    @BeforeEach void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:run-purge-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        db = new JdbcTemplate(source);
        repository = new RunPurgeRepository(db);
        schema();
        seed();
    }

    @Test void removesRunOwnedRowsInForeignKeyOrderAndLeavesAuditHistory() {
        assertEquals(true, repository.delete("run-1"));
        for (String table : new String[]{"runner_event", "human_escalation", "artifact_metadata", "github_publication",
                "verification_evidence", "review_evidence", "review_criterion_assessment", "provider_attempt",
                "repair_package", "task_lease", "delivery_task_dependency", "delivery_task", "verification_gate",
                "acceptance_criterion", "feature_run"}) {
            assertEquals(0, count(table), table + " should be purged");
        }
        assertEquals(1, count("audit_ledger_entry"), "append-only audit records survive run deletion");
    }

    private void schema() {
        db.execute("create table feature_run(id varchar primary key)");
        db.execute("create table verification_gate(id varchar primary key,run_id varchar not null references feature_run(id))");
        db.execute("create table acceptance_criterion(id varchar primary key,run_id varchar not null references feature_run(id))");
        db.execute("create table delivery_task(id varchar primary key,run_id varchar not null references feature_run(id),verification_gate_id varchar references verification_gate(id))");
        db.execute("create table delivery_task_dependency(task_id varchar references delivery_task(id),dependency_id varchar references delivery_task(id))");
        db.execute("create table task_lease(id varchar primary key,task_id varchar references delivery_task(id))");
        db.execute("create table provider_attempt(id varchar primary key,task_id varchar references delivery_task(id))");
        db.execute("create table repair_package(id varchar primary key,task_id varchar references delivery_task(id))");
        db.execute("create table verification_evidence(id varchar primary key,task_id varchar references delivery_task(id))");
        db.execute("create table review_evidence(id varchar primary key,task_id varchar references delivery_task(id))");
        db.execute("create table review_criterion_assessment(id varchar primary key,review_id varchar references review_evidence(id) on delete cascade)");
        db.execute("create table github_publication(id varchar primary key,feature_run_id varchar)");
        db.execute("create table artifact_metadata(id varchar primary key,run_id varchar)");
        db.execute("create table runner_event(id varchar primary key,run_id varchar)");
        db.execute("create table human_escalation(id varchar primary key,run_id varchar)");
        db.execute("create table audit_ledger_entry(id varchar primary key,resource_id varchar)");
    }

    private void seed() {
        db.update("insert into feature_run values ('run-1')");
        db.update("insert into verification_gate values ('gate-1','run-1')");
        db.update("insert into acceptance_criterion values ('criterion-1','run-1')");
        db.update("insert into delivery_task values ('task-1','run-1','gate-1')");
        db.update("insert into delivery_task values ('task-2','run-1',null)");
        db.update("insert into delivery_task_dependency values ('task-2','task-1')");
        db.update("insert into task_lease values ('lease-1','task-1')");
        db.update("insert into provider_attempt values ('attempt-1','task-1')");
        db.update("insert into repair_package values ('repair-1','task-1')");
        db.update("insert into verification_evidence values ('verification-1','task-1')");
        db.update("insert into review_evidence values ('review-1','task-1')");
        db.update("insert into review_criterion_assessment values ('assessment-1','review-1')");
        db.update("insert into github_publication values ('publication-1','run-1')");
        db.update("insert into artifact_metadata values ('artifact-1','run-1')");
        db.update("insert into runner_event values ('event-1','run-1')");
        db.update("insert into human_escalation values ('escalation-1','run-1')");
        db.update("insert into audit_ledger_entry values ('audit-1','run-1')");
    }

    private int count(String table) { return db.queryForObject("select count(*) from " + table, Integer.class); }
}
