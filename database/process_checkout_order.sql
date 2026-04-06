-- Atomic checkout helpers for Zejios Cafe.
-- Run this in Supabase SQL Editor after the base schema/menu/recipe seed scripts.

create or replace function public.resolve_pos_order_number(
    p_requested_order_number text default null
)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
    v_requested_counter integer;
    v_highest_counter integer;
    v_target_counter integer;
begin
    v_requested_counter := nullif(
        substring(coalesce(p_requested_order_number, '') from '#POS-(\d+)$'),
        ''
    )::integer;

    select coalesce(
        max(nullif(substring(order_number from '#POS-(\d+)$'), '')::integer),
        1023
    )
    into v_highest_counter
    from public.orders;

    v_target_counter := greatest(
        coalesce(v_requested_counter, 1024),
        coalesce(v_highest_counter, 1023) + 1,
        1024
    );

    return '#POS-' || lpad(v_target_counter::text, 4, '0');
end;
$$;

create or replace function public.peek_next_pos_order_number()
returns table (
    next_order_number text
)
language plpgsql
security definer
set search_path = public
as $$
begin
    return query
    select public.resolve_pos_order_number(null);
end;
$$;

create or replace function public.process_checkout_order(
    p_customer_name text default null,
    p_table_label text default null,
    p_requested_order_number text default null,
    p_payment_method text default 'cash',
    p_status text default 'completed',
    p_tax numeric default 0,
    p_items jsonb default '[]'::jsonb
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
begin
    v_staff_user_id := auth.uid();

    if v_staff_user_id is null then
        raise exception 'Authenticated session required to process checkout.';
    end if;

    if coalesce(jsonb_typeof(p_items), '') <> 'array' or jsonb_array_length(coalesce(p_items, '[]'::jsonb)) = 0 then
        raise exception 'Checkout must contain at least one order item.';
    end if;

    with raw_lines as (
        select
            trim(coalesce(item->>'product_variant_id', '')) as product_variant_id,
            coalesce((item->>'quantity')::integer, 0) as quantity
        from jsonb_array_elements(p_items) item
    )
    select exists(
        select 1
        from raw_lines
        where product_variant_id = ''
           or quantity <= 0
    )
    into v_invalid_item_exists;

    if v_invalid_item_exists then
        raise exception 'Checkout contains invalid product variant or quantity values.';
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
            sum(raw_lines.quantity)::integer as quantity,
            pv.variant_track_inventory
        from raw_lines
        join public.product_variants pv on pv.product_variant_id = raw_lines.product_variant_id
        join public.products p on p.product_id = pv.product_id
        where pv.variant_is_active = true
          and p.product_is_active = true
        group by
            pv.product_variant_id,
            p.product_id,
            p.product_name,
            pv.variant_name,
            pv.variant_price,
            pv.variant_track_inventory
    )
    select exists(
        select 1
        from (
            select trim(coalesce(item->>'product_variant_id', '')) as product_variant_id
            from jsonb_array_elements(p_items) item
        ) raw_lines
        where not exists (
            select 1
            from order_lines
            where order_lines.product_variant_id = raw_lines.product_variant_id
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
            p.product_name,
            pv.variant_name,
            sum(raw_lines.quantity)::integer as quantity
        from raw_lines
        join public.product_variants pv on pv.product_variant_id = raw_lines.product_variant_id
        join public.products p on p.product_id = pv.product_id
        group by
            pv.product_variant_id,
            p.product_name,
            pv.variant_name,
            pv.variant_track_inventory
        having bool_or(pv.variant_track_inventory)
    )
    select format('%s (%s)', product_name, variant_name)
    into v_missing_recipe_variant
    from order_lines
    where not exists (
        select 1
        from public.variant_ingredients vi
        where vi.product_variant_id = order_lines.product_variant_id
    )
    limit 1;

    if v_missing_recipe_variant is not null then
        raise exception 'Missing recipe definition for %.', v_missing_recipe_variant;
    end if;

    perform 1
    from public.ingredients i
    where i.ingredient_id in (
        with raw_lines as (
            select
                trim(coalesce(item->>'product_variant_id', '')) as product_variant_id,
                coalesce((item->>'quantity')::integer, 0) as quantity
            from jsonb_array_elements(p_items) item
        ),
        tracked_lines as (
            select
                pv.product_variant_id,
                sum(raw_lines.quantity)::integer as quantity
            from raw_lines
            join public.product_variants pv on pv.product_variant_id = raw_lines.product_variant_id
            where pv.variant_track_inventory = true
            group by pv.product_variant_id
        )
        select vi.ingredient_id
        from tracked_lines
        join public.variant_ingredients vi on vi.product_variant_id = tracked_lines.product_variant_id
    )
    for update;

    with raw_lines as (
        select
            trim(coalesce(item->>'product_variant_id', '')) as product_variant_id,
            coalesce((item->>'quantity')::integer, 0) as quantity
        from jsonb_array_elements(p_items) item
    ),
    tracked_lines as (
        select
            pv.product_variant_id,
            sum(raw_lines.quantity)::integer as quantity
        from raw_lines
        join public.product_variants pv on pv.product_variant_id = raw_lines.product_variant_id
        where pv.variant_track_inventory = true
        group by pv.product_variant_id
    ),
    ingredient_usage as (
        select
            vi.ingredient_id,
            sum(vi.required_quantity * tracked_lines.quantity)::numeric(12,2) as total_required
        from tracked_lines
        join public.variant_ingredients vi on vi.product_variant_id = tracked_lines.product_variant_id
        group by vi.ingredient_id
    )
    select format(
        '%s needs %s %s, but only %s %s remain.',
        i.ingredient_name,
        ingredient_usage.total_required,
        i.ingredient_unit,
        i.ingredient_current_stock,
        i.ingredient_unit
    )
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
        with raw_lines as (
            select
                trim(coalesce(item->>'product_variant_id', '')) as product_variant_id,
                coalesce((item->>'quantity')::integer, 0) as quantity
            from jsonb_array_elements(p_items) item
        )
        select pv.product_variant_id
        from raw_lines
        join public.product_variants pv on pv.product_variant_id = raw_lines.product_variant_id
        where pv.variant_track_inventory = false
    )
    for update;

    with raw_lines as (
        select
            trim(coalesce(item->>'product_variant_id', '')) as product_variant_id,
            coalesce((item->>'quantity')::integer, 0) as quantity
        from jsonb_array_elements(p_items) item
    ),
    manual_usage as (
        select
            pv.product_variant_id,
            p.product_name,
            pv.variant_name,
            sum(raw_lines.quantity)::integer as total_quantity,
            pv.variant_manual_stock_left
        from raw_lines
        join public.product_variants pv on pv.product_variant_id = raw_lines.product_variant_id
        join public.products p on p.product_id = pv.product_id
        where pv.variant_track_inventory = false
        group by
            pv.product_variant_id,
            p.product_name,
            pv.variant_name,
            pv.variant_manual_stock_left
    )
    select format(
        '%s (%s) only has %s left.',
        product_name,
        variant_name,
        variant_manual_stock_left
    )
    into v_insufficient_manual_variant
    from manual_usage
    where variant_manual_stock_left < total_quantity
    order by product_name, variant_name
    limit 1;

    if v_insufficient_manual_variant is not null then
        raise exception '%', v_insufficient_manual_variant;
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
        order_table_label,
        order_subtotal,
        order_tax,
        order_total,
        order_payment_method,
        order_status
    )
    values (
        v_staff_user_id,
        v_order_number,
        nullif(trim(coalesce(p_customer_name, '')), ''),
        nullif(trim(coalesce(p_table_label, '')), ''),
        v_subtotal,
        v_tax,
        v_total,
        lower(trim(coalesce(p_payment_method, 'cash'))),
        lower(trim(coalesce(p_status, 'completed')))
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

    with raw_lines as (
        select
            trim(coalesce(item->>'product_variant_id', '')) as product_variant_id,
            coalesce((item->>'quantity')::integer, 0) as quantity
        from jsonb_array_elements(p_items) item
    ),
    tracked_lines as (
        select
            pv.product_variant_id,
            sum(raw_lines.quantity)::integer as quantity
        from raw_lines
        join public.product_variants pv on pv.product_variant_id = raw_lines.product_variant_id
        where pv.variant_track_inventory = true
        group by pv.product_variant_id
    ),
    ingredient_usage as (
        select
            vi.ingredient_id,
            sum(vi.required_quantity * tracked_lines.quantity)::numeric(12,2) as total_required
        from tracked_lines
        join public.variant_ingredients vi on vi.product_variant_id = tracked_lines.product_variant_id
        group by vi.ingredient_id
    )
    update public.ingredients i
    set ingredient_current_stock = round((i.ingredient_current_stock - ingredient_usage.total_required)::numeric, 2)
    from ingredient_usage
    where ingredient_usage.ingredient_id = i.ingredient_id;

    with raw_lines as (
        select
            trim(coalesce(item->>'product_variant_id', '')) as product_variant_id,
            coalesce((item->>'quantity')::integer, 0) as quantity
        from jsonb_array_elements(p_items) item
    ),
    manual_usage as (
        select
            pv.product_variant_id,
            sum(raw_lines.quantity)::integer as total_quantity
        from raw_lines
        join public.product_variants pv on pv.product_variant_id = raw_lines.product_variant_id
        where pv.variant_track_inventory = false
        group by pv.product_variant_id
    )
    update public.product_variants pv
    set variant_manual_stock_left = greatest(pv.variant_manual_stock_left - manual_usage.total_quantity, 0)
    from manual_usage
    where manual_usage.product_variant_id = pv.product_variant_id;

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

revoke all on function public.resolve_pos_order_number(text) from public;
revoke all on function public.peek_next_pos_order_number() from public;
revoke all on function public.process_checkout_order(text, text, text, text, text, numeric, jsonb) from public;

grant execute on function public.peek_next_pos_order_number() to authenticated;
grant execute on function public.process_checkout_order(text, text, text, text, text, numeric, jsonb) to authenticated;
