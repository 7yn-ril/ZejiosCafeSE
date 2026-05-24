-- Optional PayMongo payment metadata for Zejios Cafe.
-- Run after the existing order migrations so receipts and reports can keep
-- the gateway reference returned by the real PayMongo checkout flow.

alter table public.orders
    add column if not exists order_payment_provider text,
    add column if not exists order_payment_status text,
    add column if not exists order_payment_reference text;

alter table public.orders
    drop constraint if exists orders_order_payment_method_check;

alter table public.orders
    add constraint orders_order_payment_method_check
    check (lower(order_payment_method) in ('cash', 'gcash', 'maya', 'card', 'qrph', 'paymongo'));

comment on column public.orders.order_payment_provider is
    'Gateway provider token, e.g. paymongo.';
comment on column public.orders.order_payment_status is
    'Gateway payment status snapshot, e.g. pending, paid, failed.';
comment on column public.orders.order_payment_reference is
    'Gateway checkout session/payment reference for receipt and reconciliation.';
