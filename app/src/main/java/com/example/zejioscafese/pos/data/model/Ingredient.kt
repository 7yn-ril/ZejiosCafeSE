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

    // True for ingredients measured in mL. Retained for any UI that
    // specifically wants liquids (e.g. a "Pour ingredient" workflow).
    val isLiquid: Boolean
        get() = IngredientUnits.isMl(unit)

    // True for ingredients measured by bulk quantity (mL or grams).
    // The per-serving / per-restock math applies to both — only pcs
    // ingredients skip it.
    val isBulk: Boolean
        get() = IngredientUnits.isBulk(unit)

    // Total servings the current stock can cover. Returns null when
    // mlPerServing isn't configured or the ingredient isn't bulk.
    val totalServings: Double?
        get() {
            val servingSize = mlPerServing?.takeIf { it > 0.0 } ?: return null
            return if (isBulk) currentStock / servingSize else null
        }

    // PHP cost of a single serving.
    val costPerServing: Double?
        get() {
            val servingSize = mlPerServing?.takeIf { it > 0.0 } ?: return null
            return if (isBulk) costPerUnit * servingSize else null
        }
}

enum class IngredientStockStatus {
    IN_STOCK,
    LOW_STOCK,
    NO_STOCK
}
