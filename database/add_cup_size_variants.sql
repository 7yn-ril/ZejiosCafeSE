-- CHANGE: Split the single takeout cup into 16oz / 22oz variants so cup
-- inventory drains based on drink size. Previous behavior: every beverage
-- line in a takeout order deducted 1 from ING-201 ("Plastic Cup with
-- Cover") regardless of drink size. New behavior: 16oz cup (ING-201) is
-- consumed by Mezzo and 16oz variants; 22oz cup (ING-204) is consumed by
-- Dosa and 22oz variants; any other beverage variant falls back to 16oz.
--
-- Dine-in orders still skip cup deduction entirely.
-- Run after fix_partial_complete_order.sql in the Supabase SQL Editor.

-- ----------------------------------------------------------------------
-- 1. Rename the existing cup to its 16oz identity and add the 22oz cup.
--    On conflict we only touch fields that describe the SKU; current
--    stock is preserved so this migration is safe to re-run.
-- ----------------------------------------------------------------------
update public.ingredients
set ingredient_name = 'Plastic Cup with Cover (16oz)'
where ingredient_id = 'ING-201'
  and ingredient_name = 'Plastic Cup with Cover';

insert into public.ingredients (
    ingredient_id,
    ingredient_name,
    ingredient_unit,
    ingredient_current_stock,
    ingredient_minimum_stock,
    ingredient_cost_per_unit,
    ingredient_category
) values
    ('ING-204', 'Plastic Cup with Cover (22oz)', 'pcs', 200, 30, 6.00, 'Takeout Supplies')
on conflict (ingredient_id) do update set
    ingredient_name = excluded.ingredient_name,
    ingredient_unit = excluded.ingredient_unit,
    ingredient_minimum_stock = excluded.ingredient_minimum_stock,
    ingredient_cost_per_unit = excluded.ingredient_cost_per_unit,
    ingredient_category = excluded.ingredient_category;

-- ----------------------------------------------------------------------
-- 2. Helper: map a beverage variant name to the cup SKU it consumes.
--    Centralizing this means complete_order() and any future caller
--    (e.g. pre-checkout stock previews) agree on the rule.
-- ----------------------------------------------------------------------
create or replace function public.resolve_cup_ingredient_id(p_variant_name text)
returns text
language sql
immutable
as $$
    select case
        when lower(trim(coalesce(p_variant_name, ''))) in ('22oz', 'dosa') then 'ING-204'
        else 'ING-201'
    end;
$$;

-- ----------------------------------------------------------------------
-- 3. Replace complete_order(). Same shape as fix_partial_complete_order
--    (returns 6 columns, per-item deduction, blocked_items list); the
--    only behavioral change is the takeout-cup block, which now splits
--    beverage lines by size and debits the matching cup SKU.
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
    v_food_lines integer;
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
    from public.order_items where order_id = v_order.order_id;

    select count(*) into v_remaining_count
    from public.order_items
    where order_id = v_order.order_id and order_item_inventory_deducted = false;

    if v_remaining_count = 0 and v_total_count > 0 then
        -- Takeout consumable deduction. Cups are split by size: each
        -- distinct beverage line picks a 16oz or 22oz cup via
        -- resolve_cup_ingredient_id(variant_name). Food containers still
        -- debit one per distinct food line.
        if not v_order.order_inventory_deducted and v_order.order_type = 'takeout' then
            for v_cup_row in
                with order_classified as (
                    select
                        oi.product_variant_id,
                        oi.order_item_variant_name,
                        public.classify_product_category(pc.category_name) as kind
                    from public.order_items oi
                    join public.product_variants pv on pv.product_variant_id = oi.product_variant_id
                    join public.products p on p.product_id = pv.product_id
                    left join public.categories pc on pc.category_id = p.category_id
                    where oi.order_id = v_order.order_id
                    group by oi.product_variant_id, oi.order_item_variant_name, pc.category_name
                )
                select
                    public.resolve_cup_ingredient_id(order_item_variant_name) as cup_ingredient_id,
                    count(*)::integer as cup_count
                from order_classified
                where kind = 'beverage'
                group by public.resolve_cup_ingredient_id(order_item_variant_name)
            loop
                update public.ingredients
                set ingredient_current_stock = greatest(
                    ingredient_current_stock - v_cup_row.cup_count, 0
                )
                where ingredient_id = v_cup_row.cup_ingredient_id;
            end loop;

            with order_classified as (
                select
                    oi.product_variant_id,
                    public.classify_product_category(pc.category_name) as kind
                from public.order_items oi
                join public.product_variants pv on pv.product_variant_id = oi.product_variant_id
                join public.products p on p.product_id = pv.product_id
                left join public.categories pc on pc.category_id = p.category_id
                where oi.order_id = v_order.order_id
                group by oi.product_variant_id, pc.category_name
            )
            select count(*) filter (where kind = 'food')
            into v_food_lines
            from order_classified;

            v_food_lines := coalesce(v_food_lines, 0);

            if v_food_lines > 0 then
                update public.ingredients
                set ingredient_current_stock = greatest(ingredient_current_stock - v_food_lines, 0)
                where ingredient_id = 'ING-202';

                update public.ingredients
                set ingredient_current_stock = greatest(ingredient_current_stock - v_food_lines, 0)
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
