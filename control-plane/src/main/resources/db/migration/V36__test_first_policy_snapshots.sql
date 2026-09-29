ALTER TABLE repository_verification_policy ADD COLUMN test_report VARCHAR(20);
ALTER TABLE verification_gate ADD COLUMN test_report VARCHAR(20);

ALTER TABLE repository_connection ADD COLUMN test_first_gate VARCHAR(80);
ALTER TABLE repository_connection ADD COLUMN test_path_globs VARCHAR(8000);

ALTER TABLE feature_run ADD COLUMN test_first_gate VARCHAR(80);
ALTER TABLE feature_run ADD COLUMN test_path_globs VARCHAR(8000);
