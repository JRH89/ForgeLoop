CREATE TABLE repository_verification_policy (
    id VARCHAR(255) PRIMARY KEY,
    connection_id VARCHAR(255) NOT NULL REFERENCES repository_connection(id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL,
    kind VARCHAR(40) NOT NULL,
    image_digest VARCHAR(255) NOT NULL,
    command VARCHAR(8000) NOT NULL,
    network_policy VARCHAR(20) NOT NULL,
    timeout_seconds INTEGER NOT NULL,
    required BOOLEAN NOT NULL,
    criterion_coverage VARCHAR(20) NOT NULL,
    CONSTRAINT repository_verification_policy_name_uq UNIQUE (connection_id, name),
    CONSTRAINT repository_verification_policy_timeout_ck CHECK (timeout_seconds BETWEEN 1 AND 3600)
);

ALTER TABLE verification_gate ADD COLUMN kind VARCHAR(40);
ALTER TABLE verification_gate ADD COLUMN image_digest VARCHAR(255);
ALTER TABLE verification_gate ADD COLUMN command VARCHAR(8000);
ALTER TABLE verification_gate ADD COLUMN network_policy VARCHAR(20);
ALTER TABLE verification_gate ADD COLUMN timeout_seconds INTEGER;
ALTER TABLE verification_gate ADD COLUMN criterion_coverage VARCHAR(20);
ALTER TABLE delivery_task ADD COLUMN verification_gate_id VARCHAR(255) REFERENCES verification_gate(id);
ALTER TABLE verification_evidence ADD COLUMN started_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE verification_evidence ADD COLUMN finished_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE verification_evidence ADD COLUMN artifact_reference VARCHAR(1000);
ALTER TABLE verification_evidence ADD COLUMN output_digest VARCHAR(64);
ALTER TABLE verification_evidence ADD COLUMN bundle_digest VARCHAR(64);

UPDATE verification_evidence SET started_at = recorded_at, finished_at = recorded_at, output_digest = digest, bundle_digest = digest WHERE started_at IS NULL;
ALTER TABLE verification_evidence ALTER COLUMN started_at SET NOT NULL;
ALTER TABLE verification_evidence ALTER COLUMN finished_at SET NOT NULL;
ALTER TABLE verification_evidence ALTER COLUMN output_digest SET NOT NULL;
ALTER TABLE verification_evidence ALTER COLUMN bundle_digest SET NOT NULL;
