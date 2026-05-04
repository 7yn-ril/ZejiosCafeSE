alter table public.ingredients
    add column if not exists ingredient_ml_per_serving numeric,
    add column if not exists ingredient_ml_per_bottle numeric;

comment on column public.ingredients.ingredient_ml_per_serving is
    'For ingredients measured in ml, the amount consumed by one serving.';

comment on column public.ingredients.ingredient_ml_per_bottle is
    'For ingredients measured in ml, the default bottle size used by restocking.';
