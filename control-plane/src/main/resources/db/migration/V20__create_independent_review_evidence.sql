CREATE TABLE review_evidence (
    id VARCHAR(255) PRIMARY KEY,
    task_id VARCHAR(255) NOT NULL REFERENCES delivery_task(id),
    runner_id VARCHAR(255) NOT NULL REFERENCES runner(id),
    approved BOOLEAN NOT NULL,
    summary VARCHAR(2000) NOT NULL,
    digest VARCHAR(64) NOT NULL UNIQUE,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE review_criterion_assessment (
    id VARCHAR(255) PRIMARY KEY,
    review_id VARCHAR(255) NOT NULL REFERENCES review_evidence(id) ON DELETE CASCADE,
    statement VARCHAR(4000) NOT NULL,
    status VARCHAR(10) NOT NULL,
    evidence VARCHAR(4000) NOT NULL
);

CREATE INDEX review_evidence_run_lookup_idx ON review_evidence(task_id, recorded_at);
CREATE INDEX review_criterion_review_idx ON review_criterion_assessment(review_id);
