-- CHANGE: Partial completion — when a barista taps "Mark as completed", the
-- previous complete_order() rolled the WHOLE order back if any one line was
-- short on ingredients, leaving served items stuck in PREPARING with no
-- record of being done. This rewrite processes each line independently:
-- items with sufficient stock get their ingredients deducted; items that
-- can't be made stay blocked with a reason returned to the caller. The
-- order itself only transitions to 'completed' when every line is done.
--
-- IMPORTANT — order_item_is_completed vs order_item_inventory_deducted:
--   * order_item_is_completed = barista's UI kitchen-progress checkbox
--     (existing column, written by updateOrderItemCompletion).
--   * order_item_inventory_deducted = whether the recipe ingredients for
--     this specific line have actually been subtracted from stock. NEW
--     column added by this migration. complete_order() loops over items
--     where this is false, attempts to deduct, and flips it on success.
-- These were the same column before, which caused the bug where checking
-- a UI box silently caused complete_order() to skip deduction.
--
-- Run after add_takeout_support.sql in the Supabase SQL Editor.

-- ----------------------------------------------------------------------
-- 1. Schema change: per-item inventory_deducted flag + backfill from the
--    order-level flag so previously-completed orders aren't re-processed.
-- ----------------------------------------------------------------------
do $$
begin
    if not exists (
        select 1 from information_schema.columns
        where table_schema = 'public'
          and table_name = 'order_items'
          and column_name = 'order_item_inventory_deducted'
    ) then
        execute 'alter table public.order_items
                 add column order_item_inventory_deducted boolean not null default false';
        -- Backfill: any order_item belonging to an order that already had
        -- inventory deducted (under the old all-or-nothing function) is
        -- considered fully deducted so re-running complete_order is a no-op.
        execute '
            update public.order_items oi
            set order_item_inventory_deducted = true
            from public.orders o
            where o.order_id = oi.order_id
              and o.order_inventory_deducted = true
        ';
    end if;
end;
$$;

-- ----------------------------------------------------------------------
-- 2. Replace complete_order(). Old signature returned 4 columns; new one
--    returns 6 (adds fully_completed bool + blocked_items jsonb). Drop
--    first so PostgREST sees the new schema.
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
    v_beverage_lines integer;
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

    -- ------------------------------------------------------------------
    -- For each line whose ingredients haven't been deducted yet, try to
    -- deduct. Items that succeed get flagged + their UI checkbox flipped
    -- so the barista sees progress. Items that fail stay deducted=false
    -- and land in the blocked list with a reason.
    -- ------------------------------------------------------------------
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
            -- Recipe-tracked item.
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

            -- Lock the ingredient rows this item needs so concurrent
            -- completions can't race past each other and oversell stock.
            perform 1
            from public.ingredients i
            where i.ingredient_id in (
                select vi.ingredient_id
                from public.variant_ingredients vi
                where vi.product_variant_id = v_item.product_variant_id
            )
            for update;

            -- Check stock for THIS item only.
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

            -- Stock is sufficient — deduct.
            update public.ingredients i
            set ingredient_current_stock = round(
                (i.ingredient_current_stock - (vi.required_quantity * v_item.order_item_quantity))::numeric, 2
            )
            from public.variant_ingredients vi
            where vi.ingredient_id = i.ingredient_id
              and vi.product_variant_id = v_item.product_variant_id;
        else
            -- Manual-stock item.
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

        -- Flag this line as deducted AND tick the UI kitchen-progress box
        -- so the barista sees it land as completed in the order dialog.
        update public.order_items
        set order_item_inventory_deducted = true,
            order_item_is_completed = true
        where order_id = v_order.order_id
          and product_variant_id = v_item.product_variant_id
          and order_item_inventory_deducted = false;
    end loop;

    -- ------------------------------------------------------------------
    -- Finalize the order only if every line was successfully deducted.
    -- ------------------------------------------------------------------
    select count(*) into v_total_count
    from public.order_items where order_id = v_order.order_id;

    select count(*) into v_remaining_count
    from public.order_items
    where order_id = v_order.order_id and order_item_inventory_deducted = false;

    if v_remaining_count = 0 and v_total_count > 0 then
        -- Takeout consumable deduction — only fires once per order, guarded
        -- by order_inventory_deducted, and only for takeout orders.
        if not v_order.order_inventory_deducted and v_order.order_type = 'takeout' then
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
            select
                count(*) filter (where kind = 'beverage'),
                count(*) filter (where kind = 'food')
            into v_beverage_lines, v_food_lines
            from order_classified;

            v_beverage_lines := coalesce(v_beverage_lines, 0);
            v_food_lines := coalesce(v_food_lines, 0);

            if v_beverage_lines > 0 then
                update public.ingredients
                set ingredient_current_stock = greatest(ingredient_current_stock - v_beverage_lines, 0)
                where ingredient_id = 'ING-201';
            end if;

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
        -- Some items still blocked — order stays in PREPARING. The caller
        -- sees fully_completed = false plus the blocked_items list.
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
