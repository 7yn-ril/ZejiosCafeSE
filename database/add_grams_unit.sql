-- Re-tags ingredients that were stored as `mL` but are physically
-- measured in grams (powders, pastes, dry bulk, frozen-by-weight items).
-- Before this migration the app's IngredientUnits.normalize() silently
-- collapsed grams → mL so staff couldn't pick grams from the unit
-- dropdown; the UI then mislabeled every powder/paste as "mL".
--
-- The numeric values (current_stock, minimum_stock, ml_per_serving,
-- ml_per_bottle, cost_per_unit) are NOT changed — they were already
-- being treated as grams under an "mL" label. We're only relabeling
-- the unit text so the dropdown, recipe Qty hint, and ingredient list
-- display the correct unit.
--
-- Idempotent. Safe to re-run. Apply via Supabase SQL Editor after
-- split_ingredient_categories.sql.

update public.ingredients
set ingredient_unit = case ingredient_id
    when 'ING-017' then 'g'  -- Biscoff Spread
    when 'ING-019' then 'g'  -- Taro Powder
    when 'ING-021' then 'g'  -- Chocolate Powder
    when 'ING-024' then 'g'  -- Matcha Powder
    when 'ING-025' then 'g'  -- Cheesecake Mix
    when 'ING-026' then 'g'  -- Oreo Crumbs
    when 'ING-028' then 'g'  -- Chocolate Chips
    when 'ING-036' then 'g'  -- Peanut Butter
    when 'ING-037' then 'g'  -- Tapioca Pearl
    when 'ING-038' then 'g'  -- Nata Jelly
    when 'ING-039' then 'g'  -- Milk Tea Creamer
    when 'ING-045' then 'g'  -- Lettuce (was incorrectly 'mL')
    when 'ING-052' then 'g'  -- Fries
    when 'ING-053' then 'g'  -- Potato Mojos
    when 'ING-054' then 'g'  -- Nacho Chips
    when 'ING-055' then 'g'  -- Ground Beef
    when 'ING-058' then 'g'  -- Calamari
    when 'ING-064' then 'g'  -- Fettuccine Pasta
    when 'ING-070' then 'g'  -- Chicken Poppers
    when 'ING-071' then 'g'  -- Pork Sisig
    else ingredient_unit
end
where ingredient_id in (
    'ING-017', 'ING-019', 'ING-021', 'ING-024', 'ING-025', 'ING-026',
    'ING-028', 'ING-036', 'ING-037', 'ING-038', 'ING-039', 'ING-045',
    'ING-052', 'ING-053', 'ING-054', 'ING-055', 'ING-058', 'ING-064',
    'ING-070', 'ING-071'
);

-- After running, audit with:
--   select ingredient_unit, count(*) from public.ingredients
--   group by ingredient_unit order by ingredient_unit;
-- You should see something like:  g 20  |  mL ~40  |  pcs ~14
