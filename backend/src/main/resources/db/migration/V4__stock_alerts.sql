-- One row per product tracking whether a low/negative-stock alert is
-- currently "open" (already notified, not yet reset) — bookkeeping for the
-- notifier, not a catalog attribute, so it's its own table rather than a
-- column on products. It can't be derived from the ledger: alert_level on
-- products is owner-editable, so replaying history against a level that has
-- changed over time can't reliably reconstruct "was this already alerted."
create table stock_alerts (
    product_id      bigint      primary key references products (id),
    active          boolean     not null default false,
    triggered_at    timestamptz,
    updated_at      timestamptz not null default now()
);
