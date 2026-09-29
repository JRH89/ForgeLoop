ALTER TABLE repository_connection
    ADD COLUMN enforcement_protected_paths text,
    ADD COLUMN enforcement_allow_workflow_changes boolean NOT NULL DEFAULT false,
    ADD COLUMN enforcement_finish_gate varchar(80);

ALTER TABLE feature_run
    ADD COLUMN enforcement_protected_paths text,
    ADD COLUMN enforcement_allow_workflow_changes boolean NOT NULL DEFAULT false,
    ADD COLUMN enforcement_finish_gate varchar(80);
