create table stores (
  id varchar(24) primary key,
  name text not null
);
insert into stores(id, name) values
 ('64feaaa51c9799143b532fcf', 'Car City'),
 ('698c479d4bb8342bd30e3c33', 'БАКОРДА'),
 ('64feaa7d401f7a28d35cda23', 'жибек жолы АВТО'),
 ('6967a39f62918c7e760f3747', 'СКЛАД');

create table products (
  id varchar(24) primary key,
  name text not null,
  sku text,
  code text,
  unit text,
  catalog_price numeric,
  category_id text,
  remote_updated_at timestamptz,
  imported_at timestamptz not null,
  version bigint not null default 0
);
create index products_sku_idx on products(sku);
create table product_images (
  product_id varchar(24) not null references products(id),
  position integer not null,
  url text not null,
  primary key(product_id, position)
);
create table product_offers (
  product_id varchar(24) not null references products(id),
  store_id varchar(24) not null references stores(id),
  quantity numeric not null,
  price numeric,
  primary key(product_id, store_id)
);
create table sync_state (
  id integer primary key check (id = 1),
  checkpoint timestamptz,
  last_full_at timestamptz
);
insert into sync_state(id) values (1);
create table sync_runs (
  id uuid primary key,
  mode varchar(20) not null,
  status varchar(20) not null,
  started_at timestamptz not null,
  finished_at timestamptz,
  product_count integer,
  error_code varchar(80)
);
