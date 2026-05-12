-- CHANGE: Takeout support — order_type column + RPC param, takeout
-- consumables seed, and consumable deduction on order completion.
-- Run after process_checkout_order.sql in Supabase SQL Editor.

-- ----------------------------------------------------------------------
-- 1. Schema change: orders.order_type
-- ----------------------------------------------------------------------
do $$
begin
    if not exists (
        select 1
        from information_schema.columns
        where table_schema = 'public'
          and table_name = 'orders'
          and column_name = 'order_type'
    ) then
        execute 'alter table public.orders add column order_type text not null default ''dine_in''';
        execute 'update public.orders set order_type = ''dine_in'' where order_type is null';
    end if;
end;
$$;

-- ----------------------------------------------------------------------
-- 2. Seed: 3 takeout consumables in the ingredients table
--    These follow the same piece-based rules as other countable items.
--    They are NOT used by any recipe; complete_order() debits them
--    directly when an order is marked takeout.
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
    ('ING-201', 'Plastic Cup with Cover', 'pcs', 200, 30, 5.00, 'Takeout Supplies'),
    ('ING-202', 'Food Styrofoam',         'pcs', 200, 30, 7.00, 'Takeout Supplies'),
    ('ING-203', 'Plastic Spoon & Fork',   'pcs', 250, 30, 2.00, 'Takeout Supplies')
on conflict (ingredient_id) do update set
    ingredient_name = excluded.ingredient_name,
    ingredient_unit = excluded.ingredient_unit,
    ingredient_minimum_stock = excluded.ingredient_minimum_stock,
    ingredient_cost_per_unit = excluded.ingredient_cost_per_unit,
    ingredient_category = excluded.ingredient_category;

-- ----------------------------------------------------------------------
-- 3. Helper: classify a product category as 'beverage' or 'food'.
--    Pure keyword match — single source of truth for backend logic.
-- ----------------------------------------------------------------------
create or replace function public.classify_product_category(p_category_name text)
returns text
language sql
immutable
as $$
    select case
        when p_category_name is null then 'food'
        when lower(p_category_name) similar to
             '%(beverage|drink|coffee|tea|smoothie|frappe|milk\s*tea|juice|shake|latte|espresso|americano|cappuccino|chocolate)%'
            then 'beverage'
        else 'food'
    end;
$$;

-- ----------------------------------------------------------------------
-- 4. Replace process_checkout_order with a version that accepts and
--    persists p_order_type. Existing param order is preserved; the new
--    p_order_type is appended at the end with a 'dine_in' default so
--    older clients keep working.
-- ----------------------------------------------------------------------
drop function if exists public.process_checkout_order(text, text, text, text, numeric, jsonb);
drop function if exists public.process_checkout_order(text, text, text, text, numeric, jsonb, text);

create or replace function public.process_checkout_order(
    p_customer_name text default null,
    p_requested_order_number text default null,
    p_payment_method text default 'cash',
    p_status text default 'preparing',
    p_tax numeric default 0,
    p_items jsonb default '[]'::jsonb,
    p_order_type text default 'dine_in'
)
returns table (
    order_id uuid,
    order_number text,
    created_at timestamptz,
    order_subtotal numeric,
    order_tax numeric,
    order_total numeric
)
language plpgsql
security definer
set search_path = public
as $$
declare
    v_staff_user_id uuid;
    v_order_id uuid;
    v_order_number text;
    v_created_at timestamptz;
    v_subtotal numeric(10,2);
    v_tax numeric(10,2);
    v_total numeric(10,2);
    v_invalid_item_exists boolean;
    v_unresolved_line_exists boolean;
    v_missing_recipe_variant text;
    v_insufficient_ingredient text;
    v_insufficient_manual_variant text;
    v_normalized_order_type text;
