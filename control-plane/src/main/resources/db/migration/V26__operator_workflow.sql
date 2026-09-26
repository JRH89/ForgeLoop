alter table feature_run add column archived boolean not null default false;
alter table repository_connection add column required_assignee varchar(100);
