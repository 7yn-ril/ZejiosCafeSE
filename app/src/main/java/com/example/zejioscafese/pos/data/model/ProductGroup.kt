package com.example.zejioscafese.pos.data.model

import androidx.annotation.DrawableRes

data class ProductGroup(
    val groupId: String,
    val displayName: String,
    val category: String,
    val variants: List<Product>,
    val imageUrl: String?,
    @DrawableRes val imageResId: Int = android.R.drawable.ic_menu_gallery
) {
    val totalStock: Int get() = variants.sumOf { it.stockLeft.coerceAtLeast(0) }
    val minPrice: Double get() = variants.minOf { it.price }
    val maxPrice: Double get() = variants.maxOf { it.price }
    val hasMultipleVariants: Boolean get() = variants.size > 1
    val singleVariant: Product? get() = variants.singleOrNull()
}
