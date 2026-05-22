-- Fix legacy beverage size labels so the POS uses the same beverage sizes
-- everywhere: 16oz and 22oz only.
--
-- Run this after the beverage size/packaging scripts. It is safe to re-run.

-- 1. If a product already has an active canonical size, hide the duplicate
--    legacy alias for that same size. This catches milk tea, creamcheese,
--    cheesecake series, and any other beverage rows that still use the old
--    labels.
with legacy_aliases as (
    select
        pv.product_variant_id,
        pv.product_id,
        case
            when lower(trim(pv.variant_name)) in ('mezzo', '16 oz') then '16oz'
            when lower(trim(pv.variant_name)) in ('grande', 'dosa', '22 oz') then '22oz'
        end as canonical_name
    from public.product_variants pv
    join public.products p on p.product_id = pv.product_id
    left join public.categories c on c.category_id = p.category_id
    where coalesce(pv.variant_is_active, true) = true
      and lower(trim(pv.variant_name)) in ('mezzo', 'grande', 'dosa', '16 oz', '22 oz')
      and lower(coalesce(c.category_name, '')) !~ '(add[ -]?on|extra)'
)
update public.product_variants pv
set variant_is_active = false
from legacy_aliases alias
where pv.product_variant_id = alias.product_variant_id
  and exists (
      select 1
      from public.product_variants canonical
      where canonical.product_id = alias.product_id
        and canonical.product_variant_id <> alias.product_variant_id
        and coalesce(canonical.variant_is_active, true) = true
        and lower(trim(canonical.variant_name)) = alias.canonical_name
  );

-- 2. Rename the remaining active legacy variants to canonical labels.
with legacy_aliases as (
    select
        pv.product_variant_id,
        case
            when lower(trim(pv.variant_name)) in ('mezzo', '16 oz') then '16oz'
            when lower(trim(pv.variant_name)) in ('grande', 'dosa', '22 oz') then '22oz'
        end as canonical_name
    from public.product_variants pv
    join public.products p on p.product_id = pv.product_id
    left join public.categories c on c.category_id = p.category_id
    where coalesce(pv.variant_is_active, true) = true
      and lower(trim(pv.variant_name)) in ('mezzo', 'grande', 'dosa', '16 oz', '22 oz')
      and lower(coalesce(c.category_name, '')) !~ '(add[ -]?on|extra)'
)
update public.product_variants pv
set variant_name = alias.canonical_name
from legacy_aliases alias
where pv.product_variant_id = alias.product_variant_id
  and pv.variant_name <> alias.canonical_name;

-- 3. Normalize historical/open order item labels so dashboards and receipts
--    do not keep showing the old size names.
update public.order_items oi
set order_item_variant_name = case
    when lower(trim(oi.order_item_variant_name)) in ('mezzo', '16 oz') then '16oz'
    when lower(trim(oi.order_item_variant_name)) in ('grande', 'dosa', '22 oz') then '22oz'
    else oi.order_item_variant_name
end
where lower(trim(oi.order_item_variant_name)) in ('mezzo', 'grande', 'dosa', '16 oz', '22 oz');

-- Quick check:
-- select c.category_name, p.product_name, pv.variant_name
-- from public.product_variants pv
-- join public.products p on p.product_id = pv.product_id
-- left join public.categories c on c.category_id = p.category_id
-- where coalesce(pv.variant_is_active, true) = true
--   and lower(trim(pv.variant_name)) in ('mezzo', 'grande', 'dosa', '16 oz', '22 oz')
-- order by p.product_name, pv.variant_name;
