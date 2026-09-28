CREATE TABLE provider_activity (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(255) NOT NULL,
    repository VARCHAR(255) NOT NULL,
    activity_type VARCHAR(32) NOT NULL,
    source_id VARCHAR(200) NOT NULL,
    provider VARCHAR(80) NOT NULL,
    model VARCHAR(200) NOT NULL,
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    estimated_cost_micros BIGINT NOT NULL DEFAULT 0,
    cost_known BOOLEAN NOT NULL DEFAULT FALSE,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_provider_activity_tokens CHECK (input_tokens >= 0 AND output_tokens >= 0),
    CONSTRAINT ck_provider_activity_cost CHECK (estimated_cost_micros >= 0 AND (cost_known OR estimated_cost_micros = 0)),
    CONSTRAINT ux_provider_activity_source UNIQUE (organization_id, activity_type, source_id)
);

CREATE INDEX ix_provider_activity_org_recorded
    ON provider_activity (organization_id, recorded_at DESC);
CREATE INDEX ix_provider_activity_org_repo_recorded
    ON provider_activity (organization_id, repository, recorded_at DESC);

-- Backfill scan metadata already collected before cross-activity metering was added.
INSERT INTO provider_activity (id, organization_id, repository, activity_type, source_id, provider, model,
                               input_tokens, output_tokens, estimated_cost_micros, cost_known, recorded_at)
SELECT id, organization_id, repository, 'REPOSITORY_SCAN', id, provider, model, input_tokens, output_tokens,
       estimated_cost_micros, cost_known, COALESCE(completed_at, created_at)
FROM repository_scan
WHERE status = 'COMPLETE' AND provider IS NOT NULL AND model IS NOT NULL;
