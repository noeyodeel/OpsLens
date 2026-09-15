alter table incident
    add column target_source_code varchar(50);

create index idx_incident_target_source_code on incident (target_source_code);
