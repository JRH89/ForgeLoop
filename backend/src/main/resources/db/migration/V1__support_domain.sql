CREATE TABLE organization (
  id VARCHAR(255) PRIMARY KEY,
  name VARCHAR(255) NOT NULL
);

CREATE TABLE member (
  id VARCHAR(255) PRIMARY KEY,
  display_name VARCHAR(255) NOT NULL,
  role VARCHAR(32) NOT NULL,
  organization_id VARCHAR(255) NOT NULL REFERENCES organization(id)
);

CREATE TABLE ticket (
  id VARCHAR(255) PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL,
  organization_id VARCHAR(255) NOT NULL REFERENCES organization(id),
  assignee_id VARCHAR(255) REFERENCES member(id)
);

CREATE TABLE audit_event (
  id VARCHAR(255) PRIMARY KEY,
  action VARCHAR(64) NOT NULL,
  ticket_id VARCHAR(255) NOT NULL,
  actor_id VARCHAR(255) NOT NULL,
  assignee_id VARCHAR(255) NOT NULL,
  occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_member_organization ON member(organization_id);
CREATE INDEX idx_ticket_organization ON ticket(organization_id);
CREATE INDEX idx_audit_ticket_action ON audit_event(ticket_id, action);
