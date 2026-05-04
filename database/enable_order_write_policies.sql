drop policy if exists "update_orders_authenticated" on public.orders;

create policy "update_orders_authenticated"
on public.orders
for update
to authenticated
using (true)
with check (true);

drop policy if exists "delete_orders_authenticated" on public.orders;

create policy "delete_orders_authenticated"
on public.orders
for delete
to authenticated
using (true);

drop policy if exists "delete_order_items_authenticated" on public.order_items;

create policy "delete_order_items_authenticated"
on public.order_items
for delete
to authenticated
using (true);

alter table public.order_items
    add column if not exists order_item_is_completed boolean not null default false;

drop policy if exists "update_order_items_authenticated" on public.order_items;

create policy "update_order_items_authenticated"
on public.order_items
for update
to authenticated
using (true)
with check (true);
