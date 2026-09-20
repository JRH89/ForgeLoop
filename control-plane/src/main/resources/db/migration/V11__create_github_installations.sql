CREATE TABLE IF NOT EXISTS github_installation (
    id VARCHAR(255) PRIMARY KEY,
    installation_id BIGINT NOT NULL UNIQUE,
    organization_id VARCHAR(255) NOT NULL,
    installed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
