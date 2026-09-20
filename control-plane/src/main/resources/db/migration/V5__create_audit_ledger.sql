CREATE TABLE IF NOT EXISTS audit_ledger_entry (
    id VARCHAR(255) PRIMARY KEY,
    actor VARCHAR(255) NOT NULL,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(255) NOT NULL,
    payload_digest VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS audit_ledger_entry_resource_idx ON audit_ledger_entry(resource_type, resource_id, occurred_at);
