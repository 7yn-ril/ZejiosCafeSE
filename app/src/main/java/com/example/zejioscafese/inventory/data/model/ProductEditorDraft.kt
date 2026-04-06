package com.example.zejioscafese.inventory.data.model

data class ProductEditorDraft(
    val productId: String? = null,
    val productVariantId: String? = null,
    val categoryId: String,
    val productName: String,
    val variantName: String,
    val price: Double,
    val ingredients: List<ProductRecipeIngredient>
)
