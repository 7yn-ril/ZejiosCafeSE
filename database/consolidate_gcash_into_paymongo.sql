-- Consolidate legacy GCash payment rows + open the schema for the new
-- per-method PayMongo capture flow.
--
-- After the PayMongo API integration shipped, the in-app "GCash" radio
-- button is going away. From now on the cashier just picks Cash or
-- PayMongo; the actual instrument used (GCash, Maya, Card, ...) is
-- captured from PayMongo's checkout-session response and stored in
-- order_payment_method, while order_payment_provider records that the
-- order was gateway-routed.
--
-- This migration:
--   1. Flags every historical order_payment_method = 'gcash' row as
--      order_payment_provider = 'paymongo'. The customer's instrument
--      stays accurate ("gcash"), and reports can now show "GCash (via
--      PayMongo)" by joining method + provider.
--   2. Expands the order_payment_method allow-list to include 'card',
--      which the gateway can now return as an instrument.
--
-- Run after database/add_paymongo_payment_support.sql.

update public.orders
set order_payment_provider = 'paymongo'
where lower(order_payment_method) = 'gcash'
  and order_payment_provider is null;

alter table public.orders
    drop constraint if exists orders_order_payment_method_check;

alter table public.orders
    add constraint orders_order_payment_method_check
    check (lower(order_payment_method) in ('cash', 'gcash', 'maya', 'card', 'qrph', 'paymongo'));
