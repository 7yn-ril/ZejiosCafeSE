-- CHANGE: Staff Personal Details — extends the staff table with basic
-- personal info (address, birthdate, gender) and an emergency contact
-- block (name, phone, relationship). All columns are nullable so existing
-- rows stay valid without backfill, and so partial onboarding is allowed.
--
-- Run after add_staff_management.sql in the Supabase SQL Editor.

-- ----------------------------------------------------------------------
-- 1. Schema additions — idempotent via if not exists.
-- ----------------------------------------------------------------------
do $$
begin
    if not exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'staff'
          and column_name = 'staff_address'
    ) then
        execute 'alter table public.staff add column staff_address text';
    end if;

    if not exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'staff'
          and column_name = 'staff_birthdate'
    ) then
        execute 'alter table public.staff add column staff_birthdate date';
    end if;

    if not exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'staff'
          and column_name = 'staff_gender'
    ) then
        execute 'alter table public.staff add column staff_gender text';
    end if;

    if not exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'staff'
          and column_name = 'staff_emergency_contact_name'
    ) then
        execute 'alter table public.staff add column staff_emergency_contact_name text';
    end if;

    if not exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'staff'
          and column_name = 'staff_emergency_contact_phone'
    ) then
        execute 'alter table public.staff add column staff_emergency_contact_phone text';
    end if;

    if not exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'staff'
          and column_name = 'staff_emergency_contact_relationship'
    ) then
        execute 'alter table public.staff add column staff_emergency_contact_relationship text';
    end if;
end;
$$;

-- ----------------------------------------------------------------------
-- 2. Refresh the active_staff view so callers can select the new fields
--    without re-querying the base table. (RLS policies still apply via
--    the underlying table; no separate policies needed.)
--
--    Note: PostgreSQL's CREATE OR REPLACE VIEW only allows APPENDING new
--    columns at the end — reordering existing columns raises
--    "cannot change name of view column". So the personal-details columns
--    must come AFTER created_at / updated_at, even though they read more
--    naturally next to the other staff_ columns.
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
        updated_at,
        staff_address,
        staff_birthdate,
        staff_gender,
        staff_emergency_contact_name,
        staff_emergency_contact_phone,
        staff_emergency_contact_relationship
    from public.staff
    where staff_is_active = true;

grant select on public.active_staff to authenticated;
