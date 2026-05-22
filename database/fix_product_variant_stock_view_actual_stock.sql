-- Repair POS/product availability so low-stock thresholds do not make menu
-- items unavailable.
--
-- Rule: recipe-tracked variants are unavailable only when the actual recipe
-- ingredients cannot produce one unit. Minimum stock remains a warning
-- threshold for Inventory; it is not reserved away from sellable POS stock.

create or replace view public.product_variant_stock_view as
with recipe_stock as (
    select
        vi.product_variant_id,
        floor(
            min(
                greatest(coalesce(i.ingredient_current_stock, 0), 0)
                / nullif(vi.required_quantity, 0)
            )
        )::int as recipe_stock_left
    from public.variant_ingredients vi
    left join public.ingredients i on i.ingredient_id = vi.ingredient_id
    where vi.required_quantity > 0
    group by vi.product_variant_id
)
select
    pv.product_variant_id,
    p.product_id,
    c.category_id,
    c.category_name,
    p.product_name,
    p.product_series,
    p.product_description,
    p.product_image_url,
    pv.variant_name,
    pv.variant_price,
    case
        when coalesce(pv.variant_track_inventory, false) = true then
            coalesce(rs.recipe_stock_left, 0)
        else
            coalesce(pv.variant_manual_stock_left, 0)
    end as variant_stock_left,
    coalesce(p.product_is_active, true) as product_is_active,
    coalesce(pv.variant_is_active, true) as variant_is_active
from public.product_variants pv
join public.products p on p.product_id = pv.product_id
join public.categories c on c.category_id = p.category_id
left join recipe_stock rs on rs.product_variant_id = pv.product_variant_id;

comment on view public.product_variant_stock_view is
    'Computes sellable variant stock from actual ingredient quantities; minimum stock is warning-only.';
