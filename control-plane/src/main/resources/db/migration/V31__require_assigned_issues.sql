ALTER TABLE repository_connection
    ADD COLUMN require_assignee BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE repository_connection
SET require_assignee = TRUE
WHERE required_assignee IS NOT NULL;
