ALTER TABLE provider_attempt
    ADD COLUMN answered_model VARCHAR(255),
    ADD COLUMN lease_id VARCHAR(255) REFERENCES task_lease(id);

ALTER TABLE verification_evidence
    ADD COLUMN lease_id VARCHAR(255) REFERENCES task_lease(id),
    ADD COLUMN target_sha VARCHAR(64),
    ADD COLUMN image_id VARCHAR(71),
    ADD COLUMN output_truncated BOOLEAN;

ALTER TABLE task_lease
    ADD COLUMN runner_revision VARCHAR(64),
    ADD COLUMN runner_jar_sha256 VARCHAR(64),
    ADD COLUMN input_refs TEXT,
    ADD COLUMN result_sha VARCHAR(64);

ALTER TABLE feature_run
    ADD COLUMN policy_snapshot TEXT,
    ADD COLUMN policy_snapshot_sha256 VARCHAR(64);

CREATE INDEX provider_attempt_lease_idx ON provider_attempt(lease_id);
CREATE INDEX verification_evidence_lease_idx ON verification_evidence(lease_id);
