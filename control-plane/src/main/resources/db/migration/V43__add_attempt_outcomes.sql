ALTER TABLE task_lease
    ADD COLUMN outcome VARCHAR(32),
    ADD COLUMN outcome_category VARCHAR(80);
