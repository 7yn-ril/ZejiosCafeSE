package com.example.zejioscafese.pos.data.model

import java.util.Locale

object IngredientUnits {
    const val PCS = "pcs"
    const val ML = "mL"
    const val G = "g"

    // Order matters: the unit spinner in the Add/Edit Ingredient dialog
    // shows them in this order. PCS first since it's the most-used.
    val options: List<String> = listOf(PCS, ML, G)

    fun normalize(unit: String): String {
        return when (unit.trim().lowercase(Locale.US)) {
            "ml",
            "milliliter",
            "milliliters",
            "millilitre",
            "millilitres",
            "l",
            "liter",
            "liters",
            "litre",
            "litres",
            "shot",
            "shots" -> ML

            // Weight units now resolve to grams rather than collapsing
            // into mL. Note: we don't convert kg→g numerically — staff
            // should enter the stock value in the unit they pick.
            "g",
            "gram",
            "grams",
            "kg",
            "kilogram",
            "kilograms" -> G

            "pc",
            "pcs",
            "piece",
            "pieces",
            "bottle",
            "bottles",
            "serving",
            "servings" -> PCS

            else -> PCS
        }
    }

    fun isMl(unit: String): Boolean {
        return normalize(unit) == ML
    }

    /**
     * True for unit kinds that are measured in bulk-by-quantity (mL or
     * grams). Both types use the same per-serving / per-restock-container
     * math in the inventory editor — only PCS is special-cased as
     * discrete count. Use this anywhere the math is "stock divided by
     * serving size", not anywhere the ingredient literally has to be a
     * liquid.
     */
    fun isBulk(unit: String): Boolean {
        val normalized = normalize(unit)
        return normalized == ML || normalized == G
    }

    // Default unit per ingredient category. Used by the Add Ingredient
    // dialog to pre-select a sensible unit when staff picks a category,
    // so they don't have to think about it for the common case (a patty
    // is pcs, matcha powder is g, a syrup is mL). Staff can still
    // override the unit manually — this is just a UX nudge.
    //
    // Includes the legacy pre-split category names too so the dialog
    // works even on a Supabase project that hasn't run
    // split_ingredient_categories.sql yet.
    private val DEFAULT_UNIT_BY_CATEGORY = mapOf(
        // Drinks-side (post-split)
        "coffeebases" to ML,
        "teabases" to ML,
        "lemonadebases" to ML,
        "sweetsyrups" to ML,
        "powdersandmixes" to G,
        "dairy" to ML,
        "drinkproduce" to PCS,
        "drinkpantry" to ML,
        "beverages" to PCS,
        // Food-side (post-split)
        "bakeryandbread" to PCS,
        "burgerproteins" to PCS,
        "wingproteins" to PCS,
        "ricemealproteins" to PCS,
        "sideproteins" to PCS,
        "foodproduce" to PCS,
        "fooddairy" to PCS,
        "foodpantry" to G,
        "savourysauces" to ML,
        "frozenandsides" to G,
        // Legacy / pre-migration categories
        "coffeeandteabases" to ML,
        "syrupsandsauces" to ML,
        "pantry" to PCS,
        "produce" to PCS,
        "proteins" to PCS
    )

    fun defaultUnitForCategory(category: String?): String {
        val key = category.orEmpty().lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
        return DEFAULT_UNIT_BY_CATEGORY[key] ?: PCS
    }
}
