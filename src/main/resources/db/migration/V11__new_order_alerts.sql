-- When staff were told about a new order. Orders placed before this migration count as
-- announced, so enabling the alert does not replay the whole order history.
alter table local_orders add column announced_at timestamptz;
update local_orders set announced_at = created_at;
create index local_orders_announce_idx on local_orders(created_at) where announced_at is null;
