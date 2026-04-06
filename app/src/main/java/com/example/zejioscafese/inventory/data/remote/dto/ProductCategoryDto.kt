package com.example.zejioscafese.inventory.data.remote.dto

import com.example.zejioscafese.inventory.data.model.ProductCategoryOption
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProductCategoryDto(
    @SerialName("category_id")
    val categoryId: String,
    @SerialName("category_name")
    val categoryName: String,
    @SerialName("category_is_active")
    val categoryIsActive: Boolean = true
) {
    fun toCategoryOption(): ProductCategoryOption {
        return ProductCategoryOption(
            id = categoryId,
            name = categoryName
        )
    }
}
