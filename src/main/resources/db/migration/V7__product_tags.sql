-- CloudShop tags with the role read by ProductTags: SECTION, SUBSECTION, MAKE, MODEL, BRAND, OTHER.
create table product_tags (
  product_id varchar(24) not null references products(id),
  position integer not null,
  name text not null,
  kind varchar(12) not null,
  parent text,
  primary key(product_id, position)
);
create index product_tags_kind_name_idx on product_tags(kind, name, product_id);
-- Products imported before tags were read receive them on the next full synchronisation.
update sync_state set last_full_at = null where id = 1;
