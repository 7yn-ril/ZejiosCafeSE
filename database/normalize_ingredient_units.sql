-- Normalize ingredient units to the app's supported inventory units.
-- The item forms now only allow pcs and mL.

alter table public.ingredients
    add column if not exists ingredient_ml_per_serving numeric,
    add column if not exists ingredient_ml_per_bottle numeric;

update public.ingredients
set ingredient_unit = case
    when lower(trim(ingredient_unit)) in (
        'ml',
        'milliliter',
        'milliliters',
        'millilitre',
        'millilitres',
        'l',
        'liter',
        'liters',
        'litre',
        'litres',
        'g',
        'gram',
        'grams',
        'kg',
        'kilogram',
        'kilograms',
        'shot',
        'shots'
    ) then 'mL'
    else 'pcs'
end
where ingredient_unit is null
   or ingredient_unit not in ('pcs', 'mL');

update public.ingredients
set ingredient_ml_per_serving = coalesce(ingredient_ml_per_serving, 1),
    ingredient_ml_per_bottle = coalesce(ingredient_ml_per_bottle, 1)
where ingredient_unit = 'mL';

update public.ingredients
set ingredient_ml_per_serving = null,
    ingredient_ml_per_bottle = null
where ingredient_unit = 'pcs';
