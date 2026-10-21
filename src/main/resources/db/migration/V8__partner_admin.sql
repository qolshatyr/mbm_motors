-- Clients are managed by the owner from the staff section.
alter table partners add column bin varchar(12) check (bin ~ '^[0-9]{12}$');
alter table partners add column last_login_at timestamptz;
-- Raised on every password change: sessions signed in with an older password end.
alter table partners add column credentials_version integer not null default 0;
-- PENDING: a CloudShop card request is in flight (stays if the process stops mid-request);
-- UNKNOWN: the answer was unclear. Neither is retried automatically.
alter table partners add column cloudshop_customer_state varchar(16)
  check (cloudshop_customer_state in ('PENDING', 'UNKNOWN'));
