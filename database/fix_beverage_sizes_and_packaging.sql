-- Repair beverage sizes, beverage recipes, and takeout packaging deduction.
-- Run after:
--   1. database/normalize_ingredient_units.sql
--   2. database/add_takeout_support.sql
--   3. database/fix_partial_complete_order.sql
--   4. database/add_cup_size_variants.sql

-- ----------------------------------------------------------------------
-- 1. Ensure takeout supply SKUs exist. Existing stock is preserved.
-- ----------------------------------------------------------------------
insert into public.ingredients (
    ingredient_id,
    ingredient_name,
    ingredient_unit,
    ingredient_current_stock,
    ingredient_minimum_stock,
    ingredient_cost_per_unit,
    ingredient_category
) values
    ('ING-201', 'Plastic Cup with Cover (16oz)', 'pcs', 200, 30, 5.00, 'Takeout Supplies'),
    ('ING-202', 'Food Styrofoam', 'pcs', 200, 30, 7.00, 'Takeout Supplies'),
    ('ING-203', 'Plastic Spoon & Fork', 'pcs', 250, 30, 2.00, 'Takeout Supplies'),
    ('ING-204', 'Plastic Cup with Cover (22oz)', 'pcs', 200, 30, 6.00, 'Takeout Supplies')
on conflict (ingredient_id) do update set
    ingredient_name = excluded.ingredient_name,
    ingredient_unit = excluded.ingredient_unit,
    ingredient_minimum_stock = excluded.ingredient_minimum_stock,
    ingredient_cost_per_unit = excluded.ingredient_cost_per_unit,
    ingredient_category = excluded.ingredient_category;

-- ----------------------------------------------------------------------
-- 2. Keep category and cup-size rules centralized for recipes and checkout.
-- ----------------------------------------------------------------------
create or replace function public.classify_product_category(p_category_name text)
returns text
language sql
immutable
as $$
    select case
        when p_category_name is null then 'food'
        when lower(p_category_name) ~
             '(beverage|drink|coffee|tea|smoothie|frappe|milk[[:space:]-]*tea|juice|shake|latte|espresso|americano|cappuccino|chocolate|lemonade|soda|refresher|cooler|brew|breve|yakult)'
            then 'beverage'
        else 'food'
    end;
$$;

create or replace function public.beverage_variant_size_oz(p_variant_name text)
returns numeric
language sql
immutable
as $$
    select case
        when lower(trim(coalesce(p_variant_name, ''))) in ('22oz', '22 oz', 'dosa', 'large')
          or lower(trim(coalesce(p_variant_name, ''))) ~ '(^|[^0-9])22([^0-9]|$)'
            then 22::numeric
        when lower(trim(coalesce(p_variant_name, ''))) in ('16oz', '16 oz', 'mezzo', 'regular', 'standard', 'small')
          or lower(trim(coalesce(p_variant_name, ''))) ~ '(^|[^0-9])16([^0-9]|$)'
            then 16::numeric
        else 16::numeric
    end;
$$;

create or replace function public.resolve_cup_ingredient_id(p_variant_name text)
returns text
language sql
immutable
as $$
    select case
        when public.beverage_variant_size_oz(p_variant_name) >= 22 then 'ING-204'
        else 'ING-201'
    end;
$$;

-- ----------------------------------------------------------------------
-- 3. Convert Standard-only beverage variants to a real cup size.
--    Bottled/pitcher products are excluded because they do not use cup
--    size options in the same way as made-to-order beverages.
-- ----------------------------------------------------------------------
with beverage_products as (
    select p.product_id
    from public.products p
    left join public.categories c on c.category_id = p.category_id
    where coalesce(p.product_is_active, true) = true
      and public.classify_product_category(c.category_name) = 'beverage'
      and lower(coalesce(c.category_name, '')) !~ '(pitcher|add[ -]?on|extra)'
      and lower(coalesce(p.product_name, '')) !~ '(pitcher|bottled|bottle)'
),
standard_only_variants as (
    select pv.product_variant_id
    from public.product_variants pv
    join beverage_products bp on bp.product_id = pv.product_id
    where coalesce(pv.variant_is_active, true) = true
      and lower(trim(pv.variant_name)) = 'standard'
      and (
          select count(*)
          from public.product_variants siblings
          where siblings.product_id = pv.product_id
            and coalesce(siblings.variant_is_active, true) = true
      ) = 1
)
update public.product_variants pv
set variant_name = '16oz'
where pv.product_variant_id in (
    select product_variant_id from standard_only_variants
);

