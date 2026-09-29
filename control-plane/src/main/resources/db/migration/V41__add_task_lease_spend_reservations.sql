ALTER TABLE task_lease
    ADD COLUMN reserved_micros bigint NOT NULL DEFAULT 0;

ALTER TABLE task_lease
    ADD CONSTRAINT task_lease_reserved_micros_bounds
        CHECK (reserved_micros BETWEEN 0 AND 1000000000000);
