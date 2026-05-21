package com.example.zejioscafese.pos.data.model

import androidx.annotation.DrawableRes
import java.util.Locale

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
    val isOrderable: Boolean get() = totalStock > 0
    val unavailableReason: String? get() = variants
        .mapNotNull(Product::unavailableReason)
        .distinct()
        .joinToString(separator = "\n")
        .takeIf(String::isNotBlank)
    val requiresVariantPicker: Boolean get() = variants.size > 1 || category.requiresDrinkSizePicker()
    val hasMultipleVariants: Boolean get() = requiresVariantPicker
    val singleVariant: Product? get() = variants.singleOrNull().takeUnless { requiresVariantPicker }

    private fun String.requiresDrinkSizePicker(): Boolean {
        return normalizedCategory() in DRINK_SIZE_PICKER_CATEGORIES
    }

    private fun String.normalizedCategory(): String {
        return trim()
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }

    private companion object {
        val DRINK_SIZE_PICKER_CATEGORIES = setOf(
            "special drinks",
            "breve coffee",
            "non coffee",
            "coffee"
        )
    }
}
