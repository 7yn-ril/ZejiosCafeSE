package com.example.zejioscafese.inventory.data.model

import com.example.zejioscafese.pos.data.model.Ingredient
import java.util.Locale

/**
 * Filters the ingredient spinner in the Add/Edit Product dialog by
 * `Ingredient.category`. The allowed-categories sets below assume the
 * `database/split_ingredient_categories.sql` migration has been run —
 * that migration breaks the old broad buckets (Syrups & Sauces, Proteins,
 * Pantry, Produce, Coffee & Tea Bases) into single-domain finer
 * categories so a Burger never sees a Caramel Syrup and a Coffee never
 * sees a Longganisa.
 *
 * Maintenance contract: when a new *ingredient category* is introduced
 * via SQL, add a constant for it below and decide which product
 * categories should surface it. Adding a new ingredient inside an
 * existing category needs zero code changes.
 */
object ProductIngredientFilter {

    // ── Drinks-side ingredient categories ──────────────────────────────
    private const val CAT_COFFEE_BASES = "Coffee Bases"
    private const val CAT_TEA_BASES = "Tea Bases"
    private const val CAT_LEMONADE_BASES = "Lemonade Bases"
    private const val CAT_SWEET_SYRUPS = "Sweet Syrups"
    private const val CAT_POWDERS = "Powders & Mixes"
    private const val CAT_DAIRY = "Dairy"           // drink-side only after the migration
    private const val CAT_DRINK_PRODUCE = "Drink Produce"
    private const val CAT_DRINK_PANTRY = "Drink Pantry"
    private const val CAT_BEVERAGES = "Beverages"

    // ── Food-side ingredient categories ────────────────────────────────
    private const val CAT_BAKERY = "Bakery & Bread"
    private const val CAT_BURGER_PROTEINS = "Burger Proteins"
    private const val CAT_WING_PROTEINS = "Wing Proteins"
    private const val CAT_RICE_MEAL_PROTEINS = "Rice Meal Proteins"
    private const val CAT_SIDE_PROTEINS = "Side Proteins"
    private const val CAT_FOOD_PRODUCE = "Food Produce"
    private const val CAT_FOOD_DAIRY = "Food Dairy"
    private const val CAT_FOOD_PANTRY = "Food Pantry"
    private const val CAT_SAVOURY_SAUCES = "Savoury Sauces"
    private const val CAT_FROZEN = "Frozen & Sides"

    // ── Product-category → allowed ingredient categories ───────────────
    // Drinks
    private val MILK_TEA_ALLOWED = setOf(CAT_TEA_BASES, CAT_POWDERS, CAT_SWEET_SYRUPS, CAT_DAIRY, CAT_DRINK_PANTRY)
    private val FRUIT_TEA_ALLOWED = setOf(CAT_TEA_BASES, CAT_SWEET_SYRUPS, CAT_DRINK_PANTRY)
    private val CREMACHEE_ALLOWED = setOf(CAT_TEA_BASES, CAT_DAIRY, CAT_SWEET_SYRUPS, CAT_DRINK_PANTRY)
    private val COFFEE_ALLOWED = setOf(CAT_COFFEE_BASES, CAT_DAIRY, CAT_SWEET_SYRUPS, CAT_POWDERS, CAT_DRINK_PANTRY)
    private val NON_COFFEE_ALLOWED = setOf(CAT_POWDERS, CAT_DAIRY, CAT_SWEET_SYRUPS, CAT_DRINK_PANTRY)
    private val BREVE_ALLOWED = setOf(CAT_COFFEE_BASES, CAT_DAIRY, CAT_SWEET_SYRUPS, CAT_DRINK_PANTRY)
    private val FRAPPE_ALLOWED = setOf(CAT_COFFEE_BASES, CAT_DAIRY, CAT_SWEET_SYRUPS, CAT_POWDERS, CAT_DRINK_PANTRY)
    private val NON_COFFEE_FRAPPE_ALLOWED = setOf(CAT_POWDERS, CAT_DAIRY, CAT_SWEET_SYRUPS, CAT_DRINK_PANTRY)
    private val LEMONADE_ALLOWED = setOf(CAT_LEMONADE_BASES, CAT_SWEET_SYRUPS, CAT_DRINK_PANTRY)
    private val SMOOTHIE_ALLOWED = setOf(CAT_DRINK_PRODUCE, CAT_POWDERS, CAT_DAIRY, CAT_SWEET_SYRUPS, CAT_DRINK_PANTRY, CAT_BEVERAGES)
    private val PITCHER_ALLOWED = setOf(CAT_TEA_BASES, CAT_LEMONADE_BASES, CAT_SWEET_SYRUPS, CAT_DRINK_PANTRY, CAT_BEVERAGES)