begin
    v_staff_user_id := auth.uid();

    if v_staff_user_id is null then
        raise exception 'Authenticated session required to process checkout.';
    end if;

    if coalesce(jsonb_typeof(p_items), '') <> 'array' or jsonb_array_length(coalesce(p_items, '[]'::jsonb)) = 0 then
        raise exception 'Checkout must contain at least one order item.';
    end if;

    v_normalized_order_type := lower(trim(coalesce(p_order_type, 'dine_in')));
    if v_normalized_order_type not in ('dine_in', 'takeout', 'delivery') then
        v_normalized_order_type := 'dine_in';
    end if;

    -- (validation blocks unchanged from process_checkout_order.sql)
    with raw_lines as (
        select
            trim(coalesce(item->>'product_variant_id', '')) as product_variant_id,
            coalesce((item->>'quantity')::integer, 0) as quantity
        from jsonb_array_elements(p_items) item
    )
    select exists(
        select 1 from raw_lines where product_variant_id = '' or quantity <= 0
    ) into v_invalid_item_exists;

    if v_invalid_item_exists then
        raise exception 'Checkout contains invalid product variant or quantity values.';
    end if;

    with raw_lines as (
        select trim(coalesce(item->>'product_variant_id', '')) as product_variant_id
        from jsonb_array_elements(p_items) item
    )
    select exists(
        select 1 from raw_lines
        where not exists (
            select 1
            from public.product_variants pv
            join public.products p on p.product_id = pv.product_id
            where pv.product_variant_id = raw_lines.product_variant_id
              and pv.variant_is_active = true
              and p.product_is_active = true
        )
    )
    into v_unresolved_line_exists;

    if v_unresolved_line_exists then
        raise exception 'One or more checkout items are unavailable or invalid.';
    end if;

    with raw_lines as (
        select
            trim(coalesce(item->>'product_variant_id', '')) as product_variant_id,
            coalesce((item->>'quantity')::integer, 0) as quantity
        from jsonb_array_elements(p_items) item
    ),
    order_lines as (
        select
            pv.product_variant_id,
            p.product_id,
            p.product_name,
            pv.variant_name,
            pv.variant_price::numeric(10,2) as unit_price,
            sum(raw_lines.quantity)::integer as quantity
        from raw_lines
        join public.product_variants pv on pv.product_variant_id = raw_lines.product_variant_id
        join public.products p on p.product_id = pv.product_id
        group by
            pv.product_variant_id,
            p.product_id,
            p.product_name,
            pv.variant_name,
            pv.variant_price
    )
    select
        round(sum(unit_price * quantity)::numeric, 2),
        round(greatest(coalesce(p_tax, 0), 0)::numeric, 2)
    into v_subtotal, v_tax
    from order_lines;

    v_total := round((coalesce(v_subtotal, 0) + coalesce(v_tax, 0))::numeric, 2);
    v_order_number := public.resolve_pos_order_number(p_requested_order_number);

    insert into public.orders (
        staff_user_id,
        order_number,
        order_customer_name,
        order_subtotal,
        order_tax,
        order_total,
        order_payment_method,
        order_status,
        order_type,
        order_completed_at,
        order_inventory_deducted
    )
    values (
        v_staff_user_id,
        v_order_number,
        nullif(trim(coalesce(p_customer_name, '')), ''),
        v_subtotal,
        v_tax,
        v_total,
        lower(trim(coalesce(p_payment_method, 'cash'))),
        lower(trim(coalesce(p_status, 'preparing'))),
        v_normalized_order_type,
        case
            when lower(trim(coalesce(p_status, 'preparing'))) = 'completed' then now()
            else null
        end,
        false
    )
    returning
        orders.order_id,
        orders.created_at
    into
        v_order_id,
        v_created_at;

    with raw_lines as (
        select
            trim(coalesce(item->>'product_variant_id', '')) as product_variant_id,
            coalesce((item->>'quantity')::integer, 0) as quantity
        from jsonb_array_elements(p_items) item
    ),
    order_lines as (
        select
            pv.product_variant_id,
            p.product_id,
            p.product_name,
            pv.variant_name,
            pv.variant_price::numeric(10,2) as unit_price,
            sum(raw_lines.quantity)::integer as quantity
        from raw_lines
        join public.product_variants pv on pv.product_variant_id = raw_lines.product_variant_id
        join public.products p on p.product_id = pv.product_id
        group by
            pv.product_variant_id,
            p.product_id,
            p.product_name,
            pv.variant_name,
            pv.variant_price
    )
    insert into public.order_items (
        order_id,
        product_id,
        product_variant_id,
        order_item_product_name,
        order_item_variant_name,
        order_item_unit_price,
        order_item_quantity,
        order_item_line_total
    )
    select
        v_order_id,
        order_lines.product_id,
        order_lines.product_variant_id,
        order_lines.product_name,
        order_lines.variant_name,
        order_lines.unit_price,
        order_lines.quantity,
        round((order_lines.unit_price * order_lines.quantity)::numeric, 2)
    from order_lines;

    return query
    select
        v_order_id,
        v_order_number,
        v_created_at,
        v_subtotal,
        v_tax,
        v_total;
