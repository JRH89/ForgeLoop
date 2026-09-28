CREATE TABLE repository_issue_proposal (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(255) NOT NULL,
    repository VARCHAR(255) NOT NULL,
    finding_id VARCHAR(36) NOT NULL REFERENCES repository_scan_finding(id) ON DELETE CASCADE,
    status VARCHAR(24) NOT NULL,
    requested_by VARCHAR(200) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    runner_id VARCHAR(36),
    failure_summary VARCHAR(500),
    proposed_title VARCHAR(200),
    proposed_body TEXT,
    proposed_criteria TEXT,
    provider VARCHAR(80),
    model VARCHAR(200),
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    estimated_cost_micros BIGINT NOT NULL DEFAULT 0,
    cost_known BOOLEAN NOT NULL DEFAULT FALSE,
    issue_number INTEGER,
    issue_url VARCHAR(500),
    rejection_reason VARCHAR(500),
    CONSTRAINT ck_repository_issue_proposal_status CHECK (status IN ('PENDING','RUNNING','READY','FAILED','REJECTED','APPROVED')),
    CONSTRAINT ck_repository_issue_proposal_usage CHECK (input_tokens >= 0 AND output_tokens >= 0 AND estimated_cost_micros >= 0 AND (cost_known OR estimated_cost_micros = 0))
);

CREATE INDEX ix_repository_issue_proposal_claim
    ON repository_issue_proposal (organization_id, status, created_at);
CREATE INDEX ix_repository_issue_proposal_finding
    ON repository_issue_proposal (finding_id, created_at DESC);
