CREATE TABLE IF NOT EXISTS github_publication (
    id VARCHAR(255) PRIMARY KEY,
    feature_run_id VARCHAR(255) NOT NULL UNIQUE,
    repository VARCHAR(500) NOT NULL,
    branch VARCHAR(500) NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    head_sha VARCHAR(255),
    check_run_id BIGINT,
    pull_request_number BIGINT,
    delivered_at TIMESTAMP WITH TIME ZONE
);
