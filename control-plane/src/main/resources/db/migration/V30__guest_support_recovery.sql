ALTER TABLE support_ticket ADD COLUMN requester_email_verified_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE support_recovery (
    id VARCHAR(36) PRIMARY KEY,
    ticket_id VARCHAR(36) NOT NULL REFERENCES support_ticket(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX support_recovery_ticket_created_idx ON support_recovery(ticket_id, created_at);
CREATE INDEX support_recovery_expiry_idx ON support_recovery(expires_at);
