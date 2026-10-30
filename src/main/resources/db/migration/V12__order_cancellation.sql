-- A partner asks to cancel an order; staff remove the sale in CloudShop by hand and record it.
-- The site never deletes or changes CloudShop documents itself.
alter table local_orders add column cancel_requested_at timestamptz;
alter table local_orders add column cancel_reason varchar(500);
-- When staff were told about the request; set only after Telegram accepted the message.
alter table local_orders add column cancel_announced_at timestamptz;
alter table local_orders add column cancelled_at timestamptz;
alter table local_orders add column cancelled_by varchar(64);
alter table local_orders add constraint local_orders_cancellation_check check
 ((cancel_reason is null or cancel_requested_at is not null)
 and (cancel_announced_at is null or cancel_requested_at is not null)
 and ((cancelled_at is null and cancelled_by is null)
   or (cancelled_at is not null and cancelled_by is not null)));
create index local_orders_cancel_announce_idx on local_orders(cancel_requested_at)
 where cancel_requested_at is not null and cancel_announced_at is null;
