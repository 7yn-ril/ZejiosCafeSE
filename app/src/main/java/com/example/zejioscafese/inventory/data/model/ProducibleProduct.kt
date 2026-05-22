package com.example.zejioscafese.inventory.data.model

data class ProducibleProduct(
    val id: String,
    val productId: String,
    val categoryId: String,
    val category: String,
    val productName: String,
    val variantName: String,
    val price: Double,
    val availableQuantity: Int,
    val imageUrl: String? = null,
    val isActive: Boolean = true
) {
    val name: String
        get() = when {
            variantName.equals("standard", ignoreCase = true) -> productName
            variantName.equals("combo", ignoreCase = true) -> productName
            else -> "$productName ($variantName)"
        }

    val estimatedValue: Double
        get() = price * availableQuantity
}
