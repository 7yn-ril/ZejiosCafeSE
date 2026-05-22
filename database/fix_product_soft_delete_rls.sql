-- Fix product soft delete failures caused by row-level security.
--
-- Symptom:
--   new row violates row-level security policy for table "product_variants"
--
-- Cause:
--   Some Supabase projects have an UPDATE policy that only permits rows while
--   variant_is_active = true. Soft delete updates that column to false, so the
--   post-update row fails the policy check.
--
-- Run this in the Supabase SQL Editor after the product management scripts.

alter table public.products enable row level security;
alter table public.product_variants enable row level security;

alter table public.products
    add column if not exists product_image_url text;

drop policy if exists "update_products_authenticated" on public.products;

create policy "update_products_authenticated"
on public.products
for update
to authenticated
using (true)
with check (true);

drop policy if exists "update_product_variants_authenticated" on public.product_variants;

create policy "update_product_variants_authenticated"
on public.product_variants
for update
to authenticated
using (true)
with check (true);

-- Keep the read behavior explicit: the app can read active rows and historical
-- rows, then filters active menu entries client-side.
drop policy if exists "read_product_variants_authenticated" on public.product_variants;

create policy "read_product_variants_authenticated"
on public.product_variants
for select
to authenticated
using (true);

drop policy if exists "read_products_authenticated" on public.products;

create policy "read_products_authenticated"
on public.products
for select
to authenticated
using (true);
