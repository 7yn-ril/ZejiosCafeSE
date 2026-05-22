package com.example.zejioscafese.pos.data.model

data class Ingredient(
    val id: String,
    val name: String,
    val category: String,          // e.g. "Dairy", "Produce", "Dry Goods"
    val unit: String,              // "pcs" or "mL"
    val currentStock: Double,
    val minimumStock: Double,      // threshold for low-stock warning
    val costPerUnit: Double,       // in PHP; for mL items this is normalized PHP/mL
    val lastRestocked: String,     // date string e.g. "2025-04-01"
    // mL ingredients only. mlPerServing is how much a single serving consumes.
    // mlPerBottle is the default size of one restock container.
    val mlPerServing: Double? = null,
    val mlPerBottle: Double? = null
) {
    val displayUnit: String
        get() = IngredientUnits.normalize(unit)

    val stockStatus: IngredientStockStatus
        get() = when {
            currentStock <= 0.0 -> IngredientStockStatus.NO_STOCK
            minimumStock > 0.0 && currentStock <= minimumStock -> IngredientStockStatus.LOW_STOCK
            else -> IngredientStockStatus.IN_STOCK
        }

    // True for ingredients measured in mL. The serving math only applies to these.
    val isLiquid: Boolean
        get() = IngredientUnits.isMl(unit)

    // Total servings the current stock can cover. Returns null when
    // mlPerServing isn't configured or the ingredient isn't liquid.
    val totalServings: Double?
        get() {
            val servingSize = mlPerServing?.takeIf { it > 0.0 } ?: return null
            return if (isLiquid) currentStock / servingSize else null
        }

    // PHP cost of a single serving.
    val costPerServing: Double?
        get() {
            val servingSize = mlPerServing?.takeIf { it > 0.0 } ?: return null
            return if (isLiquid) costPerUnit * servingSize else null
        }
}

enum class IngredientStockStatus {
    IN_STOCK,
    LOW_STOCK,
    NO_STOCK
}
