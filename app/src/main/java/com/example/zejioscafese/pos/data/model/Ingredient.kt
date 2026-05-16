package com.example.zejioscafese.pos.data.model

data class Ingredient(
    val id: String,
    val name: String,
    val category: String,          // e.g. "Dairy", "Produce", "Dry Goods"
    val unit: String,              // e.g. "kg", "L", "pcs", "g"
    val currentStock: Double,
    val minimumStock: Double,      // threshold for low-stock warning
    val costPerUnit: Double,       // in PHP; for ml items this is normalized PHP/ml
    val lastRestocked: String,     // date string e.g. "2025-04-01"
    // Liquid (ml) ingredients only. mlPerServing is how many ml a single
    // serving consumes (e.g. 5 ml of syrup per drink). mlPerBottle is the
    // default size of a single restock unit (e.g. 200 ml per bottle), used
    // by the restock dialog so staff can enter "3 bottles" instead of raw ml.
    val mlPerServing: Double? = null,
    val mlPerBottle: Double? = null
) {
    // True for ingredients measured in millilitres (ml). The serving math
    // only applies to these.
    val isLiquid: Boolean
        get() = unit.trim().equals("ml", ignoreCase = true)

    // Total servings the current stock can cover. Returns null when
    // mlPerServing isn't configured or the ingredient isn't liquid.
    val totalServings: Double?
        get() {
            val servingSize = mlPerServing?.takeIf { it > 0.0 } ?: return null
            return if (isLiquid) currentStock / servingSize else null
        }

    // PHP cost of a single serving (cost per ml × ml per serving).
    val costPerServing: Double?
        get() {
            val servingSize = mlPerServing?.takeIf { it > 0.0 } ?: return null
            return if (isLiquid) costPerUnit * servingSize else null
        }
}
