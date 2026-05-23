-- Splits the overly-broad ingredient categories that the Add/Edit Product
-- spinner was using to filter ingredients per product category. Old setup
-- mixed sweet drink syrups with savoury food sauces, burger proteins with
-- rice-meal proteins, drink fruits with burger produce, etc.
--
-- After this migration:
--
--   Coffee & Tea Bases (4 items)  → Coffee Bases / Tea Bases / Lemonade Bases
--   Syrups & Sauces  (28 items)   → Sweet Syrups / Savoury Sauces
--   Pantry           (3 items)    → Drink Pantry / Food Pantry
--   Produce          (4 items)    → Drink Produce / Food Produce
--   Proteins         (13 items)   → Burger / Wing / Rice Meal / Side Proteins
--   Dairy            (9 items)    → trimmed; Cheese Slice → Food Dairy,
--                                            Egg → Rice Meal Proteins
--   Frozen & Sides   (4 items)    → trimmed; Ice → Drink Pantry
--   Beverages        (3 items)    → trimmed; Water → Drink Pantry
--
-- Idempotent. Safe to re-run. Apply via Supabase SQL Editor after
-- add_ingredient_category.sql.

update public.ingredients
set ingredient_category = case ingredient_id
    -- ── Drinks: bases ─────────────────────────────────────────────────
    when 'ING-001' then 'Coffee Bases'        -- Espresso Shot
    when 'ING-002' then 'Tea Bases'           -- Brewed Tea Base
    when 'ING-077' then 'Tea Bases'           -- Iced Tea Base
    when 'ING-029' then 'Lemonade Bases'      -- Lemonade Base

    -- ── Drinks: sweet syrups (incl. coffee-house "sauces") ────────────
    when 'ING-010' then 'Sweet Syrups'        -- Vanilla Syrup
    when 'ING-011' then 'Sweet Syrups'        -- Caramel Syrup
    when 'ING-012' then 'Sweet Syrups'        -- Peppermint Syrup
    when 'ING-013' then 'Sweet Syrups'        -- White Mocha Sauce (sweet)
    when 'ING-014' then 'Sweet Syrups'        -- Dark Mocha Sauce (sweet)
    when 'ING-015' then 'Sweet Syrups'        -- Salted Caramel Sauce (sweet)
    when 'ING-016' then 'Sweet Syrups'        -- Roasted Almond Syrup
    when 'ING-018' then 'Sweet Syrups'        -- Coconut Syrup
    when 'ING-020' then 'Sweet Syrups'        -- Okinawa Syrup
    when 'ING-022' then 'Sweet Syrups'        -- Wintermelon Syrup
    when 'ING-023' then 'Sweet Syrups'        -- Brown Sugar Syrup
    when 'ING-030' then 'Sweet Syrups'        -- Lychee Syrup
    when 'ING-031' then 'Sweet Syrups'        -- Strawberry Syrup
    when 'ING-032' then 'Sweet Syrups'        -- Blueberry Syrup
    when 'ING-033' then 'Sweet Syrups'        -- Green Apple Syrup
    when 'ING-034' then 'Sweet Syrups'        -- Raspberry Syrup
    when 'ING-076' then 'Sweet Syrups'        -- Cucumber Syrup
    when 'ING-078' then 'Sweet Syrups'        -- Four Seasons Syrup
    when 'ING-080' then 'Sweet Syrups'        -- Assorted Syrup Portion

    -- ── Drinks: misc (produce / pantry-style ingredients) ─────────────
    when 'ING-035' then 'Drink Produce'       -- Banana
    when 'ING-036' then 'Drink Pantry'        -- Peanut Butter
    when 'ING-040' then 'Drink Pantry'        -- Ice (was Frozen & Sides)
    when 'ING-082' then 'Drink Pantry'        -- Water (was Beverages)

    -- ── Food: proteins (split by primary product domain) ──────────────
    when 'ING-042' then 'Burger Proteins'     -- Beef Patty
    when 'ING-043' then 'Burger Proteins'     -- Chicken Fillet
    when 'ING-048' then 'Burger Proteins'     -- Bacon Slice
    when 'ING-060' then 'Wing Proteins'       -- Chicken Wings
    when 'ING-066' then 'Rice Meal Proteins'  -- Longganisa
    when 'ING-067' then 'Rice Meal Proteins'  -- Hungarian Sausage
    when 'ING-069' then 'Rice Meal Proteins'  -- Egg (was Dairy)
    when 'ING-070' then 'Rice Meal Proteins'  -- Chicken Poppers
    when 'ING-071' then 'Rice Meal Proteins'  -- Pork Sisig
    when 'ING-072' then 'Rice Meal Proteins'  -- Fish Fillet
    when 'ING-055' then 'Side Proteins'       -- Ground Beef
    when 'ING-058' then 'Side Proteins'       -- Calamari
    when 'ING-059' then 'Side Proteins'       -- Chicken Fingers
    when 'ING-074' then 'Side Proteins'       -- Pork Siomai

    -- ── Food: produce / dairy / pantry ────────────────────────────────
    when 'ING-044' then 'Food Produce'        -- Pineapple Slice
    when 'ING-045' then 'Food Produce'        -- Lettuce
    when 'ING-046' then 'Food Produce'        -- Tomato Slice
    when 'ING-047' then 'Food Dairy'          -- Cheese Slice (was Dairy)
    when 'ING-064' then 'Food Pantry'         -- Fettuccine Pasta
    when 'ING-068' then 'Food Pantry'         -- Rice Serving

    -- ── Food: savoury sauces ──────────────────────────────────────────
    when 'ING-049' then 'Savoury Sauces'      -- Mushroom Sauce
    when 'ING-050' then 'Savoury Sauces'      -- Mayo Sauce
    when 'ING-051' then 'Savoury Sauces'      -- Burger Sauce
    when 'ING-056' then 'Savoury Sauces'      -- Cheese Sauce
    when 'ING-061' then 'Savoury Sauces'      -- Honey Garlic Sauce
    when 'ING-062' then 'Savoury Sauces'      -- Buffalo Sauce
    when 'ING-063' then 'Savoury Sauces'      -- Teriyaki Sauce
    when 'ING-065' then 'Savoury Sauces'      -- Carbonara Sauce
    when 'ING-073' then 'Savoury Sauces'      -- Dip Sauce
    when 'ING-079' then 'Savoury Sauces'      -- Toyomansi Sauce
    when 'ING-081' then 'Savoury Sauces'      -- Assorted Sauce Portion

    -- Safety net: leave unchanged any ingredient_id not listed above so
    -- this migration never silently rewrites a row it wasn't intended to.
    else ingredient_category
