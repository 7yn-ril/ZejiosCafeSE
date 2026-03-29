package com.example.zejioscafese.pos.data.model

data class OrderItem(
    val product: Product,
    val quantity: Int
) {
    val lineTotal: Double
        get() = product.price * quantity
}

