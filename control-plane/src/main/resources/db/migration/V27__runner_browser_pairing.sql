CREATE TABLE runner_pairing (
  challenge VARCHAR(64) PRIMARY KEY,
  organization_id VARCHAR(255) NOT NULL,
  name VARCHAR(100) NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  consumed_at TIMESTAMPTZ
);
