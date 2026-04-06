package com.example.zejioscafese.inventory.data.model

data class ProducibleProduct(
    val id: String,
    val name: String,
    val category: String,
    val price: Double,
    val availableQuantity: Int
) {
    val estimatedValue: Double
        get() = price * availableQuantity
}
