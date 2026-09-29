-- Persist the bounded failing-test context attached to each repair attempt.
ALTER TABLE repair_package
    ADD COLUMN failing_tests VARCHAR(4000);
