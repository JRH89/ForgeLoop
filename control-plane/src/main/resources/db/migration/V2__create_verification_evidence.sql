CREATE TABLE IF NOT EXISTS verification_evidence (
    id VARCHAR(255) PRIMARY KEY,
    task_id VARCHAR(255) NOT NULL REFERENCES delivery_task(id),
    runner_id VARCHAR(255) NOT NULL REFERENCES runner(id),
    kind VARCHAR(80) NOT NULL,
    image VARCHAR(255),
    command VARCHAR(4000) NOT NULL,
    exit_code INTEGER NOT NULL,
    timed_out BOOLEAN NOT NULL,
    output VARCHAR(65536) NOT NULL,
    digest VARCHAR(64) NOT NULL UNIQUE,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS verification_evidence_task_recorded_at_idx ON verification_evidence(task_id, recorded_at);
