-- Typo-tolerant catalog search (word_similarity). pg_trgm is a trusted extension: the database
-- owner may create it without superuser rights.
create extension if not exists pg_trgm;

-- The partner's cart survives logout and session expiry. Prices accepted by the partner are kept
-- so a later price change is still shown for confirmation. Products are not referenced: a product
-- removed from the catalog stays in the cart and is shown as unavailable.
create table partner_cart_items (
 partner_id uuid not null references partners(id) on delete cascade,
 product_id varchar(24) not null,
 store_id varchar(24) not null,
 position integer not null,
 quantity integer not null check (quantity between 1 and 999),
 accepted_price numeric,
 primary key (partner_id, product_id)
);

-- Consent to process contact data, given once at the first order and shown as given afterwards.
alter table partners add column order_consent_at timestamptz;
