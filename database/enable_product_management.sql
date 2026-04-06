drop policy if exists "insert_products_authenticated" on public.products;

create policy "insert_products_authenticated"
on public.products
for insert
to authenticated
with check (true);

drop policy if exists "update_products_authenticated" on public.products;

create policy "update_products_authenticated"
on public.products
for update
to authenticated
using (true)
with check (true);

drop policy if exists "insert_product_variants_authenticated" on public.product_variants;

create policy "insert_product_variants_authenticated"
on public.product_variants
for insert
to authenticated
with check (true);

drop policy if exists "update_product_variants_authenticated" on public.product_variants;

create policy "update_product_variants_authenticated"
on public.product_variants
for update
to authenticated
using (true)
with check (true);

drop policy if exists "read_variant_ingredients_authenticated" on public.variant_ingredients;
drop policy if exists "read_variant_ingredients_public" on public.variant_ingredients;

create policy "read_variant_ingredients_public"
on public.variant_ingredients
for select
to anon, authenticated
using (true);

drop policy if exists "insert_variant_ingredients_authenticated" on public.variant_ingredients;

create policy "insert_variant_ingredients_authenticated"
on public.variant_ingredients
for insert
to authenticated
with check (true);

drop policy if exists "update_variant_ingredients_authenticated" on public.variant_ingredients;

create policy "update_variant_ingredients_authenticated"
on public.variant_ingredients
for update
to authenticated
using (true)
with check (true);

drop policy if exists "delete_variant_ingredients_authenticated" on public.variant_ingredients;

create policy "delete_variant_ingredients_authenticated"
on public.variant_ingredients
for delete
to authenticated
using (true);
