package com.example.zejioscafese.orders.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class CafeOrderTest {

    private fun order(
        orderType: String = "dine_in",
        paymentMethod: String = "cash"
    ) = CafeOrder(
        id = "#POS-1001",
        customerName = "Walk-in Customer",
        itemsSummary = "Latte x1",
        itemCount = 1,
        timeLabel = "10:00 AM",
        status = CafeOrderStatus.PREPARING,
        total = 95.0,
        initials = "WC",
        orderType = orderType,
        paymentMethod = paymentMethod
    )

    // ── isTakeout ─────────────────────────────────────────────────────────────

    @Test
    fun isTakeout_orderTypeIsTakeout_returnsTrue() =
        assertTrue(order(orderType = "takeout").isTakeout)

    @Test
    fun isTakeout_orderTypeIsDineIn_returnsFalse() =
        assertFalse(order(orderType = "dine_in").isTakeout)

    @Test
    fun isTakeout_orderTypeIsDelivery_returnsFalse() =
        assertFalse(order(orderType = "delivery").isTakeout)

    @Test
    fun isTakeout_orderTypeCaseInsensitive_returnsTrue() =
        assertTrue(order(orderType = "TAKEOUT").isTakeout)

    // ── isGcash ───────────────────────────────────────────────────────────────

    @Test
    fun isGcash_paymentMethodIsGcash_returnsTrue() =
        assertTrue(order(paymentMethod = "gcash").isGcash)

    @Test
    fun isGcash_paymentMethodIsCash_returnsFalse() =
        assertFalse(order(paymentMethod = "cash").isGcash)

    @Test
    fun isGcash_paymentMethodIsMaya_returnsFalse() =
        assertFalse(order(paymentMethod = "maya").isGcash)

    @ParameterizedTest
    @ValueSource(strings = ["GCASH", "GCash", "Gcash"])
    fun isGcash_paymentMethodCaseInsensitive_returnsTrue(method: String) =
        assertTrue(order(paymentMethod = method).isGcash)

    // ── CafeOrderStatus ───────────────────────────────────────────────────────

    @Test
    fun cafeOrderStatus_enumValues_areThreeExpectedValues() {
        val values = CafeOrderStatus.values()
        assertEquals(3, values.size)
        assertTrue(CafeOrderStatus.PENDING in values)
        assertTrue(CafeOrderStatus.PREPARING in values)
        assertTrue(CafeOrderStatus.COMPLETED in values)
    }
}
