create table local_orders (
 id uuid primary key,
 number bigint generated always as identity unique,
 idempotency_key uuid not null unique,
 owner_id uuid not null,
 request_hash varchar(64) not null,
 revision varchar(64) not null,
 status varchar(20) not null check (status = 'RECEIVED'),
 store_id varchar(24) not null,
 store_name text not null,
 customer_name varchar(80) not null,
 phone varchar(32) not null,
 comment varchar(1000) not null,
 consent_at timestamptz not null,
 created_at timestamptz not null,
 total numeric not null check (total > 0),
 total_text text not null,
 item_count integer not null check (item_count > 0)
);
create index local_orders_owner_key_idx on local_orders(owner_id, idempotency_key);
create table local_order_items (
 order_id uuid not null references local_orders(id),
 position integer not null,
 product_id varchar(24) not null,
 product_name text not null,
 sku text,
 unit text not null,
 quantity integer not null check (quantity between 1 and 999),
 price numeric not null check (price > 0),
 line_total numeric not null check (line_total > 0),
 primary key(order_id, position)
);
