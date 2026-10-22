-- When staff were told that an order needs attention (UNKNOWN or stuck before delivery).
-- Set only after the message was accepted, so a failed alert is retried on the next run.
alter table delivery_outbox add column alerted_at timestamptz;
create index delivery_outbox_alert_idx on delivery_outbox(created_at) where alerted_at is null;
