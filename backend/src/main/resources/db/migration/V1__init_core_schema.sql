create table users (
    id              bigint generated always as identity primary key,
    name            varchar(120) not null,
    role            varchar(20)  not null check (role in ('OWNER', 'STAFF')),
    pin_hash        varchar(255),
    clerk_user_id   varchar(255) unique,
    active          boolean      not null default true,
    created_at      timestamptz  not null default now()
);

create table devices (
    id          bigint generated always as identity primary key,
    token_hash  varchar(255) not null unique,
    revoked     boolean      not null default false,
    paired_at   timestamptz  not null default now()
);

create table products (
    id              bigint generated always as identity primary key,
    name            varchar(120)   not null,
    unit            varchar(10)    not null check (unit in ('KG', 'UNIT')),
    price           numeric(10, 2) not null,
    alert_level     numeric(10, 2) not null,
    carton_weight   numeric(10, 2),
    created_at      timestamptz    not null default now()
);

create sequence sale_receipt_number_seq start with 1000;

create table sales (
    id              bigint generated always as identity primary key,
    receipt_number  bigint      not null default nextval('sale_receipt_number_seq') unique,
    staff_id        bigint      not null references users (id),
    voided          boolean     not null default false,
    created_at      timestamptz not null default now()
);

create table sale_lines (
    id          bigint generated always as identity primary key,
    sale_id     bigint         not null references sales (id),
    product_id  bigint         not null references products (id),
    quantity    numeric(10, 2) not null,
    unit_price  numeric(10, 2) not null,
    discount    numeric(10, 2) not null default 0,
    line_total  numeric(10, 2) not null
);

create index idx_sale_lines_sale_id on sale_lines (sale_id);

-- Signed quantity: positive for opening/received, negative for sale/write-off.
-- A reversal or void row is simply the negation of the movement it points to,
-- so a product's balance is always sum(quantity) over this table.
create table stock_movements (
    id                      bigint generated always as identity primary key,
    product_id              bigint         not null references products (id),
    quantity                numeric(10, 2) not null,
    type                    varchar(20)    not null check (type in
                             ('OPENING', 'RECEIVED', 'SALE', 'WRITE_OFF', 'VOID', 'REVERSAL', 'COUNT_ADJUST')),
    reversed_movement_id    bigint references stock_movements (id),
    sale_line_id            bigint references sale_lines (id),
    performed_by            bigint         not null references users (id),
    note                    varchar(500),
    created_at              timestamptz    not null default now()
);

create index idx_stock_movements_product_id on stock_movements (product_id);
create index idx_stock_movements_product_created on stock_movements (product_id, created_at);
create index idx_stock_movements_sale_line_id on stock_movements (sale_line_id);
