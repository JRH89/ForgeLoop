ALTER TABLE organization_membership ADD COLUMN github_login varchar(255);
ALTER TABLE organization_membership ADD COLUMN accepted_at timestamptz;
UPDATE organization_membership SET accepted_at = CURRENT_TIMESTAMP;
