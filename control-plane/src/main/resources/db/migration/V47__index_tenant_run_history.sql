-- Support stable tenant-scoped run history pages without sorting the complete run table.
CREATE INDEX feature_run_tenant_history_idx ON feature_run (organization_id, created_at DESC, id DESC);
