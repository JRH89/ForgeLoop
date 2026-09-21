CREATE TABLE provider_attempt (
    id VARCHAR(255) PRIMARY KEY,
    task_id VARCHAR(255) NOT NULL REFERENCES delivery_task(id),
    runner_id VARCHAR(255) NOT NULL REFERENCES runner(id),
    provider VARCHAR(40) NOT NULL,
    model VARCHAR(255) NOT NULL,
    request_id_digest VARCHAR(64) NOT NULL,
    input_tokens BIGINT NOT NULL,
    output_tokens BIGINT NOT NULL,
    attempt_count INTEGER NOT NULL,
    estimated_cost_micros BIGINT NOT NULL,
    cost_known BOOLEAN NOT NULL,
    outcome VARCHAR(40) NOT NULL,
    retryable BOOLEAN NOT NULL,
    category VARCHAR(80) NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT provider_attempt_task_request_uq UNIQUE (task_id, request_id_digest)
);
CREATE INDEX provider_attempt_task_recorded_at_idx ON provider_attempt(task_id, recorded_at);
