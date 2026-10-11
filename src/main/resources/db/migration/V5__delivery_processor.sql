alter table delivery_outbox drop constraint delivery_outbox_state_check;
alter table delivery_outbox add column updated_at timestamptz;
update delivery_outbox set updated_at = created_at;
alter table delivery_outbox alter column updated_at set not null;
alter table delivery_outbox alter column updated_at set default now();
alter table delivery_outbox add column customer_id varchar(24);
alter table delivery_outbox add column sale_id varchar(24);
alter table delivery_outbox add column sale_number bigint;
alter table delivery_outbox add constraint delivery_outbox_state_check check
 (state in ('WAITING_CONFIGURATION','READY','CUSTOMER_IN_FLIGHT','CUSTOMER_CREATED','SALE_IN_FLIGHT','SENT','UNKNOWN'));
alter table delivery_outbox add constraint delivery_outbox_customer_check check
 ((customer_id is null or customer_id ~ '^[a-f0-9]{24}$')
 and (state not in ('CUSTOMER_CREATED','SALE_IN_FLIGHT','SENT') or customer_id is not null)
 and (state not in ('WAITING_CONFIGURATION','READY','CUSTOMER_IN_FLIGHT') or customer_id is null));
alter table delivery_outbox add constraint delivery_outbox_sale_check check
 ((state = 'SENT' and sale_id ~ '^[a-f0-9]{24}$' and sale_id is not null and sale_number > 0 and sale_number is not null)
 or (state <> 'SENT' and sale_id is null and sale_number is null));
create index delivery_outbox_inflight_idx on delivery_outbox(updated_at)
 where state in ('CUSTOMER_IN_FLIGHT','SALE_IN_FLIGHT');
