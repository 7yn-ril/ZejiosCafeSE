-- Starter inventory seed for Zejios Cafe.
-- This uses public-standard recipe assumptions so the system can compute stock
-- without requiring the owner's proprietary formulas.

insert into public.ingredients (
    ingredient_id,
    ingredient_name,
    ingredient_unit,
    ingredient_current_stock,
    ingredient_minimum_stock,
    ingredient_cost_per_unit
) values
    ('ING-001', 'Espresso Shot', 'mL', 450, 80, 18.00),
    ('ING-002', 'Brewed Tea Base', 'mL', 30000, 5000, 0.02),
    ('ING-003', 'Whole Milk', 'mL', 40000, 8000, 0.06),
    ('ING-004', 'Oat Milk', 'mL', 10000, 2000, 0.12),
    ('ING-005', 'Half-and-Half', 'mL', 12000, 2500, 0.10),
    ('ING-006', 'Condensed Milk', 'mL', 6000, 1200, 0.07),
    ('ING-007', 'Cream Cheese Foam', 'mL', 7000, 1000, 0.18),
    ('ING-008', 'Sea Salt Cream', 'mL', 4000, 700, 0.15),
    ('ING-009', 'Whipped Cream', 'mL', 4000, 600, 0.12),
    ('ING-010', 'Vanilla Syrup', 'mL', 2500, 400, 0.16),
    ('ING-011', 'Caramel Syrup', 'mL', 3000, 500, 0.16),
    ('ING-012', 'Peppermint Syrup', 'mL', 1500, 250, 0.18),
    ('ING-013', 'White Mocha Sauce', 'mL', 2500, 400, 0.20),
    ('ING-014', 'Dark Mocha Sauce', 'mL', 3000, 500, 0.18),
    ('ING-015', 'Salted Caramel Sauce', 'mL', 2000, 300, 0.20),
    ('ING-016', 'Roasted Almond Syrup', 'mL', 1500, 250, 0.19),
    ('ING-017', 'Biscoff Spread', 'mL', 2500, 400, 0.22),
    ('ING-018', 'Coconut Syrup', 'mL', 1500, 250, 0.18),
    ('ING-019', 'Taro Powder', 'mL', 5000, 800, 0.45),
    ('ING-020', 'Okinawa Syrup', 'mL', 2500, 400, 0.16),
    ('ING-021', 'Chocolate Powder', 'mL', 4000, 600, 0.28),
    ('ING-022', 'Wintermelon Syrup', 'mL', 2500, 400, 0.15),
    ('ING-023', 'Brown Sugar Syrup', 'mL', 2500, 400, 0.14),
    ('ING-024', 'Matcha Powder', 'mL', 2000, 300, 1.20),
    ('ING-025', 'Cheesecake Mix', 'mL', 2000, 300, 0.40),
    ('ING-026', 'Oreo Crumbs', 'mL', 2500, 300, 0.22),
    ('ING-027', 'Black Forest Flavor', 'mL', 1800, 300, 0.35),
    ('ING-028', 'Chocolate Chips', 'mL', 2000, 300, 0.24),
    ('ING-029', 'Lemonade Base', 'mL', 18000, 3000, 0.05),
    ('ING-030', 'Lychee Syrup', 'mL', 2000, 300, 0.17),
    ('ING-031', 'Strawberry Syrup', 'mL', 2500, 400, 0.16),
    ('ING-032', 'Blueberry Syrup', 'mL', 2000, 300, 0.18),
    ('ING-033', 'Green Apple Syrup', 'mL', 2000, 300, 0.17),
    ('ING-034', 'Raspberry Syrup', 'mL', 1500, 250, 0.18),
    ('ING-035', 'Banana', 'pcs', 120, 20, 8.00),
    ('ING-036', 'Peanut Butter', 'mL', 2500, 400, 0.12),
    ('ING-037', 'Tapioca Pearl', 'mL', 5000, 800, 0.10),
    ('ING-038', 'Nata Jelly', 'mL', 5000, 800, 0.08),
    ('ING-039', 'Milk Tea Creamer', 'mL', 6000, 1000, 0.18),
    ('ING-040', 'Ice', 'mL', 100000, 20000, 0.005),
    ('ING-041', 'Burger Bun', 'pcs', 180, 30, 8.00),
    ('ING-042', 'Beef Patty', 'pcs', 220, 40, 30.00),
    ('ING-043', 'Chicken Fillet', 'pcs', 140, 25, 28.00),
    ('ING-044', 'Pineapple Slice', 'pcs', 150, 20, 4.00),
    ('ING-045', 'Lettuce', 'mL', 8000, 1500, 0.07),
    ('ING-046', 'Tomato Slice', 'pcs', 800, 120, 1.00),
    ('ING-047', 'Cheese Slice', 'pcs', 400, 60, 6.00),
    ('ING-048', 'Bacon Slice', 'pcs', 350, 60, 5.00),
    ('ING-049', 'Mushroom Sauce', 'mL', 4000, 600, 0.18),
    ('ING-050', 'Mayo Sauce', 'mL', 4000, 600, 0.10),
    ('ING-051', 'Burger Sauce', 'mL', 3500, 500, 0.11),
    ('ING-052', 'Fries', 'mL', 30000, 5000, 0.04),
    ('ING-053', 'Potato Mojos', 'mL', 20000, 3000, 0.05),
    ('ING-054', 'Nacho Chips', 'mL', 15000, 2500, 0.07),
    ('ING-055', 'Ground Beef', 'mL', 18000, 3000, 0.30),
    ('ING-056', 'Cheese Sauce', 'mL', 5000, 800, 0.14),
    ('ING-057', 'Quesadilla Tortilla', 'pcs', 150, 20, 6.00),
    ('ING-058', 'Calamari', 'mL', 12000, 2000, 0.24),
    ('ING-059', 'Chicken Fingers', 'pcs', 500, 80, 12.00),
    ('ING-060', 'Chicken Wings', 'pcs', 700, 120, 10.00),
    ('ING-061', 'Honey Garlic Sauce', 'mL', 2500, 400, 0.13),
    ('ING-062', 'Buffalo Sauce', 'mL', 2500, 400, 0.13),
    ('ING-063', 'Teriyaki Sauce', 'mL', 2500, 400, 0.12),
    ('ING-064', 'Fettuccine Pasta', 'mL', 10000, 1500, 0.06),
    ('ING-065', 'Carbonara Sauce', 'mL', 6000, 900, 0.18),
    ('ING-066', 'Longganisa', 'pcs', 180, 30, 15.00),
    ('ING-067', 'Hungarian Sausage', 'pcs', 150, 25, 18.00),
    ('ING-068', 'Rice Serving', 'pcs', 320, 50, 10.00),
    ('ING-069', 'Egg', 'pcs', 500, 80, 7.00),
    ('ING-070', 'Chicken Poppers', 'mL', 12000, 1800, 0.18),
    ('ING-071', 'Pork Sisig', 'mL', 12000, 1800, 0.22),
    ('ING-072', 'Fish Fillet', 'pcs', 120, 20, 24.00),
    ('ING-073', 'Dip Sauce', 'mL', 3000, 400, 0.10),
    ('ING-074', 'Pork Siomai', 'pcs', 350, 50, 6.00),
    ('ING-075', 'Bottled Water', 'pcs', 200, 30, 12.00),
    ('ING-076', 'Cucumber Syrup', 'mL', 1500, 200, 0.12),
    ('ING-077', 'Iced Tea Base', 'mL', 15000, 2500, 0.03),
    ('ING-078', 'Four Seasons Syrup', 'mL', 1800, 250, 0.14),
    ('ING-079', 'Toyomansi Sauce', 'mL', 2000, 250, 0.07),
    ('ING-080', 'Assorted Syrup Portion', 'mL', 2000, 300, 0.15),
    ('ING-081', 'Assorted Sauce Portion', 'mL', 2000, 300, 0.15),
    ('ING-082', 'Water', 'mL', 30000, 5000, 0.00),
    ('ING-083', 'Yakult', 'pcs', 120, 20, 18.00)
