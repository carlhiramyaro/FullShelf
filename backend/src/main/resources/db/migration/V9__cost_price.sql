-- I2: cost price and the cost snapshots profit will need. Production holds a real
-- ledger, so every change here is additive: nullable columns, nothing rewritten.
-- The one-time fill of old sale lines happens in the app, in the transaction that
-- first sets a product's cost, not in this migration (no product has a cost yet).
alter table products add column cost_price numeric(12, 2);
alter table products add constraint products_cost_price_check check (cost_price is null or cost_price >= 0);

-- A default is safe on a populated table: every existing line is "not estimated"
-- until the fill flags it.
alter table sale_lines add column unit_cost numeric(12, 2);
alter table sale_lines add column cost_estimated boolean not null default false;

-- Set only on write-offs, so the cost of lost stock is fixed at the time it was lost.
alter table stock_movements add column unit_cost numeric(12, 2);
alter table stock_movements add constraint stock_movements_unit_cost_check
    check (unit_cost is null or type = 'WRITE_OFF');
