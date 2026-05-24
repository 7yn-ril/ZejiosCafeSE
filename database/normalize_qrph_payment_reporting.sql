-- Normalize legacy e-wallet payment-method tokens into the QR Ph bucket.
-- Run after database/add_paymongo_qrph_support.sql on existing Supabase projects.
--
-- Older POS/reporting flows could store gcash or maya as the payment method.
-- The counter workflow now treats those dynamic PayMongo e-wallet scans as
-- QR Ph, so reports and exports should read one consistent method: qrph.

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

comment on constraint orders_order_payment_method_check on public.orders is
    'Allowed payment method tokens. qrph is the dynamic QR Ph counter flow; paymongo is retained only as a hosted-checkout fallback bucket.';
