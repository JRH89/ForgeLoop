CREATE TABLE IF NOT EXISTS organization (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);
CREATE TABLE IF NOT EXISTS organization_membership (
    id VARCHAR(255) PRIMARY KEY,
    organization_id VARCHAR(255) NOT NULL REFERENCES organization(id),
    subject VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL,
    CONSTRAINT organization_membership_org_subject_uq UNIQUE (organization_id, subject)
);
INSERT INTO organization (id, name) VALUES ('local-development', 'Local development') ON CONFLICT (id) DO NOTHING;
