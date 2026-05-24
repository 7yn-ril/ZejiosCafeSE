package com.example.zejioscafese.orders.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class CafeOrderTest {

    private fun order(
        orderType: String = "dine_in",
        paymentMethod: String = "cash",
        paymentProvider: String? = null
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
        paymentMethod = paymentMethod,
        paymentProvider = paymentProvider
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

    // ── isPayMongo ────────────────────────────────────────────────────────────
    // isPayMongo now keys off paymentProvider (the gateway tag) instead
    // of paymentMethod, so a GCash order routed through PayMongo and a
    // raw-cash order with provider=null both behave correctly regardless
    // of the underlying instrument.

    @Test
    fun isPayMongo_paymentProviderIsPaymongo_returnsTrue() =
        assertTrue(order(paymentMethod = "gcash", paymentProvider = "paymongo").isPayMongo)

    @Test
    fun isPayMongo_paymentProviderIsNull_returnsFalse() =
        assertFalse(order(paymentMethod = "cash", paymentProvider = null).isPayMongo)

    @Test
    fun isPayMongo_methodIsGcashButProviderNull_returnsFalse() =
        assertFalse(order(paymentMethod = "gcash", paymentProvider = null).isPayMongo)

    @ParameterizedTest
    @ValueSource(strings = ["PAYMONGO", "PayMongo", "paymongo", "Paymongo"])
    fun isPayMongo_paymentProviderCaseInsensitive_returnsTrue(provider: String) =
        assertTrue(order(paymentMethod = "card", paymentProvider = provider).isPayMongo)

    // ── CafeOrderStatus ───────────────────────────────────────────────────────

    @Test
    fun cafeOrderStatus_enumValues_areFourExpectedValues() {
        val values = CafeOrderStatus.values()
        assertEquals(4, values.size)
        assertTrue(CafeOrderStatus.PENDING in values)
        assertTrue(CafeOrderStatus.PREPARING in values)
        assertTrue(CafeOrderStatus.COMPLETED in values)
        assertTrue(CafeOrderStatus.CANCELLED in values)
    }
}
