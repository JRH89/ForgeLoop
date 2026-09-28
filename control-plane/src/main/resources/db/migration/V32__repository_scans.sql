CREATE TABLE repository_scan (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(255) NOT NULL,
    repository VARCHAR(255) NOT NULL,
    base_branch VARCHAR(255) NOT NULL,
    status VARCHAR(24) NOT NULL,
    requested_by VARCHAR(200) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    runner_id VARCHAR(36),
    commit_sha VARCHAR(64),
    provider VARCHAR(80),
    model VARCHAR(200),
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    estimated_cost_micros BIGINT NOT NULL DEFAULT 0,
    cost_known BOOLEAN NOT NULL DEFAULT FALSE,
    failure_summary VARCHAR(1000)
);

CREATE INDEX ix_repository_scan_org_status_created
    ON repository_scan (organization_id, status, created_at);
CREATE INDEX ix_repository_scan_org_repo_created
    ON repository_scan (organization_id, repository, created_at DESC);
CREATE UNIQUE INDEX ux_repository_scan_one_active_per_repo
    ON repository_scan (organization_id, repository)
    WHERE status IN ('PENDING', 'RUNNING');

CREATE TABLE repository_scan_finding (
    id VARCHAR(36) PRIMARY KEY,
    scan_id VARCHAR(36) NOT NULL REFERENCES repository_scan(id) ON DELETE CASCADE,
    severity VARCHAR(16) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(3000) NOT NULL,
    impact VARCHAR(1200) NOT NULL,
    evidence VARCHAR(1600) NOT NULL,
    affected_files VARCHAR(3000) NOT NULL,
    acceptance_criteria VARCHAR(2400) NOT NULL,
    severity_order INTEGER NOT NULL,
    issue_number INTEGER,
    issue_url VARCHAR(500)
);
