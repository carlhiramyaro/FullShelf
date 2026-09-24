-- Soft delete: mirrors users.active rather than a hard delete, since
-- products are already referenced by stock_movements/sale_lines FKs and
-- will be referenced more once Phase C/D land.
alter table products
    add column active boolean not null default true;

-- Case-insensitive: staff pick products by name off a tile grid, so
-- "Chicken" and "chicken" coexisting as separate tiles would be confusing
-- rather than a legitimate distinct product.
create unique index products_name_unique_idx on products (lower(name));
