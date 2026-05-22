-- Repair beverage 22oz pricing.
--
-- Rule: for every product that has both an active 16oz-class variant and
-- an active 22oz-class variant, the 22oz price must be exactly 20 pesos
-- more than the 16oz price.
--
-- Background: database/fix_beverage_sizes_and_packaging.sql added 22oz
-- variants by *copying* the base price (so the migration stayed
-- business-neutral). That left some products with identical 16oz and
-- 22oz prices. This migration sets the canonical premium.
--
-- Idempotent: re-running picks up the same 16oz price and computes the
-- same target, so applying twice is a no-op after the first run.
--
-- Run after:
--   1. database/normalize_ingredient_units.sql
--   2. database/add_takeout_support.sql
--   3. database/fix_partial_complete_order.sql
--   4. database/add_cup_size_variants.sql
--   5. database/fix_beverage_sizes_and_packaging.sql

-- ----------------------------------------------------------------------
-- 1. Resolve the canonical 16oz price per product, then update every
--    22oz-class sibling to that price + 20.
--    Variant-name classification matches fix_beverage_sizes_and_packaging.sql
--    so both migrations agree on what counts as "16oz" vs "22oz".
-- ----------------------------------------------------------------------
with sixteen_oz_prices as (
    select distinct on (pv.product_id)
        pv.product_id,
        pv.variant_price as base_price
    from public.product_variants pv
    where coalesce(pv.variant_is_active, true) = true
      and lower(trim(pv.variant_name)) in ('16oz', '16 oz', 'mezzo', 'regular', 'standard', 'small')
    -- Same priority order used by fix_beverage_sizes_and_packaging.sql so
    -- that if a product happens to have multiple base-size variants, both
    -- migrations pick the same row as canonical.
    order by
        pv.product_id,
        case lower(trim(pv.variant_name))
            when '16oz' then 1
            when '16 oz' then 1
            when 'mezzo' then 2
            when 'regular' then 3
            when 'standard' then 4
            when 'small' then 5
            else 9
        end,
        pv.variant_display_order
),
target_22oz as (
    select
        pv.product_variant_id,
        round((sop.base_price + 20)::numeric, 2) as target_price
    from public.product_variants pv
    join sixteen_oz_prices sop on sop.product_id = pv.product_id
    where coalesce(pv.variant_is_active, true) = true
      and lower(trim(pv.variant_name)) in ('22oz', '22 oz', 'dosa', 'large')
)
update public.product_variants pv
set variant_price = tv.target_price
from target_22oz tv
where pv.product_variant_id = tv.product_variant_id
  and pv.variant_price is distinct from tv.target_price;

-- ----------------------------------------------------------------------
-- Optional validation queries:
--
-- 1. Confirm every 22oz variant is now exactly 16oz + 20:
-- select
--     p.product_name,
--     small.variant_name as small_name,
--     small.variant_price as small_price,
--     large.variant_name as large_name,
--     large.variant_price as large_price,
--     (large.variant_price - small.variant_price) as premium
-- from public.product_variants large
-- join public.product_variants small on small.product_id = large.product_id
-- join public.products p on p.product_id = large.product_id
-- where coalesce(large.variant_is_active, true) = true
--   and coalesce(small.variant_is_active, true) = true
--   and lower(trim(large.variant_name)) in ('22oz', '22 oz', 'dosa', 'large')
--   and lower(trim(small.variant_name)) in ('16oz', '16 oz', 'mezzo', 'regular', 'standard', 'small')
-- order by p.product_name;
--
-- 2. Surface any 22oz variants that have no 16oz sibling (these are
--    skipped by the migration and may need manual pricing review):
-- select p.product_name, pv.variant_name, pv.variant_price
-- from public.product_variants pv
-- join public.products p on p.product_id = pv.product_id
-- where coalesce(pv.variant_is_active, true) = true
--   and lower(trim(pv.variant_name)) in ('22oz', '22 oz', 'dosa', 'large')
--   and not exists (
--       select 1
--       from public.product_variants sibling
--       where sibling.product_id = pv.product_id
--         and coalesce(sibling.variant_is_active, true) = true
--         and lower(trim(sibling.variant_name)) in ('16oz', '16 oz', 'mezzo', 'regular', 'standard', 'small')
--   )
-- order by p.product_name;
