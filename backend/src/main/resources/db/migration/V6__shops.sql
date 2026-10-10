-- Phase G1: one shared database, a shop_id on every shop-owned table.

create table shops (
    id                  bigint generated always as identity primary key,
    name                varchar(120) not null,
    active              boolean      not null default true,
    -- The next receipt number to hand out. Incremented atomically in
    -- SaleService (one UPDATE ... RETURNING), which replaces the global
    -- sale_receipt_number_seq so every shop numbers its own receipts.
    next_receipt_number bigint       not null default 1000,
    created_at          timestamptz  not null default now()
);

-- Shop #1: everything that exists today (local dev data, the demo and test
-- owner on Railway) belongs to it.
insert into shops (name) values ('Carl''s Demo Shop');

-- Roots: add nullable, backfill into shop #1, then make not null.
alter table users                add column shop_id bigint references shops (id);
alter table products             add column shop_id bigint references shops (id);
alter table sales                add column shop_id bigint references shops (id);
alter table devices              add column shop_id bigint references shops (id);
alter table device_pairing_codes add column shop_id bigint references shops (id);
alter table stock_alerts         add column shop_id bigint references shops (id);

update users                set shop_id = (select min(id) from shops);
update products             set shop_id = (select min(id) from shops);
update sales                set shop_id = (select min(id) from shops);
update devices              set shop_id = (select min(id) from shops);
update device_pairing_codes set shop_id = (select min(id) from shops);
update stock_alerts         set shop_id = (select min(id) from shops);

alter table users                alter column shop_id set not null;
alter table products             alter column shop_id set not null;
alter table sales                alter column shop_id set not null;
alter table devices              alter column shop_id set not null;
alter table device_pairing_codes alter column shop_id set not null;
alter table stock_alerts         alter column shop_id set not null;

create index idx_users_shop_id                on users (shop_id);
create index idx_products_shop_id             on products (shop_id);
create index idx_sales_shop_id                on sales (shop_id);
create index idx_devices_shop_id              on devices (shop_id);
create index idx_device_pairing_codes_shop_id on device_pairing_codes (shop_id);
create index idx_stock_alerts_shop_id         on stock_alerts (shop_id);

-- stock_movements carries its own shop_id as a second line of defence. It is
-- backfilled from its product, and a composite foreign key to the product
-- makes the database reject a movement whose shop differs from its product's.
alter table products add constraint products_id_shop_unique unique (id, shop_id);

alter table stock_movements add column shop_id bigint references shops (id);
update stock_movements m set shop_id = p.shop_id from products p where p.id = m.product_id;
alter table stock_movements alter column shop_id set not null;
alter table stock_movements
    add constraint stock_movements_product_shop_fk
    foreign key (product_id, shop_id) references products (id, shop_id);
create index idx_stock_movements_shop_id on stock_movements (shop_id);

-- Product names are unique per shop, not across the whole database.
drop index products_name_unique_idx;
create unique index products_shop_name_unique_idx on products (shop_id, lower(name));

-- Receipt numbers are unique per shop. The old global sequence goes away; the
-- counter now lives on shops.
alter table sales alter column receipt_number drop default;
alter table sales drop constraint sales_receipt_number_key;
alter table sales add constraint sales_shop_receipt_unique unique (shop_id, receipt_number);
drop sequence sale_receipt_number_seq;

update shops set next_receipt_number = greatest(1000, coalesce(
    (select max(receipt_number) + 1 from sales where sales.shop_id = shops.id), 1000));
