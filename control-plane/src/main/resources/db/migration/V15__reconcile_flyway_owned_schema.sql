-- Older development installations let Hibernate create these objects. Flyway must own
-- the complete schema so a new database can start with ddl-auto=validate.
CREATE TABLE IF NOT EXISTS acceptance_criterion (
    id VARCHAR(255) PRIMARY KEY,
    run_id VARCHAR(255) NOT NULL REFERENCES feature_run(id),
    statement VARCHAR(4000),
    coverage_state VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS verification_gate (
    id VARCHAR(255) PRIMARY KEY,
    run_id VARCHAR(255) NOT NULL REFERENCES feature_run(id),
    name VARCHAR(255),
    required BOOLEAN NOT NULL,
    state VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS github_delivery (
    id VARCHAR(255) PRIMARY KEY,
    delivery_id VARCHAR(255) NOT NULL UNIQUE,
    event_type VARCHAR(255) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS runner_registration_token (
    id VARCHAR(255) PRIMARY KEY,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    organization_id VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS task_lease (
    id VARCHAR(255) PRIMARY KEY,
    task_id VARCHAR(255) NOT NULL UNIQUE REFERENCES delivery_task(id),
    runner_id VARCHAR(255) NOT NULL REFERENCES runner(id),
    nonce_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    acknowledged_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE
);

-- V0 used repository as the primary key before RepositoryConnection gained a generated ID.
ALTER TABLE repository_connection ADD COLUMN IF NOT EXISTS id VARCHAR(255);
UPDATE repository_connection
SET id = md5(repository || clock_timestamp()::text || random()::text)
WHERE id IS NULL;
ALTER TABLE repository_connection ALTER COLUMN id SET NOT NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'repository_connection'::regclass
          AND contype = 'p'
          AND pg_get_constraintdef(oid) = 'PRIMARY KEY (repository)'
    ) THEN
        ALTER TABLE repository_connection DROP CONSTRAINT repository_connection_pkey;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'repository_connection'::regclass AND contype = 'p'
    ) THEN
        ALTER TABLE repository_connection ADD CONSTRAINT repository_connection_pkey PRIMARY KEY (id);
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS repository_connection_repository_uq
    ON repository_connection(repository);
ALTER TABLE repository_connection ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE repository_connection ADD COLUMN IF NOT EXISTS policy_revision INTEGER NOT NULL DEFAULT 1;

-- Tighten baseline columns to the entity invariants. Fresh schemas are empty, while
-- upgraded schemas already contain application-created non-null values.
ALTER TABLE runner ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE runner ALTER COLUMN name SET NOT NULL;
ALTER TABLE runner ALTER COLUMN version SET NOT NULL;
ALTER TABLE runner ALTER COLUMN capabilities SET NOT NULL;
ALTER TABLE runner ALTER COLUMN registered_at SET NOT NULL;
ALTER TABLE runner ALTER COLUMN last_heartbeat_at SET NOT NULL;
ALTER TABLE runner ALTER COLUMN credential_hash SET NOT NULL;
ALTER TABLE runner ALTER COLUMN enabled SET NOT NULL;

ALTER TABLE repository_connection ALTER COLUMN repository SET NOT NULL;
ALTER TABLE repository_connection ALTER COLUMN installation_id SET NOT NULL;
ALTER TABLE repository_connection ALTER COLUMN default_branch SET NOT NULL;
ALTER TABLE repository_connection ALTER COLUMN issue_label SET NOT NULL;
ALTER TABLE repository_connection ALTER COLUMN harness_profile SET NOT NULL;
ALTER TABLE repository_connection ALTER COLUMN required_gates SET NOT NULL;
ALTER TABLE repository_connection ALTER COLUMN max_budget_usd SET NOT NULL;
