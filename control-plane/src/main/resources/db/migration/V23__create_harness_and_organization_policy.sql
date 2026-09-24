create table organization_policy (
 organization_id varchar(255) primary key references organization(id), max_run_budget_usd double precision not null check(max_run_budget_usd>0),
 max_parallel_tasks integer not null check(max_parallel_tasks between 1 and 16), allowed_providers varchar(1000) not null, require_human_approval boolean not null, revision integer not null
);
insert into organization_policy(organization_id,max_run_budget_usd,max_parallel_tasks,allowed_providers,require_human_approval,revision)
select id,100,4,'anthropic,openai,gemini,local',true,1 from organization on conflict do nothing;
create table harness_definition (
 id varchar(36) primary key, organization_id varchar(255) not null references organization(id), name varchar(80) not null,
 description varchar(1000) not null, allowed_roles varchar(1000) not null, default_attempt_budget integer not null check(default_attempt_budget between 1 and 10),
 enabled boolean not null, revision integer not null, unique(organization_id,name)
);
insert into harness_definition(id,organization_id,name,description,allowed_roles,default_attempt_budget,enabled,revision)
select gen_random_uuid()::text,organization_id,harness_profile,'Imported repository harness','PLANNER,IMPLEMENTATION,BACKEND,FRONTEND,INDEPENDENT_TEST,INTEGRATION,REVIEW,REPAIR,VERIFICATION',2,true,1
from repository_connection group by organization_id,harness_profile on conflict do nothing;
create table local_mcp_configuration (
 id varchar(36) primary key, organization_id varchar(255) not null references organization(id), name varchar(80) not null,
 command varchar(500) not null, arguments varchar(4000) not null, allowed_roles varchar(1000) not null,
 context_tool varchar(120) not null, tool_arguments varchar(4000) not null, enabled boolean not null, revision integer not null,
 unique(organization_id,name)
);
