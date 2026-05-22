package com.example.zejioscafese.pos.data.model

import androidx.annotation.DrawableRes

data class Product(
    val id: String,
    val name: String,
    val category: String,
    val price: Double,
    val stockLeft: Int,
    val sourceProductId: String? = null,
    val sourceProductName: String? = null,
    val sourceVariantName: String? = null,
    val unavailableReason: String? = null,
    val recipeIngredients: List<ProductRecipeRequirement> = emptyList(),
    val imageUrl: String? = null,
    @DrawableRes val imageResId: Int = android.R.drawable.ic_menu_gallery
) {
    val isOrderable: Boolean get() = stockLeft > 0
}

data class ProductRecipeRequirement(
    val ingredientId: String,
    val ingredientName: String,
    val ingredientUnit: String,
    val requiredQuantity: Double,
    val currentStock: Double
)
