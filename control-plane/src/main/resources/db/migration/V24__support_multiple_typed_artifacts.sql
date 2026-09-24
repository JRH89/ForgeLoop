alter table artifact_metadata drop constraint artifact_metadata_lease_id_key;
alter table artifact_metadata add column artifact_type varchar(40) not null default 'VERIFICATION_BUNDLE';
alter table artifact_metadata add column display_name varchar(160) not null default 'evidence.json';
alter table artifact_metadata add constraint artifact_metadata_lease_name_uq unique (lease_id, display_name);