end;
$$;

-- ----------------------------------------------------------------------
-- 5. Replace complete_order. The recipe-deduction logic is preserved
--    verbatim. New section: when order_type = 'takeout', additionally
--    deduct takeout consumables.
--      * 1 Plastic Cup with Cover  per beverage line in the order
--      * 1 Food Styrofoam          per food line in the order
--      * 1 Plastic Spoon & Fork    per food line in the order
--    Granularity = "per food line" (one container per distinct dish),
--    matching the locked product decision. Dine-in orders skip this.
--    Stock can go to zero but never below — staff are warned client-side
--    before confirming if any consumable is empty.
-- ----------------------------------------------------------------------
create or replace function public.complete_order(
    p_order_number text
)
returns table (
    completed_order_id uuid,
    completed_order_number text,
    completed_order_status text,
    completed_order_inventory_deducted boolean
)
language plpgsql
security definer
set search_path = public
as $$
declare
    v_order record;
    v_missing_recipe_variant text;
    v_insufficient_ingredient text;
    v_insufficient_manual_variant text;
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

    if not v_order.order_inventory_deducted then
        if not exists (select 1 from public.order_items oi where oi.order_id = v_order.order_id) then
            raise exception 'Order % has no items to complete.', v_order.order_number;
        end if;

        -- recipe ingredient stock check + deduct (unchanged from prior version)
        with tracked_lines as (
            select
                oi.product_variant_id,
                oi.order_item_product_name as product_name,
                oi.order_item_variant_name as variant_name,
                sum(oi.order_item_quantity)::integer as quantity
            from public.order_items oi
            join public.product_variants pv on pv.product_variant_id = oi.product_variant_id
            where oi.order_id = v_order.order_id
              and pv.variant_track_inventory = true
            group by
                oi.product_variant_id,
                oi.order_item_product_name,
                oi.order_item_variant_name
        )
        select format('%s (%s)', product_name, variant_name)
        into v_missing_recipe_variant
        from tracked_lines
        where not exists (
            select 1 from public.variant_ingredients vi
            where vi.product_variant_id = tracked_lines.product_variant_id
        )
        limit 1;

        if v_missing_recipe_variant is not null then
            raise exception 'Missing recipe definition for %.', v_missing_recipe_variant;
        end if;

        perform 1
        from public.ingredients i
        where i.ingredient_id in (
            select vi.ingredient_id
            from public.order_items oi
            join public.product_variants pv on pv.product_variant_id = oi.product_variant_id
            join public.variant_ingredients vi on vi.product_variant_id = oi.product_variant_id
            where oi.order_id = v_order.order_id
              and pv.variant_track_inventory = true
        )
        for update;

        with tracked_lines as (
            select oi.product_variant_id, sum(oi.order_item_quantity)::integer as quantity
            from public.order_items oi
            join public.product_variants pv on pv.product_variant_id = oi.product_variant_id
            where oi.order_id = v_order.order_id and pv.variant_track_inventory = true
            group by oi.product_variant_id
        ),
        ingredient_usage as (
            select vi.ingredient_id,
                   sum(vi.required_quantity * tracked_lines.quantity)::numeric(12,2) as total_required
            from tracked_lines
            join public.variant_ingredients vi on vi.product_variant_id = tracked_lines.product_variant_id
            group by vi.ingredient_id
        )
        select format('%s needs %s %s, but only %s %s remain.',
                      i.ingredient_name, ingredient_usage.total_required, i.ingredient_unit,
                      i.ingredient_current_stock, i.ingredient_unit)
        into v_insufficient_ingredient
        from ingredient_usage
        join public.ingredients i on i.ingredient_id = ingredient_usage.ingredient_id
        where i.ingredient_current_stock < ingredient_usage.total_required
        order by i.ingredient_name
        limit 1;

        if v_insufficient_ingredient is not null then
            raise exception '%', v_insufficient_ingredient;
        end if;

        perform 1
        from public.product_variants pv
        where pv.product_variant_id in (
            select oi.product_variant_id
            from public.order_items oi
            join public.product_variants item_variant on item_variant.product_variant_id = oi.product_variant_id
            where oi.order_id = v_order.order_id and item_variant.variant_track_inventory = false
        )
        for update;

        with manual_usage as (
            select pv.product_variant_id, p.product_name, pv.variant_name,
                   sum(oi.order_item_quantity)::integer as total_quantity,
                   pv.variant_manual_stock_left
            from public.order_items oi
            join public.product_variants pv on pv.product_variant_id = oi.product_variant_id
            join public.products p on p.product_id = pv.product_id
            where oi.order_id = v_order.order_id and pv.variant_track_inventory = false
            group by pv.product_variant_id, p.product_name, pv.variant_name, pv.variant_manual_stock_left
        )
        select format('%s (%s) only has %s left.', product_name, variant_name, variant_manual_stock_left)
        into v_insufficient_manual_variant
        from manual_usage
        where variant_manual_stock_left < total_quantity
        order by product_name, variant_name
        limit 1;

        if v_insufficient_manual_variant is not null then
            raise exception '%', v_insufficient_manual_variant;
        end if;

        with tracked_lines as (
            select oi.product_variant_id, sum(oi.order_item_quantity)::integer as quantity
            from public.order_items oi
            join public.product_variants pv on pv.product_variant_id = oi.product_variant_id
            where oi.order_id = v_order.order_id and pv.variant_track_inventory = true
            group by oi.product_variant_id
        ),
        ingredient_usage as (
            select vi.ingredient_id,
                   sum(vi.required_quantity * tracked_lines.quantity)::numeric(12,2) as total_required
            from tracked_lines
            join public.variant_ingredients vi on vi.product_variant_id = tracked_lines.product_variant_id
            group by vi.ingredient_id
        )
        update public.ingredients i
        set ingredient_current_stock = round((i.ingredient_current_stock - ingredient_usage.total_required)::numeric, 2)
        from ingredient_usage
        where ingredient_usage.ingredient_id = i.ingredient_id;

        with manual_usage as (
            select pv.product_variant_id, sum(oi.order_item_quantity)::integer as total_quantity
            from public.order_items oi
            join public.product_variants pv on pv.product_variant_id = oi.product_variant_id
            where oi.order_id = v_order.order_id and pv.variant_track_inventory = false
            group by pv.product_variant_id
        )
        update public.product_variants pv
        set variant_manual_stock_left = greatest(pv.variant_manual_stock_left - manual_usage.total_quantity, 0)
        from manual_usage
        where manual_usage.product_variant_id = pv.product_variant_id;

        -- ----------------------------------------------------------------
        -- CHANGE: Takeout consumable deduction — only fires for takeout
        -- orders, never for dine-in. Counts DISTINCT lines (product
        -- variants) per category, not per quantity.
        -- ----------------------------------------------------------------
        if v_order.order_type = 'takeout' then
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
    end if;

    update public.order_items
    set order_item_is_completed = true
    where order_id = v_order.order_id;

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

    return next;
end;
$$;

-- ----------------------------------------------------------------------
-- 6. Re-grant execute. Old signature was already revoked in
--    process_checkout_order.sql; re-grant the new signature so callers
--    can continue invoking via PostgREST.
-- ----------------------------------------------------------------------
revoke all on function public.process_checkout_order(text, text, text, text, numeric, jsonb, text) from public;
revoke all on function public.complete_order(text) from public;

grant execute on function public.process_checkout_order(text, text, text, text, numeric, jsonb, text) to authenticated;
grant execute on function public.complete_order(text) to authenticated;
