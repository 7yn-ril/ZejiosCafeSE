drop policy if exists "read_own_orders" on public.orders;

create policy "read_all_orders_for_authenticated_users"
on public.orders
for select
to authenticated
using (true);

drop policy if exists "read_own_order_items" on public.order_items;

create policy "read_all_order_items_for_authenticated_users"
on public.order_items
for select
to authenticated
using (true);
