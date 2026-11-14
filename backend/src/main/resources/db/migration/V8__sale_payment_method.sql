-- I1: how each sale was paid. Production holds a real ledger, so this adds the
-- column nullable, backfills, then tightens. Every sale that exists was cash
-- (the shop had no other option), so the backfill is true, not a guess.
alter table sales add column payment_method varchar(10);

update sales set payment_method = 'CASH' where payment_method is null;

alter table sales alter column payment_method set not null;
alter table sales add constraint sales_payment_method_check
    check (payment_method in ('CASH', 'MOMO'));
