-- Why a delivery attempt became UNKNOWN (fixed texts and CloudShop error messages, no bodies).
alter table delivery_outbox add column failure_reason varchar(300);

-- Staff record the outcome of a manual check in CloudShop. The state stays UNKNOWN, so the
-- worker never picks the order up again; a found sale is kept by its number only.
alter table delivery_outbox add column reconciled_result varchar(16);
alter table delivery_outbox add column reconciled_sale_number bigint;
alter table delivery_outbox add column reconciled_by varchar(64);
alter table delivery_outbox add column reconciled_at timestamptz;
alter table delivery_outbox add constraint delivery_outbox_reconciliation_check check
 ((reconciled_result is null and reconciled_sale_number is null
   and reconciled_by is null and reconciled_at is null)
 or (state = 'UNKNOWN' and reconciled_by is not null and reconciled_at is not null
   and ((reconciled_result = 'FOUND' and reconciled_sale_number > 0)
     or (reconciled_result = 'NOT_CREATED' and reconciled_sale_number is null))));
