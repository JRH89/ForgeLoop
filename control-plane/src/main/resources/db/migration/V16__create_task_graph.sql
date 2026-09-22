ALTER TABLE delivery_task ADD COLUMN IF NOT EXISTS plan_key VARCHAR(80);
ALTER TABLE delivery_task ADD COLUMN IF NOT EXISTS owned_paths VARCHAR(4000);
ALTER TABLE delivery_task ADD COLUMN IF NOT EXISTS budget_micros BIGINT NOT NULL DEFAULT 0;
ALTER TABLE delivery_task ADD COLUMN IF NOT EXISTS change_sha VARCHAR(64);

UPDATE delivery_task SET plan_key = lower(role) || '-' || id WHERE plan_key IS NULL;
ALTER TABLE delivery_task ALTER COLUMN plan_key SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS delivery_task_run_plan_key_uq ON delivery_task(run_id, plan_key);

CREATE TABLE IF NOT EXISTS delivery_task_dependency (
    task_id VARCHAR(255) NOT NULL REFERENCES delivery_task(id),
    dependency_id VARCHAR(255) NOT NULL REFERENCES delivery_task(id),
    PRIMARY KEY (task_id, dependency_id),
    CONSTRAINT delivery_task_dependency_not_self CHECK (task_id <> dependency_id)
);

CREATE TABLE IF NOT EXISTS repair_package (
    id VARCHAR(255) PRIMARY KEY,
    task_id VARCHAR(255) NOT NULL REFERENCES delivery_task(id),
    attempt INTEGER NOT NULL,
    failure_category VARCHAR(80) NOT NULL,
    change_sha VARCHAR(64),
    evidence_digest VARCHAR(64),
    owned_paths VARCHAR(4000) NOT NULL,
    acceptance_criteria VARCHAR(12000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT repair_package_task_attempt_uq UNIQUE (task_id, attempt)
);
