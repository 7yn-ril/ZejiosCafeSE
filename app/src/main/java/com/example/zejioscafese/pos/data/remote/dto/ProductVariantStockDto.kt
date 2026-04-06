package com.example.zejioscafese.pos.data.remote.dto

import com.example.zejioscafese.pos.data.model.Product
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProductVariantStockDto(
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
    @SerialName("product_series")
    val productSeries: String? = null,
    @SerialName("product_description")
    val productDescription: String? = null,
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

    fun toProduct(): Product {
        return Product(
            id = productVariantId,
            name = displayName(),
            category = categoryName,
            price = variantPrice,
            stockLeft = variantStockLeft.coerceAtLeast(0),
            sourceProductId = productId,
            sourceProductName = productName,
            sourceVariantName = variantName,
            imageUrl = productImageUrl
        )
    }

    private fun displayName(): String {
        return when {
            variantName.equals("standard", ignoreCase = true) -> productName
            variantName.equals("combo", ignoreCase = true) -> productName
            else -> "$productName ($variantName)"
        }
    }
}
