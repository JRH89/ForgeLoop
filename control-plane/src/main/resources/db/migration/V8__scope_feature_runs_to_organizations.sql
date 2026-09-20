ALTER TABLE feature_run ADD COLUMN IF NOT EXISTS organization_id VARCHAR(255);
UPDATE feature_run SET organization_id = 'local-development' WHERE organization_id IS NULL;
ALTER TABLE feature_run ALTER COLUMN organization_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS feature_run_organization_idx ON feature_run(organization_id);
