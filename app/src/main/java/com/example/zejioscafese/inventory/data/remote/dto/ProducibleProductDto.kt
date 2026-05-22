package com.example.zejioscafese.inventory.data.remote.dto

import com.example.zejioscafese.inventory.data.model.ProducibleProduct
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProducibleProductDto(
    @SerialName("product_variant_id")
    val productVariantId: String,
    @SerialName("product_id")
    val productId: String,
    @SerialName("category_id")
    val categoryId: String,
    @SerialName("category_name")
    val categoryName: String,
    @SerialName("product_name")
    val productName: String,
    @SerialName("product_image_url")
    val productImageUrl: String? = null,
    @SerialName("variant_name")
    val variantName: String,
    @SerialName("variant_price")
    val variantPrice: Double,
    @SerialName("variant_stock_left")
    val variantStockLeft: Int,
    @SerialName("product_is_active")
    val productIsActive: Boolean,
    @SerialName("variant_is_active")
    val variantIsActive: Boolean
) {

    fun toProducibleProduct(): ProducibleProduct {
        return ProducibleProduct(
            id = productVariantId,
            productId = productId,
            categoryId = categoryId,
            category = categoryName,
            productName = productName,
            variantName = variantName,
            price = variantPrice,
            availableQuantity = variantStockLeft.coerceAtLeast(0),
            imageUrl = productImageUrl,
            isActive = productIsActive && variantIsActive
        )
    }
}
