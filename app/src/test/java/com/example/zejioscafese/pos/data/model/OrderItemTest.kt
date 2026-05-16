package com.example.zejioscafese.pos.data.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OrderItemTest {

    private fun product(price: Double, stock: Int = 10) = Product(
        id = "VAR-001",
        name = "Latte",
        category = "Coffee",
        price = price,
        stockLeft = stock
    )

    @Test
    fun lineTotal_singleQuantity_equalsProductPrice() {
        val item = OrderItem(product = product(95.0), quantity = 1)
        assertEquals(95.0, item.lineTotal)
    }

    @Test
    fun lineTotal_multipleQuantity_returnsPriceTimesQuantity() {
        val item = OrderItem(product = product(95.0), quantity = 3)
        assertEquals(285.0, item.lineTotal)
    }

    @Test
    fun lineTotal_zeroPriceProduct_returnsZero() {
        val item = OrderItem(product = product(0.0), quantity = 5)
        assertEquals(0.0, item.lineTotal)
    }

    @Test
    fun lineTotal_fractionalPrice_computesCorrectly() {
        val item = OrderItem(product = product(10.50), quantity = 2)
        assertEquals(21.0, item.lineTotal, 1e-9)
    }

    @Test
    fun lineTotal_largeQuantity_doesNotOverflow() {
        val item = OrderItem(product = product(999.99), quantity = 1000)
        assertEquals(999990.0, item.lineTotal, 1e-6)
    }
}
