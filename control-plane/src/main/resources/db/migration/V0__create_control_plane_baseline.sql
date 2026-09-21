-- Establish the tables that predate Flyway. Earlier deployments used Hibernate schema creation,
-- but a new production database must be able to run the versioned migrations from an empty schema.
CREATE TABLE IF NOT EXISTS feature_run (
    id VARCHAR(255) PRIMARY KEY,
    repository VARCHAR(500) NOT NULL,
    source_ref VARCHAR(500) NOT NULL,
    title VARCHAR(1000),
    specification VARCHAR(20000),
    budget_usd DOUBLE PRECISION NOT NULL DEFAULT 0,
    harness_profile VARCHAR(255),
    state VARCHAR(80),
    created_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS runner (
    id VARCHAR(255) PRIMARY KEY,
    organization_id VARCHAR(255),
    name VARCHAR(255),
    version VARCHAR(255),
    capabilities VARCHAR(2000),
    registered_at TIMESTAMP WITH TIME ZONE,
    last_heartbeat_at TIMESTAMP WITH TIME ZONE,
    credential_hash VARCHAR(64),
    enabled BOOLEAN
);

CREATE TABLE IF NOT EXISTS delivery_task (
    id VARCHAR(255) PRIMARY KEY,
    run_id VARCHAR(255) NOT NULL REFERENCES feature_run(id),
    role VARCHAR(255),
    title VARCHAR(1000),
    state VARCHAR(80),
    attempt_budget INTEGER NOT NULL DEFAULT 2,
    attempts INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS repository_connection (
    repository VARCHAR(500) PRIMARY KEY,
    installation_id BIGINT,
    default_branch VARCHAR(255),
    issue_label VARCHAR(255),
    harness_profile VARCHAR(255),
    required_gates VARCHAR(4000),
    max_budget_usd DOUBLE PRECISION
);
