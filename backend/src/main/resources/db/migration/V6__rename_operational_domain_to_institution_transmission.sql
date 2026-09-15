alter table source_system rename to external_institution;
alter table customers rename to record_subjects;
alter table orders rename to treatment_records;
alter table payments rename to verification_records;

alter table record_subjects rename column customer_no to subject_no;
alter table record_subjects rename column customer_name to subject_alias;
alter table record_subjects rename column customer_phone to required_field_value;
alter table record_subjects rename column source_system_id to external_institution_id;

alter table treatment_records rename column order_no to treatment_record_no;
alter table treatment_records rename column customer_id to record_subject_id;
alter table treatment_records rename column source_system_id to external_institution_id;
alter table treatment_records rename column order_status to record_status;
alter table treatment_records rename column order_amount to record_value;
alter table treatment_records rename column ordered_at to recorded_at;

alter table verification_records rename column payment_id to verification_record_key;
alter table verification_records rename column order_id to treatment_record_id;
alter table verification_records rename column source_system_id to external_institution_id;
alter table verification_records rename column payment_status to verification_status;
alter table verification_records rename column paid_at to verified_at;

alter table data_ingestion_log rename column source_system_id to external_institution_id;
alter table scenario rename column target_source_code to target_institution_code;
alter table incident rename column target_source_code to target_institution_code;

alter index idx_customers_source_system_id rename to idx_record_subjects_external_institution_id;
alter index idx_customers_created_at rename to idx_record_subjects_created_at;
alter index idx_orders_customer_id rename to idx_treatment_records_record_subject_id;
alter index idx_orders_source_system_id rename to idx_treatment_records_external_institution_id;
alter index idx_orders_ordered_at rename to idx_treatment_records_recorded_at;
alter index idx_orders_ingested_at rename to idx_treatment_records_ingested_at;
alter index idx_payments_payment_id rename to idx_verification_records_verification_record_key;
alter index idx_payments_order_id rename to idx_verification_records_treatment_record_id;
alter index idx_payments_source_system_id rename to idx_verification_records_external_institution_id;
alter index idx_payments_paid_at rename to idx_verification_records_verified_at;
alter index idx_data_ingestion_log_source_system_id rename to idx_data_ingestion_log_external_institution_id;
alter index idx_incident_target_source_code rename to idx_incident_target_institution_code;
