create table artifact_metadata (
    id varchar(36) primary key,
    organization_id varchar(255) not null,
    run_id varchar(36) not null,
    task_id varchar(36) not null,
    lease_id varchar(36) not null unique,
    storage_reference varchar(1000) not null unique,
    content_type varchar(255) not null,
    size_bytes bigint not null check (size_bytes > 0),
    sha256 varchar(64) not null,
    retention_class varchar(40) not null,
    retain_until timestamp with time zone not null,
    created_at timestamp with time zone not null
);
create index artifact_metadata_run_idx on artifact_metadata(run_id, created_at);
