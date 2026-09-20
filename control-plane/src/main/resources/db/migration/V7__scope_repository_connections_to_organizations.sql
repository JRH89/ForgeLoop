ALTER TABLE repository_connection ADD COLUMN IF NOT EXISTS organization_id VARCHAR(255);
UPDATE repository_connection SET organization_id = 'local-development' WHERE organization_id IS NULL;
ALTER TABLE repository_connection ALTER COLUMN organization_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS repository_connection_organization_idx ON repository_connection(organization_id);
