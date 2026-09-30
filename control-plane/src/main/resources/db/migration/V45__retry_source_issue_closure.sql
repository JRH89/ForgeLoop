alter table github_publication add column source_issue_number integer;
alter table github_publication add column source_issue_closed_at timestamptz;

update github_publication publication
set source_issue_number = case
    when substring(run.source_ref from '^issue-([1-9][0-9]*)$')::numeric <= 2147483647
        then substring(run.source_ref from '^issue-([1-9][0-9]*)$')::integer
    else null
end
from feature_run run
where publication.feature_run_id = run.id
  and run.source_ref ~ '^issue-[1-9][0-9]*$'
  and length(substring(run.source_ref from '^issue-([1-9][0-9]*)$')) <= 10;

create index github_publication_pending_issue_close_idx
    on github_publication (merged_at)
    where merged_at is not null and source_issue_number is not null and source_issue_closed_at is null;
