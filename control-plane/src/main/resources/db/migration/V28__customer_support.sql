CREATE TABLE support_ticket (
    id VARCHAR(36) PRIMARY KEY,
    owner_subject VARCHAR(255),
    access_hash VARCHAR(64) NOT NULL,
    requester_name VARCHAR(100) NOT NULL,
    requester_email VARCHAR(254) NOT NULL,
    subject VARCHAR(160) NOT NULL,
    category VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX support_ticket_owner_idx ON support_ticket(owner_subject, updated_at);
CREATE INDEX support_ticket_status_idx ON support_ticket(status, updated_at);
CREATE TABLE support_message (
    id VARCHAR(36) PRIMARY KEY,
    ticket_id VARCHAR(36) NOT NULL REFERENCES support_ticket(id),
    author_subject VARCHAR(255),
    author_kind VARCHAR(16) NOT NULL,
    body VARCHAR(8000) NOT NULL,
    internal_note BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX support_message_ticket_idx ON support_message(ticket_id, created_at);
