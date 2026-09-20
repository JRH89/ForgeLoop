ALTER TABLE feature_run ADD COLUMN base_branch VARCHAR(255);
UPDATE feature_run SET base_branch = 'main' WHERE base_branch IS NULL;
ALTER TABLE feature_run ALTER COLUMN base_branch SET NOT NULL;
