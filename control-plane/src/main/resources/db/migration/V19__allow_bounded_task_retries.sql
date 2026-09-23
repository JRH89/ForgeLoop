-- Preserve every completed lease attempt while allowing a bounded repair attempt to lease the same task again.
DO $$
DECLARE
    unique_constraint RECORD;
BEGIN
    FOR unique_constraint IN
        SELECT constraint_name
        FROM information_schema.table_constraints
        WHERE table_schema = current_schema()
          AND table_name = 'task_lease'
          AND constraint_type = 'UNIQUE'
          AND constraint_name IN (
              SELECT constraint_name
              FROM information_schema.constraint_column_usage
              WHERE table_schema = current_schema()
                AND table_name = 'task_lease'
                AND column_name = 'task_id'
          )
    LOOP
        EXECUTE format('ALTER TABLE task_lease DROP CONSTRAINT %I', unique_constraint.constraint_name);
    END LOOP;
END $$;

CREATE INDEX IF NOT EXISTS task_lease_task_attempt_idx ON task_lease(task_id, expires_at DESC);
