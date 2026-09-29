alter table repository_connection add column run_record_enabled boolean not null default false;
alter table feature_run add column run_record_enabled boolean not null default false;
