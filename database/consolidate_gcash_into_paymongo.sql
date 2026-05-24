-- Consolidate legacy GCash payment rows into the current QR Ph payment flow.
--
-- The cashier now picks Cash or QR Ph at the counter. Historical rows that
-- used the old GCash/Maya method tokens should report as QR Ph.
--
-- This migration:
--   1. Rewrites historical GCash/Maya-style method tokens to 'qrph'.
--   2. Keeps provider='paymongo' for gateway reconciliation.
--   3. Keeps card/paymongo in the allow-list only for historical hosted
--      checkout rows and hidden fallback support.
--
-- Run after database/add_paymongo_payment_support.sql.

update public.orders
set
    order_payment_method = 'qrph',
    order_payment_provider = coalesce(order_payment_provider, 'paymongo')
where lower(order_payment_method) in ('gcash', 'maya', 'paymaya', 'qr_ph');

alter table public.orders
    drop constraint if exists orders_order_payment_method_check;

alter table public.orders
    add constraint orders_order_payment_method_check
    check (lower(order_payment_method) in ('cash', 'card', 'qrph', 'paymongo'));
