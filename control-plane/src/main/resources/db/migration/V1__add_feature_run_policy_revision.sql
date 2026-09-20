-- Safe forward migration for control planes created before repository policy revisions were persisted.
ALTER TABLE IF EXISTS feature_run
    ADD COLUMN IF NOT EXISTS policy_revision INTEGER NOT NULL DEFAULT 1;