end
where ingredient_id in (
    -- Drinks
    'ING-001', 'ING-002', 'ING-077', 'ING-029',
    'ING-010', 'ING-011', 'ING-012', 'ING-013', 'ING-014', 'ING-015',
    'ING-016', 'ING-018', 'ING-020', 'ING-022', 'ING-023',
    'ING-030', 'ING-031', 'ING-032', 'ING-033', 'ING-034',
    'ING-076', 'ING-078', 'ING-080',
    'ING-035', 'ING-036', 'ING-040', 'ING-082',
    -- Food
    'ING-042', 'ING-043', 'ING-048', 'ING-060',
    'ING-066', 'ING-067', 'ING-069', 'ING-070', 'ING-071', 'ING-072',
    'ING-055', 'ING-058', 'ING-059', 'ING-074',
    'ING-044', 'ING-045', 'ING-046', 'ING-047', 'ING-064', 'ING-068',
    'ING-049', 'ING-050', 'ING-051', 'ING-056', 'ING-061', 'ING-062',
    'ING-063', 'ING-065', 'ING-073', 'ING-079', 'ING-081'
);

-- Sanity-check: anything left with one of the old broad categories
-- after this runs is either a new ingredient added since the seed (in
-- which case it should be re-categorized by the inventory team) or a
-- product packaging row (e.g. Plastic Cups under 'Takeout Supplies' —
-- not touched by this migration on purpose).
--
-- To audit after running:
--   select ingredient_id, ingredient_name, ingredient_category
--   from public.ingredients
--   where ingredient_category in (
--     'Coffee & Tea Bases', 'Syrups & Sauces', 'Pantry',
--     'Produce', 'Proteins'
--   )
--   order by ingredient_category, ingredient_id;