    // Food
    private val BURGERS_ALLOWED = setOf(CAT_BAKERY, CAT_BURGER_PROTEINS, CAT_FOOD_PRODUCE, CAT_FOOD_DAIRY, CAT_SAVOURY_SAUCES)
    private val WINGS_ALLOWED = setOf(CAT_WING_PROTEINS, CAT_SAVOURY_SAUCES)
    private val RICE_MEAL_ALLOWED = setOf(CAT_RICE_MEAL_PROTEINS, CAT_BURGER_PROTEINS, CAT_FOOD_DAIRY, CAT_SAVOURY_SAUCES, CAT_FOOD_PANTRY, CAT_FOOD_PRODUCE)
    private val PASTA_ALLOWED = setOf(CAT_FOOD_PANTRY, CAT_BURGER_PROTEINS, CAT_FOOD_DAIRY, CAT_SAVOURY_SAUCES)
    private val SIDES_ALLOWED = setOf(CAT_FROZEN, CAT_SIDE_PROTEINS, CAT_SAVOURY_SAUCES, CAT_FOOD_DAIRY, CAT_BAKERY)

    private val allowedCategoriesByProductCategory: Map<String, Set<String>> = mapOf(
        "burgers" to BURGERS_ALLOWED,
        "milktea" to MILK_TEA_ALLOWED,
        "fruittea" to FRUIT_TEA_ALLOWED,
        "cremachee" to CREMACHEE_ALLOWED,
        "coffee" to COFFEE_ALLOWED,
        "noncoffee" to NON_COFFEE_ALLOWED,
        "brevecoffee" to BREVE_ALLOWED,
        "specialdrinks" to COFFEE_ALLOWED,
        "specialdrink" to COFFEE_ALLOWED,
        "coffeefrappes" to FRAPPE_ALLOWED,
        "coffeefrappe" to FRAPPE_ALLOWED,
        "noncoffeefrappes" to NON_COFFEE_FRAPPE_ALLOWED,
        "noncoffeefrappe" to NON_COFFEE_FRAPPE_ALLOWED,
        "frappes" to FRAPPE_ALLOWED,
        "frappe" to FRAPPE_ALLOWED,
        "frappuccino" to FRAPPE_ALLOWED,
        "lemonades" to LEMONADE_ALLOWED,
        "lemonade" to LEMONADE_ALLOWED,
        "smoothies" to SMOOTHIE_ALLOWED,
        "smoothie" to SMOOTHIE_ALLOWED,
        "wings" to WINGS_ALLOWED,
        "ricemeals" to RICE_MEAL_ALLOWED,
        "ricemeal" to RICE_MEAL_ALLOWED,
        "pasta" to PASTA_ALLOWED,
        "appetizersandsides" to SIDES_ALLOWED,
        "appetizers" to SIDES_ALLOWED,
        "sides" to SIDES_ALLOWED,
        "pitcherdrinks" to PITCHER_ALLOWED,
        "pitcher" to PITCHER_ALLOWED
    )

    /**
     * Returns the subset of [ingredients] whose category is in the
     * product category's allowlist, alphabetized. Combo Meals and any
     * brand-new product category fall through to "show everything" so
     * staff never sees an empty spinner.
     *
     * The caller is responsible for re-injecting any ingredients that
     * were saved on an existing recipe but don't match the allowlist —
     * see InventoryFragment.rebuildIngredientOptionsForCategory.
     */
    fun filterForProductCategory(
        productCategoryName: String?,
        ingredients: List<Ingredient>
    ): List<Ingredient> {
        if (ingredients.isEmpty()) return ingredients
        val allowed = allowedCategoriesByProductCategory[normalize(productCategoryName)]
            ?: return ingredients.sortedBy { it.name.lowercase(Locale.US) }
        val matching = ingredients.filter { it.category in allowed }
        // Fail open: if the allowlist somehow matches nothing in the
        // current ingredient inventory (e.g. migration hasn't been run
        // yet on this Supabase project), surface everything rather than
        // an empty spinner.
        return if (matching.isEmpty()) {
            ingredients.sortedBy { it.name.lowercase(Locale.US) }
        } else {
            matching.sortedBy { it.name.lowercase(Locale.US) }
        }
    }

    private fun normalize(value: String?): String {
        return value
            .orEmpty()
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]"), "")
    }
}
