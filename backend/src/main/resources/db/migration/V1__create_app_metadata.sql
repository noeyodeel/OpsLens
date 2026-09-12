create table app_metadata (
    metadata_key varchar(100) primary key,
    metadata_value text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

insert into app_metadata (metadata_key, metadata_value)
values ('schema.version', '1');
