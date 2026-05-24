-- Add PayMongo QR Ph as a first-class gateway instrument.
-- Run after database/consolidate_gcash_into_paymongo.sql on existing projects.

alter table public.orders
    drop constraint if exists orders_order_payment_method_check;

alter table public.orders
    add constraint orders_order_payment_method_check
    check (lower(order_payment_method) in ('cash', 'gcash', 'maya', 'card', 'qrph', 'paymongo'));

comment on constraint orders_order_payment_method_check on public.orders is
    'Allowed tender/instrument tokens. qrph is PayMongo dynamic QR Ph; paymongo is the fallback hosted-checkout bucket.';