on conflict (ingredient_id) do update set
    ingredient_name = excluded.ingredient_name,
    ingredient_unit = excluded.ingredient_unit,
    ingredient_current_stock = excluded.ingredient_current_stock,
    ingredient_minimum_stock = excluded.ingredient_minimum_stock,
    ingredient_cost_per_unit = excluded.ingredient_cost_per_unit;

with starting_id as (
    select coalesce(max(substring(variant_ingredient_id from 5)::int), 0) as max_id
    from public.variant_ingredients
),
recipe_rows as (
    -- Combo meals
    select pv.product_variant_id, x.ingredient_id, x.required_quantity
    from public.product_variants pv
    join public.products p on p.product_id = pv.product_id
    cross join lateral (
        values
            ('ING-041', 1::numeric),
            ('ING-042', 1::numeric),
            ('ING-045', 10::numeric),
            ('ING-046', 2::numeric),
            ('ING-047', 1::numeric),
            ('ING-051', 20::numeric),
            ('ING-002', 180::numeric),
            ('ING-039', 20::numeric),
            ('ING-023', 20::numeric),
            ('ING-040', 120::numeric)
    ) x(ingredient_id, required_quantity)
    where p.product_id = 'PRD-001'

    union all

    select pv.product_variant_id, x.ingredient_id, x.required_quantity
    from public.product_variants pv
    join public.products p on p.product_id = pv.product_id
    cross join lateral (
        values
            ('ING-041', 1::numeric),
            ('ING-042', 1::numeric),
            ('ING-044', 1::numeric),
            ('ING-045', 10::numeric),
            ('ING-046', 2::numeric),
            ('ING-047', 1::numeric),
            ('ING-048', 1::numeric),
            ('ING-051', 20::numeric),
            ('ING-002', 240::numeric),
            ('ING-030', 25::numeric),
            ('ING-007', 35::numeric),
            ('ING-040', 120::numeric)
    ) x(ingredient_id, required_quantity)
    where p.product_id = 'PRD-002'

    union all

    select pv.product_variant_id, x.ingredient_id, x.required_quantity
    from public.product_variants pv
    join public.products p on p.product_id = pv.product_id
    cross join lateral (
        values
            ('ING-041', 1::numeric),
            ('ING-042', 1::numeric),
            ('ING-045', 10::numeric),
            ('ING-046', 2::numeric),
            ('ING-047', 1::numeric),
            ('ING-051', 20::numeric),
            ('ING-052', 150::numeric),
            ('ING-073', 20::numeric),
            ('ING-029', 240::numeric),
            ('ING-033', 25::numeric),
            ('ING-040', 120::numeric)
    ) x(ingredient_id, required_quantity)
    where p.product_id = 'PRD-003'

    union all

    select pv.product_variant_id, x.ingredient_id, x.required_quantity
    from public.product_variants pv
    join public.products p on p.product_id = pv.product_id
    cross join lateral (
        values
            ('ING-041', 2::numeric),
            ('ING-042', 2::numeric),
            ('ING-045', 20::numeric),
            ('ING-046', 4::numeric),
            ('ING-047', 2::numeric),
            ('ING-051', 40::numeric),
            ('ING-029', 480::numeric),
            ('ING-033', 50::numeric),
            ('ING-040', 240::numeric)
    ) x(ingredient_id, required_quantity)
    where p.product_id = 'PRD-004'

    union all

    select pv.product_variant_id, x.ingredient_id, x.required_quantity
    from public.product_variants pv
    join public.products p on p.product_id = pv.product_id
    cross join lateral (
        values
            ('ING-001', 2::numeric),
            ('ING-003', 240::numeric),
            ('ING-040', 100::numeric),
            ('ING-064', 120::numeric),
            ('ING-065', 100::numeric),
            ('ING-048', 2::numeric),
            ('ING-047', 1::numeric)
    ) x(ingredient_id, required_quantity)
    where p.product_id = 'PRD-005'

    union all

    select pv.product_variant_id, x.ingredient_id, x.required_quantity
    from public.product_variants pv
    join public.products p on p.product_id = pv.product_id
    cross join lateral (
        values
            ('ING-001', 4::numeric),
            ('ING-003', 480::numeric),
            ('ING-040', 200::numeric),
            ('ING-054', 120::numeric),
            ('ING-055', 80::numeric),
            ('ING-050', 20::numeric),
            ('ING-056', 30::numeric)
    ) x(ingredient_id, required_quantity)
    where p.product_id = 'PRD-006'

    union all

    -- Classic milk tea
    select pv.product_variant_id, x.ingredient_id, x.required_quantity
    from public.product_variants pv
    join public.products p on p.product_id = pv.product_id
    cross join lateral (
        values
            ('ING-002', case when lower(trim(pv.variant_name)) in ('mezzo', '16oz', '16 oz') then 180 else 240 end::numeric),
            ('ING-039', case when lower(trim(pv.variant_name)) in ('mezzo', '16oz', '16 oz') then 20 else 26 end::numeric),
            (
                case
                    when p.product_id = 'PRD-007' then 'ING-019'
                    when p.product_id = 'PRD-008' then 'ING-020'
                    when p.product_id = 'PRD-009' then 'ING-021'
                    when p.product_id = 'PRD-010' then 'ING-022'
                    when p.product_id = 'PRD-011' then 'ING-023'
                end,
                case
                    when p.product_id = 'PRD-007' and lower(trim(pv.variant_name)) in ('mezzo', '16oz', '16 oz') then 30
                    when p.product_id = 'PRD-007' then 36
                    when p.product_id = 'PRD-009' and lower(trim(pv.variant_name)) in ('mezzo', '16oz', '16 oz') then 22
                    when p.product_id = 'PRD-009' then 30
                    when lower(trim(pv.variant_name)) in ('mezzo', '16oz', '16 oz') then 25
                    else 35
                end::numeric
            ),
            ('ING-040', case when lower(trim(pv.variant_name)) in ('mezzo', '16oz', '16 oz') then 120 else 180 end::numeric)
    ) x(ingredient_id, required_quantity)
    where p.product_id in ('PRD-007', 'PRD-008', 'PRD-009', 'PRD-010', 'PRD-011')

    union all

    -- Creamcheese series
    select pv.product_variant_id, x.ingredient_id, x.required_quantity
    from public.product_variants pv
    join public.products p on p.product_id = pv.product_id
    cross join lateral (
        values
            ('ING-002', case when lower(trim(pv.variant_name)) in ('mezzo', '16oz', '16 oz') then 180 else 240 end::numeric),
            ('ING-039', case when lower(trim(pv.variant_name)) in ('mezzo', '16oz', '16 oz') then 20 else 26 end::numeric),
            (
                case
                    when p.product_id = 'PRD-012' then 'ING-019'
                    when p.product_id = 'PRD-013' then 'ING-024'
                    else 'ING-021'
                end,
                case
                    when p.product_id = 'PRD-012' and lower(trim(pv.variant_name)) in ('mezzo', '16oz', '16 oz') then 30
                    when p.product_id = 'PRD-012' then 36
                    when p.product_id = 'PRD-013' and lower(trim(pv.variant_name)) in ('mezzo', '16oz', '16 oz') then 10
                    when p.product_id = 'PRD-013' then 12
                    when lower(trim(pv.variant_name)) in ('mezzo', '16oz', '16 oz') then 22
                    else 30
                end::numeric
            ),
            ('ING-007', case when lower(trim(pv.variant_name)) in ('mezzo', '16oz', '16 oz') then 35 else 45 end::numeric),
            ('ING-028', case when p.product_id = 'PRD-014' and lower(trim(pv.variant_name)) in ('mezzo', '16oz', '16 oz') then 12 when p.product_id = 'PRD-014' then 16 else 0 end::numeric),
            ('ING-040', case when lower(trim(pv.variant_name)) in ('mezzo', '16oz', '16 oz') then 120 else 180 end::numeric)
    ) x(ingredient_id, required_quantity)
    where p.product_id in ('PRD-012', 'PRD-013', 'PRD-014')
      and x.required_quantity > 0

    union all

    -- Cheesecake series
    select pv.product_variant_id, x.ingredient_id, x.required_quantity
    from public.product_variants pv
    join public.products p on p.product_id = pv.product_id
    cross join lateral (
        values
            ('ING-002', case when lower(trim(pv.variant_name)) in ('mezzo', '16oz', '16 oz') then 180 else 240 end::numeric),
            ('ING-039', case when lower(trim(pv.variant_name)) in ('mezzo', '16oz', '16 oz') then 20 else 26 end::numeric),
            ('ING-025', case when lower(trim(pv.variant_name)) in ('mezzo', '16oz', '16 oz') then 24 else 30 end::numeric),
            (
                case when p.product_id = 'PRD-015' then 'ING-026' else 'ING-027' end,
                case when lower(trim(pv.variant_name)) in ('mezzo', '16oz', '16 oz') then 14 else 18 end::numeric
            ),
            ('ING-040', case when lower(trim(pv.variant_name)) in ('mezzo', '16oz', '16 oz') then 120 else 180 end::numeric)
    ) x(ingredient_id, required_quantity)
    where p.product_id in ('PRD-015', 'PRD-016')

    union all

    -- Fruit tea
    select pv.product_variant_id, x.ingredient_id, x.required_quantity
    from public.product_variants pv
    join public.products p on p.product_id = pv.product_id
    cross join lateral (
        values
            ('ING-002', case when pv.variant_name = '16oz' then 240 else 330 end::numeric),
            (
                case
                    when p.product_id = 'PRD-017' then 'ING-030'
                    when p.product_id = 'PRD-018' then 'ING-031'
                    when p.product_id = 'PRD-019' then 'ING-032'
                    else 'ING-033'
                end,
                case when pv.variant_name = '16oz' then 25 else 35 end::numeric
            ),
            ('ING-040', case when pv.variant_name = '16oz' then 120 else 180 end::numeric)
    ) x(ingredient_id, required_quantity)
    where p.product_id in ('PRD-017', 'PRD-018', 'PRD-019', 'PRD-020')

    union all

    -- Cremachee
    select pv.product_variant_id, x.ingredient_id, x.required_quantity
    from public.product_variants pv
    join public.products p on p.product_id = pv.product_id
    cross join lateral (
        values
            ('ING-002', case when pv.variant_name = '16oz' then 240 else 330 end::numeric),
            (
                case
                    when p.product_id = 'PRD-021' then 'ING-030'
                    when p.product_id = 'PRD-022' then 'ING-031'
                    when p.product_id = 'PRD-023' then 'ING-032'
                    else 'ING-033'
                end,
                case when pv.variant_name = '16oz' then 25 else 35 end::numeric
            ),
            ('ING-007', case when pv.variant_name = '16oz' then 35 else 45 end::numeric),
            ('ING-040', case when pv.variant_name = '16oz' then 120 else 180 end::numeric)
    ) x(ingredient_id, required_quantity)
    where p.product_id in ('PRD-021', 'PRD-022', 'PRD-023', 'PRD-024')

    union all

    -- Americano
    select pv.product_variant_id, x.ingredient_id, x.required_quantity
    from public.product_variants pv
    join public.products p on p.product_id = pv.product_id
    cross join lateral (
        values
            ('ING-001', 2::numeric),
            ('ING-082', 180::numeric),
            ('ING-040', 100::numeric)
    ) x(ingredient_id, required_quantity)
    where p.product_id = 'PRD-025'

    union all

    -- Latte
    select pv.product_variant_id, x.ingredient_id, x.required_quantity
    from public.product_variants pv
    join public.products p on p.product_id = pv.product_id
    cross join lateral (
        values
            ('ING-001', 2::numeric),
            ('ING-003', 240::numeric),
            ('ING-040', 100::numeric)
    ) x(ingredient_id, required_quantity)
    where p.product_id = 'PRD-026'

    union all

    -- Vietnamese Black
    select pv.product_variant_id, x.ingredient_id, x.required_quantity
    from public.product_variants pv
    join public.products p on p.product_id = pv.product_id
    cross join lateral (
        values
            ('ING-001', case when pv.variant_name = 'Regular' then 2 else 3 end::numeric),
            ('ING-082', case when pv.variant_name = 'Regular' then 140 else 190 end::numeric),
            ('ING-040', case when pv.variant_name = 'Regular' then 100 else 140 end::numeric)
    ) x(ingredient_id, required_quantity)
    where p.product_id = 'PRD-027'

    union all

    -- Vietnamese Coffee
    select pv.product_variant_id, x.ingredient_id, x.required_quantity
    from public.product_variants pv
    join public.products p on p.product_id = pv.product_id
    cross join lateral (
        values
            ('ING-001', case when pv.variant_name = 'Regular' then 2 else 3 end::numeric),
            ('ING-006', case when pv.variant_name = 'Regular' then 25 else 35 end::numeric),
            ('ING-082', case when pv.variant_name = 'Regular' then 100 else 150 end::numeric),
            ('ING-040', case when pv.variant_name = 'Regular' then 100 else 140 end::numeric)
    ) x(ingredient_id, required_quantity)
    where p.product_id = 'PRD-028'

    union all

    -- Standard coffee family
    select pv.product_variant_id, x.ingredient_id, x.required_quantity
    from public.product_variants pv
    join public.products p on p.product_id = pv.product_id
    cross join lateral (
        values
            ('ING-001', 2::numeric),
            ('ING-003', case when p.product_id = 'PRD-035' then 160 else 180 end::numeric),
            (
                case
                    when p.product_id = 'PRD-029' then 'ING-006'
                    when p.product_id = 'PRD-030' then 'ING-011'
                    when p.product_id = 'PRD-031' then 'ING-015'
                    when p.product_id = 'PRD-032' then 'ING-014'
                    when p.product_id = 'PRD-033' then 'ING-013'
                    when p.product_id = 'PRD-034' then 'ING-016'
                    when p.product_id = 'PRD-035' then 'ING-024'
                    when p.product_id = 'PRD-036' then 'ING-008'
                    when p.product_id = 'PRD-037' then 'ING-012'
                    when p.product_id = 'PRD-038' then 'ING-017'
                end,
                case
                    when p.product_id = 'PRD-029' then 30
                    when p.product_id in ('PRD-030', 'PRD-031', 'PRD-032', 'PRD-033') then 20
                    when p.product_id = 'PRD-034' then 18
                    when p.product_id = 'PRD-035' then 8
                    when p.product_id = 'PRD-036' then 30
                    when p.product_id = 'PRD-037' then 10
                    else 25
                end::numeric
            ),
            ('ING-014', case when p.product_id = 'PRD-037' then 15 else 0 end::numeric),
            ('ING-040', 100::numeric)
    ) x(ingredient_id, required_quantity)
    where p.product_id in ('PRD-029', 'PRD-030', 'PRD-031', 'PRD-032', 'PRD-033', 'PRD-034', 'PRD-035', 'PRD-036', 'PRD-037', 'PRD-038')
      and x.required_quantity > 0

    union all

    -- Non-coffee
    select pv.product_variant_id, x.ingredient_id, x.required_quantity
    from public.product_variants pv
    join public.products p on p.product_id = pv.product_id
    cross join lateral (
        values
            (
                case when p.product_id = 'PRD-040' then 'ING-004' else 'ING-003' end,
                case when p.product_id = 'PRD-041' then 200 else 220 end::numeric
            ),
            ('ING-024', case when p.product_id = 'PRD-041' then 8 else 10 end::numeric),
            ('ING-031', case when p.product_id = 'PRD-041' then 10 else 0 end::numeric),
            ('ING-032', case when p.product_id = 'PRD-041' then 10 else 0 end::numeric),
            ('ING-040', 100::numeric)
    ) x(ingredient_id, required_quantity)
    where p.product_id in ('PRD-039', 'PRD-040', 'PRD-041')
      and x.required_quantity > 0

    union all

    -- Breve coffee
    select pv.product_variant_id, x.ingredient_id, x.required_quantity
    from public.product_variants pv
    join public.products p on p.product_id = pv.product_id
    cross join lateral (
        values
            ('ING-001', 2::numeric),
            ('ING-005', 180::numeric),
            (
                case
                    when p.product_id = 'PRD-042' then 'ING-006'
                    when p.product_id = 'PRD-043' then 'ING-011'
                    else 'ING-013'
                end,
                case
                    when p.product_id = 'PRD-042' then 30
                    when p.product_id = 'PRD-043' then 20
                    else 20
                end::numeric
            ),
            ('ING-010', case when p.product_id = 'PRD-043' then 10 else 0 end::numeric),
            ('ING-040', 100::numeric)
    ) x(ingredient_id, required_quantity)
    where p.product_id in ('PRD-042', 'PRD-043', 'PRD-044')
      and x.required_quantity > 0

    union all

    -- Espresso Kelapa
    select pv.product_variant_id, x.ingredient_id, x.required_quantity
    from public.product_variants pv
    join public.products p on p.product_id = pv.product_id
    cross join lateral (
        values
            ('ING-001', 2::numeric),
            ('ING-003', 160::numeric),
            ('ING-018', 25::numeric),
            ('ING-040', 100::numeric)
    ) x(ingredient_id, required_quantity)
    where p.product_id = 'PRD-045'

    union all

    -- Coffee frappes
    select pv.product_variant_id, x.ingredient_id, x.required_quantity
    from public.product_variants pv
    join public.products p on p.product_id = pv.product_id
    cross join lateral (
        values
            ('ING-001', 2::numeric),
            ('ING-003', 120::numeric),
            (
                case
                    when p.product_id = 'PRD-046' then 'ING-014'
                    when p.product_id = 'PRD-047' then 'ING-011'
                    when p.product_id = 'PRD-048' then 'ING-013'
                    else 'ING-017'
                end,
                case
                    when p.product_id = 'PRD-046' then 15
                    when p.product_id = 'PRD-047' then 20
                    when p.product_id = 'PRD-048' then 20
                    else 25
                end::numeric
            ),
            ('ING-010', case when p.product_id in ('PRD-046', 'PRD-047') then 10 else 0 end::numeric),
            ('ING-040', 180::numeric),
            ('ING-009', 20::numeric)
    ) x(ingredient_id, required_quantity)
    where p.product_id in ('PRD-046', 'PRD-047', 'PRD-048', 'PRD-049')
      and x.required_quantity > 0

    union all

    -- Non-coffee frappes
    select pv.product_variant_id, x.ingredient_id, x.required_quantity
    from public.product_variants pv
    join public.products p on p.product_id = pv.product_id
    cross join lateral (
        values
            ('ING-003', 150::numeric),
            (
                case
                    when p.product_id = 'PRD-050' then 'ING-021'
                    when p.product_id = 'PRD-051' then 'ING-031'
                    when p.product_id = 'PRD-052' then 'ING-032'
                    when p.product_id = 'PRD-053' then 'ING-034'
                    else 'ING-024'
                end,
                case
                    when p.product_id = 'PRD-050' then 25
                    when p.product_id = 'PRD-054' then 10
                    else 30
                end::numeric
            ),
            ('ING-014', case when p.product_id = 'PRD-050' then 15 else 0 end::numeric),
            ('ING-040', 200::numeric),
            ('ING-009', 20::numeric)
    ) x(ingredient_id, required_quantity)
    where p.product_id in ('PRD-050', 'PRD-051', 'PRD-052', 'PRD-053', 'PRD-054')
      and x.required_quantity > 0

    union all

    -- Lemonades
    select pv.product_variant_id, x.ingredient_id, x.required_quantity
    from public.product_variants pv
    join public.products p on p.product_id = pv.product_id
    cross join lateral (
        values
            ('ING-029', 240::numeric),
            (
                case
                    when p.product_id = 'PRD-055' then 'ING-030'
                    when p.product_id = 'PRD-056' then 'ING-031'
                    else 'ING-033'
                end,
                25::numeric
            ),
            ('ING-040', 120::numeric)
    ) x(ingredient_id, required_quantity)
    where p.product_id in ('PRD-055', 'PRD-056', 'PRD-057')

    union all

    -- Smoothies
    select pv.product_variant_id, x.ingredient_id, x.required_quantity
    from public.product_variants pv
    join public.products p on p.product_id = pv.product_id
    cross join lateral (
        values
            ('ING-035', 1::numeric),
            ('ING-003', 180::numeric),
            ('ING-021', case when p.product_id = 'PRD-059' then 20 else 0 end::numeric),
            ('ING-036', case when p.product_id = 'PRD-060' then 25 else 0 end::numeric),
            ('ING-040', 150::numeric)
    ) x(ingredient_id, required_quantity)
    where p.product_id in ('PRD-058', 'PRD-059', 'PRD-060')
      and x.required_quantity > 0

    union all

    -- Rice meals
    select pv.product_variant_id, x.ingredient_id, x.required_quantity
    from public.product_variants pv
    join public.products p on p.product_id = pv.product_id
    cross join lateral (
        values
            (
                case
                    when p.product_id = 'PRD-061' then 'ING-066'
                    when p.product_id = 'PRD-062' then 'ING-067'
                    when p.product_id = 'PRD-063' then 'ING-042'
                    when p.product_id = 'PRD-064' then 'ING-070'
                    when p.product_id = 'PRD-065' then 'ING-071'
                    else 'ING-072'
                end,
                case
                    when p.product_id = 'PRD-064' then 150
                    when p.product_id = 'PRD-065' then 150
                    else 1
                end::numeric
            ),
            ('ING-068', 1::numeric),
            ('ING-069', case when p.product_id in ('PRD-061', 'PRD-062') then 1 else 0 end::numeric),
            ('ING-047', case when p.product_id = 'PRD-062' then 1 else 0 end::numeric),
            ('ING-049', case when p.product_id = 'PRD-063' then 60 else 0 end::numeric),
            ('ING-073', case when p.product_id in ('PRD-064', 'PRD-066') then 20 else 0 end::numeric)
    ) x(ingredient_id, required_quantity)
    where p.product_id in ('PRD-061', 'PRD-062', 'PRD-063', 'PRD-064', 'PRD-065', 'PRD-066')
      and x.required_quantity > 0

    union all

    -- Wings
    select pv.product_variant_id, x.ingredient_id, x.required_quantity
    from public.product_variants pv
    join public.products p on p.product_id = pv.product_id
    cross join lateral (
        values
            ('ING-060', case when pv.variant_name = 'Ala Carte' then 6 else 12 end::numeric),
            (
                case
                    when p.product_id = 'PRD-067' then 'ING-061'
                    when p.product_id = 'PRD-068' then 'ING-062'
                    else 'ING-063'
                end,
                case when pv.variant_name = 'Ala Carte' then 45 else 90 end::numeric
            )
    ) x(ingredient_id, required_quantity)
    where p.product_id in ('PRD-067', 'PRD-068', 'PRD-069')

    union all

    -- Pasta
    select pv.product_variant_id, x.ingredient_id, x.required_quantity
    from public.product_variants pv
    join public.products p on p.product_id = pv.product_id
    cross join lateral (
        values
            ('ING-064', case when pv.variant_name = 'Ala Carte' then 120 else 240 end::numeric),
            ('ING-065', case when pv.variant_name = 'Ala Carte' then 100 else 200 end::numeric),
            ('ING-048', case when pv.variant_name = 'Ala Carte' then 2 else 4 end::numeric),
            ('ING-047', case when pv.variant_name = 'Ala Carte' then 1 else 2 end::numeric)
    ) x(ingredient_id, required_quantity)
    where p.product_id = 'PRD-070'

    union all

    -- Burgers
    select pv.product_variant_id, x.ingredient_id, x.required_quantity
    from public.product_variants pv
    join public.products p on p.product_id = pv.product_id
    cross join lateral (
        values
            ('ING-041', 1::numeric),
            (
                case
                    when p.product_id = 'PRD-075' then 'ING-043'
                    else 'ING-042'
                end,
                case
                    when p.product_id in ('PRD-072', 'PRD-074') then 2
                    else 1
                end::numeric
            ),
            ('ING-045', 10::numeric),
            ('ING-046', case when p.product_id = 'PRD-075' then 0 else 2 end::numeric),
            ('ING-047', case when p.product_id = 'PRD-072' then 2 when p.product_id = 'PRD-074' then 2 when p.product_id in ('PRD-071', 'PRD-073') then 1 else 0 end::numeric),
            ('ING-048', case when p.product_id = 'PRD-073' then 1 when p.product_id = 'PRD-074' then 2 else 0 end::numeric),
            ('ING-044', case when p.product_id = 'PRD-073' then 1 when p.product_id = 'PRD-074' then 2 else 0 end::numeric),
            ('ING-051', case when p.product_id in ('PRD-071', 'PRD-072', 'PRD-073', 'PRD-074') then 20 else 0 end::numeric),
            ('ING-050', case when p.product_id in ('PRD-075', 'PRD-076') then 15 else 0 end::numeric),
            ('ING-049', case when p.product_id = 'PRD-076' then 30 else 0 end::numeric)
    ) x(ingredient_id, required_quantity)
    where p.product_id in ('PRD-071', 'PRD-072', 'PRD-073', 'PRD-074', 'PRD-075', 'PRD-076')
      and x.required_quantity > 0

    union all

    -- Appetizers and sides
    select pv.product_variant_id, x.ingredient_id, x.required_quantity
    from public.product_variants pv
    join public.products p on p.product_id = pv.product_id
    cross join lateral (
        values
            (
                case
                    when p.product_id = 'PRD-077' then 'ING-059'
                    when p.product_id = 'PRD-078' then 'ING-058'
                    when p.product_id = 'PRD-079' then 'ING-052'
                    when p.product_id = 'PRD-080' then 'ING-053'
                    when p.product_id in ('PRD-081', 'PRD-082') then 'ING-054'
                    when p.product_id = 'PRD-083' then 'ING-057'
                    else 'ING-074'
                end,
                case
                    when p.product_id = 'PRD-077' and pv.variant_name = 'Ala Carte' then 4
                    when p.product_id = 'PRD-077' then 8
                    when p.product_id = 'PRD-078' and pv.variant_name = 'Ala Carte' then 180
                    when p.product_id = 'PRD-078' then 360
                    when p.product_id = 'PRD-079' then 150
                    when p.product_id = 'PRD-080' then 150
                    when p.product_id = 'PRD-081' then 120
                    when p.product_id = 'PRD-082' then 200
                    when p.product_id = 'PRD-083' then 2
                    else 4
                end::numeric
            ),
            ('ING-073', case when p.product_id in ('PRD-077', 'PRD-078') and pv.variant_name = 'Platter' then 40 when p.product_id in ('PRD-077', 'PRD-078', 'PRD-079', 'PRD-080') then 20 else 0 end::numeric),
            ('ING-055', case when p.product_id = 'PRD-081' then 80 when p.product_id = 'PRD-082' then 120 when p.product_id = 'PRD-083' then 100 else 0 end::numeric),
            ('ING-056', case when p.product_id = 'PRD-081' then 30 when p.product_id = 'PRD-082' then 50 when p.product_id = 'PRD-083' then 40 else 0 end::numeric),
            ('ING-050', case when p.product_id = 'PRD-081' then 20 when p.product_id = 'PRD-082' then 30 else 0 end::numeric),
            ('ING-079', case when p.product_id = 'PRD-084' then 15 else 0 end::numeric)
    ) x(ingredient_id, required_quantity)
    where p.product_id in ('PRD-077', 'PRD-078', 'PRD-079', 'PRD-080', 'PRD-081', 'PRD-082', 'PRD-083', 'PRD-084')
      and x.required_quantity > 0

    union all

    -- Pitcher drinks
    select pv.product_variant_id, x.ingredient_id, x.required_quantity
    from public.product_variants pv
    join public.products p on p.product_id = pv.product_id
    cross join lateral (
        values
            (
                case
                    when p.product_id = 'PRD-085' then 'ING-029'
                    when p.product_id = 'PRD-086' then 'ING-077'
                    else 'ING-082'
                end,
                case
                    when p.product_id = 'PRD-085' then 700
                    when p.product_id = 'PRD-086' then 800
                    else 500
                end::numeric
            ),
            ('ING-076', case when p.product_id = 'PRD-085' then 50 else 0 end::numeric),
            ('ING-029', case when p.product_id = 'PRD-086' then 100 else 0 end::numeric),
            ('ING-078', case when p.product_id = 'PRD-087' then 50 else 0 end::numeric),
            ('ING-040', 300::numeric)
    ) x(ingredient_id, required_quantity)
    where p.product_id in ('PRD-085', 'PRD-086', 'PRD-087')
      and x.required_quantity > 0

    union all

    -- Add-ons
    select pv.product_variant_id, x.ingredient_id, x.required_quantity
    from public.product_variants pv
    join public.products p on p.product_id = pv.product_id
    cross join lateral (
        values
            (
                case
                    when p.product_id = 'PRD-088' then 'ING-037'
                    when p.product_id = 'PRD-089' then 'ING-038'
                    when p.product_id = 'PRD-090' then 'ING-007'
                    when p.product_id = 'PRD-091' then 'ING-025'
                    when p.product_id = 'PRD-092' then 'ING-028'
                    when p.product_id = 'PRD-093' then 'ING-026'
                    when p.product_id = 'PRD-094' then 'ING-083'
                    when p.product_id = 'PRD-095' then 'ING-003'
                    when p.product_id = 'PRD-096' then 'ING-004'
                    when p.product_id = 'PRD-097' then 'ING-001'
                    when p.product_id = 'PRD-098' then 'ING-009'
                    when p.product_id = 'PRD-099' then 'ING-080'
                    when p.product_id = 'PRD-100' then 'ING-081'
                    else 'ING-008'
                end,
                case
                    when p.product_id in ('PRD-088', 'PRD-089') then 50
                    when p.product_id = 'PRD-090' then 35
                    when p.product_id = 'PRD-091' then 25
                    when p.product_id in ('PRD-092', 'PRD-093') then 15
                    when p.product_id = 'PRD-094' then 1
                    when p.product_id in ('PRD-095', 'PRD-096') then 120
                    when p.product_id = 'PRD-097' then 1
                    when p.product_id = 'PRD-098' then 25
                    when p.product_id in ('PRD-099', 'PRD-100') then 20
                    else 30
                end::numeric
            )
    ) x(ingredient_id, required_quantity)
    where p.product_id in ('PRD-088', 'PRD-089', 'PRD-090', 'PRD-091', 'PRD-092', 'PRD-093', 'PRD-094', 'PRD-095', 'PRD-096', 'PRD-097', 'PRD-098', 'PRD-099', 'PRD-100', 'PRD-101')

    union all

    -- Extras, excluding platter upgrade
    select pv.product_variant_id, x.ingredient_id, x.required_quantity
    from public.product_variants pv
    join public.products p on p.product_id = pv.product_id
    cross join lateral (
        values
            (
                case
                    when p.product_id = 'PRD-102' then 'ING-068'
                    when p.product_id = 'PRD-104' then 'ING-069'
                    when p.product_id = 'PRD-105' then 'ING-048'
                    when p.product_id = 'PRD-106' then 'ING-047'
                    when p.product_id = 'PRD-107' then 'ING-079'
                    else 'ING-073'
                end,
                case
                    when p.product_id in ('PRD-102', 'PRD-104', 'PRD-105', 'PRD-106') then 1
                    when p.product_id = 'PRD-107' then 10
                    else 20
                end::numeric
            )
    ) x(ingredient_id, required_quantity)
    where p.product_id in ('PRD-102', 'PRD-104', 'PRD-105', 'PRD-106', 'PRD-107', 'PRD-108')

    union all

    -- Others
    select pv.product_variant_id, x.ingredient_id, x.required_quantity
    from public.product_variants pv
    join public.products p on p.product_id = pv.product_id
    cross join lateral (
        values
            ('ING-075', 1::numeric)
    ) x(ingredient_id, required_quantity)
    where p.product_id = 'PRD-109'
),
recipe_rows_deduped as (
    select distinct product_variant_id, ingredient_id, required_quantity
    from recipe_rows
),
numbered_recipe_rows as (
    select
        concat('RCP-', lpad((starting_id.max_id + row_number() over (order by product_variant_id, ingredient_id))::text, 3, '0')) as variant_ingredient_id,
        product_variant_id,
        ingredient_id,
        required_quantity
    from recipe_rows_deduped
    cross join starting_id
)
insert into public.variant_ingredients (
    variant_ingredient_id,
    product_variant_id,
    ingredient_id,
    required_quantity
)
select
    variant_ingredient_id,
    product_variant_id,
    ingredient_id,
    required_quantity
from numbered_recipe_rows
on conflict (product_variant_id, ingredient_id) do update set
    required_quantity = excluded.required_quantity;

update public.product_variants pv
set
    variant_track_inventory = true,
    variant_manual_stock_left = 0
where exists (
    select 1
    from public.variant_ingredients vi
    where vi.product_variant_id = pv.product_variant_id
);

update public.product_variants
set
    variant_track_inventory = false,
    variant_manual_stock_left = 25
where product_id = 'PRD-103';

-- Validation helpers:
-- 1. Show any tracked variant that still has no recipe rows.
-- select p.product_name, pv.variant_name
-- from public.product_variants pv
-- join public.products p on p.product_id = pv.product_id
-- left join public.variant_ingredients vi on vi.product_variant_id = pv.product_variant_id
-- where pv.variant_track_inventory = true
-- group by p.product_name, pv.variant_name
-- having count(vi.variant_ingredient_id) = 0
-- order by p.product_name, pv.variant_name;
--
-- 2. Preview computed POS stock.
-- select category_name, product_name, variant_name, variant_stock_left
-- from public.product_variant_stock_view
-- order by category_name, product_name, variant_name;
