-- CHANGE: Discounts — replaces the hard-coded 10/20/50 percent presets in
-- the checkout dialog with a discounts table. Built-in rows (PWD, Senior)
-- ship with the schema and cannot be deleted; managers can add custom
-- date-bounded promotions via the POS "Other" modal. Each order snapshots
-- the percent applied at checkout so historical reports stay accurate
-- even if the manager later edits the discount.
--
-- Run after add_takeout_support.sql in the Supabase SQL Editor.

-- ----------------------------------------------------------------------
-- 1. Schema change: discounts table
-- ----------------------------------------------------------------------
create table if not exists public.discounts (
    discount_id          text primary key,
    discount_name        text not null,
    discount_percent     numeric(5,2) not null check (discount_percent >= 0 and discount_percent <= 100),
    discount_start_date  date,
    discount_end_date    date,
    discount_is_built_in boolean not null default false,
    discount_is_active   boolean not null default true,
    created_at           timestamptz not null default now(),
    check (
        discount_start_date is null
        or discount_end_date is null
        or discount_start_date <= discount_end_date
    )
);

-- ----------------------------------------------------------------------
-- 2. Seed: PWD and Senior Citizen built-in discounts. Both are 20% per
--    Philippine RA 9994 / RA 10754. is_built_in = true protects them
--    from accidental deletion in the manager UI.
-- ----------------------------------------------------------------------
insert into public.discounts (
    discount_id,
    discount_name,
    discount_percent,
    discount_start_date,
    discount_end_date,
    discount_is_built_in,
    discount_is_active
) values
    ('DSC-PWD',    'PWD',            20.00, null, null, true, true),
    ('DSC-SENIOR', 'Senior Citizen', 20.00, null, null, true, true)
on conflict (discount_id) do update set
    discount_name        = excluded.discount_name,
    discount_percent     = excluded.discount_percent,
    discount_is_built_in = excluded.discount_is_built_in,
    discount_is_active   = excluded.discount_is_active;

-- ----------------------------------------------------------------------
-- 3. Schema change: orders table — add discount tracking columns.
--    discount_percent is a snapshot of the rate at checkout time so the
--    line stays correct even after the manager changes the discount row.
-- ----------------------------------------------------------------------
do $$
begin
    if not exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'orders'
          and column_name = 'order_discount_id'
    ) then
        execute 'alter table public.orders add column order_discount_id text references public.discounts(discount_id)';
    end if;

    if not exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'orders'
          and column_name = 'order_discount_label'
    ) then
        execute 'alter table public.orders add column order_discount_label text';
    end if;

    if not exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'orders'
          and column_name = 'order_discount_percent'
    ) then
        execute 'alter table public.orders add column order_discount_percent numeric(5,2)';
    end if;

    if not exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'orders'
          and column_name = 'order_discount_amount'
    ) then
        execute 'alter table public.orders add column order_discount_amount numeric(10,2) not null default 0';
    end if;
end;
$$;

-- ----------------------------------------------------------------------
-- 4. RLS policies — every authenticated staff member can read the active
--    discounts list (so the POS can render the dropdown) and create new
--    rows (the manager PIN gate lives in the app, not the database). The
--    built-in rows are protected from delete + rename via WITH CHECK.
-- ----------------------------------------------------------------------
alter table public.discounts enable row level security;

drop policy if exists "discounts_select_authenticated" on public.discounts;
create policy "discounts_select_authenticated"
    on public.discounts
    for select
    to authenticated
    using (true);

drop policy if exists "discounts_insert_authenticated" on public.discounts;
create policy "discounts_insert_authenticated"
    on public.discounts
    for insert
    to authenticated
    with check (discount_is_built_in = false);

drop policy if exists "discounts_update_authenticated" on public.discounts;
create policy "discounts_update_authenticated"
    on public.discounts
    for update
    to authenticated
    using (discount_is_built_in = false)
    with check (discount_is_built_in = false);

-- (intentionally no DELETE policy — managers deactivate instead of delete)

-- ----------------------------------------------------------------------
-- 5. Helper view: active discounts for a given date. The POS calls this
--    by passing today's date as a filter; the dropdown only shows rows
--    whose schedule covers today.
-- ----------------------------------------------------------------------
create or replace view public.active_discounts as
    select
        discount_id,
        discount_name,
        discount_percent,
        discount_start_date,
        discount_end_date,
        discount_is_built_in
    from public.discounts
    where discount_is_active = true;

grant select on public.active_discounts to authenticated;
