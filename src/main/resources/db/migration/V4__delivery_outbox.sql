create table delivery_outbox (
 order_id uuid primary key references local_orders(id) on delete cascade,
 state varchar(32) not null check (state = 'WAITING_CONFIGURATION'),
 created_at timestamptz not null
);

-- Existing requests remain local; enqueueing does not imply a delivery attempt.
insert into delivery_outbox(order_id,state,created_at)
select id,'WAITING_CONFIGURATION',created_at from local_orders
on conflict (order_id) do nothing;
