-- CHANGE: Staff Management — promotes the Staff tab from a local-only roster
-- (LocalAppPrefs.saveStaff) to a Supabase-backed CRUD table so multiple
-- devices share the same team list. Soft delete is the only deletion mode
-- (staff_is_active = false) so accidental removals are recoverable from
-- the "Show deactivated" filter in the app.
--
-- Run after add_paymongo_payment_support.sql in the Supabase SQL Editor.

-- ----------------------------------------------------------------------
-- 1. Schema: staff table
-- ----------------------------------------------------------------------
create table if not exists public.staff (
    staff_id           text primary key,
    staff_employee_id  text not null unique,
    staff_full_name    text not null,
    staff_role         text not null,
    staff_email        text,
    staff_phone        text,
    staff_status       text not null default 'active'
                       check (staff_status in ('active', 'on_break', 'off_duty')),
    staff_is_active    boolean not null default true,
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now()
);

-- ----------------------------------------------------------------------
-- 2. updated_at trigger so the column tracks the last edit automatically.
-- ----------------------------------------------------------------------
create or replace function public.staff_set_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists staff_set_updated_at on public.staff;
create trigger staff_set_updated_at
    before update on public.staff
    for each row
    execute function public.staff_set_updated_at();

-- ----------------------------------------------------------------------
-- 3. RLS policies — every authenticated app user can read the full
--    roster (including deactivated rows, so the restore filter works)
--    and create / update rows. Deletes are intentionally blocked: the
--    app soft-deletes by flipping staff_is_active = false.
-- ----------------------------------------------------------------------
alter table public.staff enable row level security;

drop policy if exists "staff_select_authenticated" on public.staff;
create policy "staff_select_authenticated"
    on public.staff
    for select
    to authenticated
    using (true);

drop policy if exists "staff_insert_authenticated" on public.staff;
create policy "staff_insert_authenticated"
    on public.staff
    for insert
    to authenticated
    with check (true);

drop policy if exists "staff_update_authenticated" on public.staff;
create policy "staff_update_authenticated"
    on public.staff
    for update
    to authenticated
    using (true)
    with check (true);

-- (intentionally no DELETE policy — use soft delete via staff_is_active)

-- ----------------------------------------------------------------------
-- 4. View: only active (non-soft-deleted) rows. Convenient for callers
--    that want the default roster without thinking about the flag.
-- ----------------------------------------------------------------------
create or replace view public.active_staff as
    select
        staff_id,
        staff_employee_id,
        staff_full_name,
        staff_role,
        staff_email,
        staff_phone,
        staff_status,
        staff_is_active,
        created_at,
        updated_at
    from public.staff
    where staff_is_active = true;

grant select on public.active_staff to authenticated;
