package com.example.zejioscafese.inventory.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProductRecipeLinkDto(
    @SerialName("variant_ingredient_id")
    val variantIngredientId: String,
    @SerialName("product_variant_id")
    val productVariantId: String,
    @SerialName("ingredient_id")
    val ingredientId: String,
    @SerialName("required_quantity")
    val requiredQuantity: Double
)
