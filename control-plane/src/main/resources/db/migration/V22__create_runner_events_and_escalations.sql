create table runner_event (
    id varchar(36) primary key,
    organization_id varchar(255) not null,
    run_id varchar(36) not null,
    task_id varchar(36) not null,
    runner_id varchar(36) not null,
    lease_id varchar(36) not null,
    sequence_number bigint not null,
    level varchar(16) not null,
    event_type varchar(64) not null,
    message varchar(2000) not null,
    occurred_at timestamp with time zone not null,
    received_at timestamp with time zone not null,
    unique (lease_id, sequence_number)
);
create index runner_event_run_timeline_idx on runner_event(run_id, occurred_at, sequence_number);

create table human_escalation (
    id varchar(36) primary key,
    organization_id varchar(255) not null,
    run_id varchar(36) not null,
    task_id varchar(36),
    reason varchar(64) not null,
    severity varchar(16) not null,
    status varchar(24) not null,
    summary varchar(1000) not null,
    created_at timestamp with time zone not null,
    acknowledged_at timestamp with time zone,
    acknowledged_by varchar(255),
    resolved_at timestamp with time zone,
    resolved_by varchar(255),
    unique (run_id, task_id, reason)
);
create index human_escalation_run_idx on human_escalation(run_id, created_at);