-- ----------------------------------------------------------------------
-- 4. Add a 22oz option to cup beverages that still only have a base size.
--    Prices are copied from the base size so this migration does not make
--    business-pricing assumptions; adjust prices separately if needed.
-- ----------------------------------------------------------------------
with beverage_products as (
    select p.product_id
    from public.products p
    left join public.categories c on c.category_id = p.category_id
    where coalesce(p.product_is_active, true) = true
      and public.classify_product_category(c.category_name) = 'beverage'
      and lower(coalesce(c.category_name, '')) !~ '(pitcher|add[ -]?on|extra)'
      and lower(coalesce(p.product_name, '')) !~ '(pitcher|bottled|bottle)'
),
source_variants as (
    select distinct on (pv.product_id)
        pv.product_id,
        pv.product_variant_id as source_variant_id,
        pv.variant_price,
        pv.variant_display_order
    from public.product_variants pv
    join beverage_products bp on bp.product_id = pv.product_id
    where coalesce(pv.variant_is_active, true) = true
      and lower(trim(pv.variant_name)) in ('16oz', '16 oz', 'mezzo', 'regular', 'standard', 'small')
      and exists (
          select 1
          from public.variant_ingredients vi
          where vi.product_variant_id = pv.product_variant_id
      )
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
missing_large_variants as (
    select
        sv.product_id,
        sv.source_variant_id,
        sv.variant_price,
        coalesce(
            (
                select max(pv.variant_display_order) + 1
                from public.product_variants pv
                where pv.product_id = sv.product_id
            ),
            sv.variant_display_order + 1,
            1
        ) as variant_display_order
    from source_variants sv
    where not exists (
        select 1
        from public.product_variants existing_large
        where existing_large.product_id = sv.product_id
          and coalesce(existing_large.variant_is_active, true) = true
          and lower(trim(existing_large.variant_name)) in ('22oz', '22 oz', 'dosa', 'large')
    )
),
starting_id as (
    select coalesce(max(substring(product_variant_id from 5)::int), 0) as max_id
    from public.product_variants
    where product_variant_id ~ '^VAR-[0-9]+$'
),
numbered_variants as (
    select
        concat('VAR-', lpad((starting_id.max_id + row_number() over (order by product_id))::text, 3, '0')) as product_variant_id,
        product_id,
        source_variant_id,
        variant_price,
        variant_display_order
    from missing_large_variants
    cross join starting_id
)
insert into public.product_variants (
    product_variant_id,
    product_id,
    variant_name,
    variant_price,
    variant_display_order,
    variant_manual_stock_left,
    variant_track_inventory,
    variant_is_active
)
select
    product_variant_id,
    product_id,
    '22oz',
    variant_price,
    variant_display_order,
    0,
    true,
    true
from numbered_variants;

-- ----------------------------------------------------------------------
-- 5. Backfill missing beverage recipe rows by size.
--    mL ingredients scale from the base size to the target size.
--    pcs ingredients are copied exactly, so cups/bananas/Yakult-style
--    ingredients do not become fractional unless already configured that way.
--    Existing recipe quantities are left intact so intentional formulas are
--    not overwritten; this fills newly-added or incomplete size variants.
-- ----------------------------------------------------------------------
with beverage_products as (
    select p.product_id
    from public.products p
    left join public.categories c on c.category_id = p.category_id
    where coalesce(p.product_is_active, true) = true
      and public.classify_product_category(c.category_name) = 'beverage'
      and lower(coalesce(c.category_name, '')) !~ '(pitcher|add[ -]?on|extra)'
      and lower(coalesce(p.product_name, '')) !~ '(pitcher|bottled|bottle)'
),
source_variants as (
    select distinct on (pv.product_id)
        pv.product_id,
        pv.product_variant_id as source_variant_id,
        public.beverage_variant_size_oz(pv.variant_name) as source_size_oz
    from public.product_variants pv
    join beverage_products bp on bp.product_id = pv.product_id
    where coalesce(pv.variant_is_active, true) = true
      and lower(trim(pv.variant_name)) in ('16oz', '16 oz', 'mezzo', 'regular', 'standard', 'small')
      and exists (
          select 1
          from public.variant_ingredients vi
          where vi.product_variant_id = pv.product_variant_id
      )
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
target_variants as (
    select
        pv.product_id,
        pv.product_variant_id,
        pv.variant_name,
        public.beverage_variant_size_oz(pv.variant_name) as target_size_oz
    from public.product_variants pv
    join beverage_products bp on bp.product_id = pv.product_id
    where coalesce(pv.variant_is_active, true) = true
      and lower(trim(pv.variant_name)) in ('22oz', '22 oz', 'dosa', 'large')
),
desired_recipe as (
    select
        tv.product_variant_id,
        vi.ingredient_id,
        case
            when lower(coalesce(i.ingredient_unit, '')) = 'ml' then
                round((vi.required_quantity * tv.target_size_oz / nullif(sv.source_size_oz, 0))::numeric, 2)
            else vi.required_quantity
        end as required_quantity
    from target_variants tv
    join source_variants sv on sv.product_id = tv.product_id
    join public.variant_ingredients vi on vi.product_variant_id = sv.source_variant_id
    join public.ingredients i on i.ingredient_id = vi.ingredient_id
),
starting_id as (
    select coalesce(max(substring(variant_ingredient_id from 5)::int), 0) as max_id
    from public.variant_ingredients
    where variant_ingredient_id ~ '^RCP-[0-9]+$'
),
numbered_recipe_rows as (
    select
        concat('RCP-', lpad((starting_id.max_id + row_number() over (order by product_variant_id, ingredient_id))::text, 3, '0')) as variant_ingredient_id,
        product_variant_id,
        ingredient_id,
        required_quantity
    from desired_recipe
    cross join starting_id
)
insert into public.variant_ingredients (
    variant_ingredient_id,
    product_variant_id,
    ingredient_id,
    required_quantity
)
select
    variant_ingredient_id,
    product_variant_id,
    ingredient_id,
    required_quantity
from numbered_recipe_rows
on conflict (product_variant_id, ingredient_id) do nothing;

update public.product_variants pv
set
    variant_track_inventory = true,
    variant_manual_stock_left = 0
where coalesce(pv.variant_is_active, true) = true
  and exists (
      select 1
      from public.variant_ingredients vi
      where vi.product_variant_id = pv.product_variant_id
  );

-- ----------------------------------------------------------------------
-- 6. Replace complete_order so packaging follows order type and item type.
--    Dine-in: no packaging.
--    Takeout beverage: cups only, based on beverage variant size and qty.
--    Takeout food: styro + spoon/fork only, based on food qty.
-- ----------------------------------------------------------------------
drop function if exists public.complete_order(text);

create or replace function public.complete_order(
    p_order_number text
)
returns table (
    completed_order_id uuid,
    completed_order_number text,
    completed_order_status text,
    completed_order_inventory_deducted boolean,
    fully_completed boolean,
    blocked_items jsonb
)
language plpgsql
security definer
set search_path = public
as $$
declare
    v_order record;
    v_item record;
    v_blocked jsonb := '[]'::jsonb;
    v_display_name text;
    v_insufficient_reason text;
    v_remaining_count integer;
    v_total_count integer;
    v_cup_row record;
    v_food_units integer;
begin
    if auth.uid() is null then
        raise exception 'Authenticated session required to complete an order.';
    end if;

    if nullif(trim(coalesce(p_order_number, '')), '') is null then
        raise exception 'Order number is required.';
    end if;

    select
        orders.order_id,
        orders.order_number,
        orders.order_status,
        orders.order_inventory_deducted,
        orders.order_type
    into v_order
    from public.orders
    where orders.order_number = trim(p_order_number)
    for update;

    if not found then
        raise exception 'Order % was not found.', p_order_number;
    end if;

    if not exists (select 1 from public.order_items oi where oi.order_id = v_order.order_id) then
        raise exception 'Order % has no items to complete.', v_order.order_number;
    end if;

    for v_item in
        select
            oi.product_variant_id,
            oi.order_item_product_name,
            oi.order_item_variant_name,
            oi.order_item_quantity,
            pv.variant_track_inventory,
            pv.variant_manual_stock_left
        from public.order_items oi
        join public.product_variants pv on pv.product_variant_id = oi.product_variant_id
        where oi.order_id = v_order.order_id
          and oi.order_item_inventory_deducted = false
    loop
        v_display_name := case
            when lower(v_item.order_item_variant_name) in ('standard', 'combo')
                then v_item.order_item_product_name
            else format('%s (%s)', v_item.order_item_product_name, v_item.order_item_variant_name)
        end;

        if v_item.variant_track_inventory then
            if not exists (
                select 1 from public.variant_ingredients vi
                where vi.product_variant_id = v_item.product_variant_id
            ) then
                v_blocked := v_blocked || jsonb_build_array(jsonb_build_object(
                    'name', v_display_name,
                    'reason', 'no recipe defined'
                ));
                continue;
            end if;

            perform 1
            from public.ingredients i
            where i.ingredient_id in (
                select vi.ingredient_id
                from public.variant_ingredients vi
                where vi.product_variant_id = v_item.product_variant_id
            )
            for update;

            select format('%s short (needs %s %s, have %s %s)',
                          i.ingredient_name,
                          (vi.required_quantity * v_item.order_item_quantity)::numeric(12,2),
                          i.ingredient_unit,
                          i.ingredient_current_stock,
                          i.ingredient_unit)
            into v_insufficient_reason
            from public.variant_ingredients vi
            join public.ingredients i on i.ingredient_id = vi.ingredient_id
            where vi.product_variant_id = v_item.product_variant_id
              and i.ingredient_current_stock < (vi.required_quantity * v_item.order_item_quantity)
            order by i.ingredient_name
            limit 1;

            if v_insufficient_reason is not null then
                v_blocked := v_blocked || jsonb_build_array(jsonb_build_object(
                    'name', v_display_name,
                    'reason', v_insufficient_reason
                ));
                v_insufficient_reason := null;
                continue;
            end if;

            update public.ingredients i
            set ingredient_current_stock = round(
                (i.ingredient_current_stock - (vi.required_quantity * v_item.order_item_quantity))::numeric, 2
            )
            from public.variant_ingredients vi
            where vi.ingredient_id = i.ingredient_id
              and vi.product_variant_id = v_item.product_variant_id;
        else
            perform 1
            from public.product_variants
            where product_variant_id = v_item.product_variant_id
            for update;

            if v_item.variant_manual_stock_left < v_item.order_item_quantity then
                v_blocked := v_blocked || jsonb_build_array(jsonb_build_object(
                    'name', v_display_name,
                    'reason', format('only %s left', v_item.variant_manual_stock_left)
                ));
                continue;
            end if;

            update public.product_variants
            set variant_manual_stock_left = greatest(
                variant_manual_stock_left - v_item.order_item_quantity, 0
            )
            where product_variant_id = v_item.product_variant_id;
        end if;

        update public.order_items
        set order_item_inventory_deducted = true,
            order_item_is_completed = true
        where order_id = v_order.order_id
          and product_variant_id = v_item.product_variant_id
          and order_item_inventory_deducted = false;
    end loop;

    select count(*) into v_total_count
    from public.order_items
    where order_id = v_order.order_id;

    select count(*) into v_remaining_count
    from public.order_items
    where order_id = v_order.order_id
      and order_item_inventory_deducted = false;

    if v_remaining_count = 0 and v_total_count > 0 then
        if not v_order.order_inventory_deducted
           and lower(coalesce(v_order.order_type, 'dine_in')) = 'takeout' then
            for v_cup_row in
                with order_classified as (
                    select
                        oi.product_variant_id,
                        oi.order_item_variant_name,
                        sum(oi.order_item_quantity)::integer as quantity,
                        public.classify_product_category(pc.category_name) as kind
                    from public.order_items oi
                    join public.product_variants pv on pv.product_variant_id = oi.product_variant_id
                    join public.products p on p.product_id = pv.product_id
                    left join public.categories pc on pc.category_id = p.category_id
                    where oi.order_id = v_order.order_id
                    group by
                        oi.product_variant_id,
                        oi.order_item_variant_name,
                        pc.category_name
                )
                select
                    public.resolve_cup_ingredient_id(order_item_variant_name) as cup_ingredient_id,
                    sum(quantity)::integer as cup_count
                from order_classified
                where kind = 'beverage'
                group by public.resolve_cup_ingredient_id(order_item_variant_name)
            loop
                update public.ingredients
                set ingredient_current_stock = greatest(
                    ingredient_current_stock - v_cup_row.cup_count,
                    0
                )
                where ingredient_id = v_cup_row.cup_ingredient_id;
            end loop;

            with order_classified as (
                select
                    oi.product_variant_id,
                    sum(oi.order_item_quantity)::integer as quantity,
                    public.classify_product_category(pc.category_name) as kind
                from public.order_items oi
                join public.product_variants pv on pv.product_variant_id = oi.product_variant_id
                join public.products p on p.product_id = pv.product_id
                left join public.categories pc on pc.category_id = p.category_id
                where oi.order_id = v_order.order_id
                group by
                    oi.product_variant_id,
                    pc.category_name
            )
            select coalesce(sum(quantity) filter (where kind = 'food'), 0)::integer
            into v_food_units
            from order_classified;

            if v_food_units > 0 then
                update public.ingredients
                set ingredient_current_stock = greatest(ingredient_current_stock - v_food_units, 0)
                where ingredient_id = 'ING-202';

                update public.ingredients
                set ingredient_current_stock = greatest(ingredient_current_stock - v_food_units, 0)
                where ingredient_id = 'ING-203';
            end if;
        end if;

        update public.orders
        set
            order_status = 'completed',
            order_completed_at = coalesce(order_completed_at, now()),
            order_inventory_deducted = true
        where order_id = v_order.order_id
        returning
            orders.order_id,
            orders.order_number,
            orders.order_status,
            orders.order_inventory_deducted
        into
            completed_order_id,
            completed_order_number,
            completed_order_status,
            completed_order_inventory_deducted;

        fully_completed := true;
    else
        completed_order_id := v_order.order_id;
        completed_order_number := v_order.order_number;
        completed_order_status := v_order.order_status;
        completed_order_inventory_deducted := v_order.order_inventory_deducted;
        fully_completed := false;
    end if;

    blocked_items := v_blocked;

    return next;
end;
$$;

revoke all on function public.complete_order(text) from public;
grant execute on function public.complete_order(text) to authenticated;
grant execute on function public.classify_product_category(text) to authenticated;
grant execute on function public.beverage_variant_size_oz(text) to authenticated;
grant execute on function public.resolve_cup_ingredient_id(text) to authenticated;

-- Optional validation queries:
-- 1. Beverages that still have fewer than two active cup-size variants:
-- select c.category_name, p.product_name, count(*) as active_size_count
-- from public.products p
-- join public.product_variants pv on pv.product_id = p.product_id
-- left join public.categories c on c.category_id = p.category_id
-- where coalesce(p.product_is_active, true) = true
--   and coalesce(pv.variant_is_active, true) = true
--   and public.classify_product_category(c.category_name) = 'beverage'
--   and lower(coalesce(c.category_name, '')) !~ '(pitcher|add[ -]?on|extra)'
--   and lower(coalesce(p.product_name, '')) !~ '(pitcher|bottled|bottle)'
-- group by c.category_name, p.product_name
-- having count(*) < 2
-- order by c.category_name, p.product_name;
--
-- 2. Active beverage variants without recipe rows:
-- select c.category_name, p.product_name, pv.variant_name
-- from public.product_variants pv
-- join public.products p on p.product_id = pv.product_id
-- left join public.categories c on c.category_id = p.category_id
-- left join public.variant_ingredients vi on vi.product_variant_id = pv.product_variant_id
-- where coalesce(p.product_is_active, true) = true
--   and coalesce(pv.variant_is_active, true) = true
--   and public.classify_product_category(c.category_name) = 'beverage'
-- group by c.category_name, p.product_name, pv.variant_name
-- having count(vi.variant_ingredient_id) = 0
-- order by c.category_name, p.product_name, pv.variant_name;
