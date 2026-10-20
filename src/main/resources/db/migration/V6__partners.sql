-- Partner accounts are provisioned by staff; there is no self-registration.
create table partners (
 id uuid primary key,
 login varchar(64) not null unique check (login ~ '^[a-z0-9._-]{3,64}$'),
 password_hash varchar(72) not null check (password_hash ~ '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$'),
 company varchar(120) not null check (length(btrim(company)) > 0),
 contact_name varchar(80) not null check (length(btrim(contact_name)) > 0),
 phone varchar(16) not null check (phone ~ '^\+[1-9][0-9]{9,14}$'),
 cloudshop_customer_id varchar(24) check (cloudshop_customer_id ~ '^[a-f0-9]{24}$'),
 enabled boolean not null default true,
 created_at timestamptz not null
);

-- Orders placed before partner access stay without an owner.
alter table local_orders add column partner_id uuid references partners(id);
create index local_orders_partner_idx on local_orders(partner_id, created_at desc, number desc);
