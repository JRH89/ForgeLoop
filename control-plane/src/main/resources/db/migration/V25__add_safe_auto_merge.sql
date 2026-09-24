alter table organization_policy add column auto_merge_enabled boolean not null default false;
alter table github_publication add column auto_merge_requested boolean not null default false;
alter table github_publication add column merged_at timestamptz;
alter table github_publication add column merge_sha varchar(64);
create index github_publication_pending_auto_merge_idx
    on github_publication (auto_merge_requested, merged_at)
    where auto_merge_requested = true and merged_at is null;
