package com.example.zejioscafese.pos.data.model

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DiscountTest {

    private fun discount(
        percent: Double = 20.0,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
        isBuiltIn: Boolean = false
    ) = Discount(
        id = "DSC-TEST",
        name = "Test",
        percent = percent,
        startDate = startDate,
        endDate = endDate,
        isBuiltIn = isBuiltIn
    )

    // ── isApplicableOn ────────────────────────────────────────────────────────

    @Test
    fun isApplicableOn_noBounds_alwaysTrue() {
        val d = discount()
        assertTrue(d.isApplicableOn(LocalDate.of(2024, 1, 1)))
        assertTrue(d.isApplicableOn(LocalDate.of(2099, 12, 31)))
    }

    @Test
    fun isApplicableOn_beforeStartDate_returnsFalse() {
        val d = discount(startDate = LocalDate.of(2026, 6, 1))
        assertFalse(d.isApplicableOn(LocalDate.of(2026, 5, 31)))
    }

    @Test
    fun isApplicableOn_onStartDate_returnsTrue() {
        val start = LocalDate.of(2026, 6, 1)
        assertTrue(discount(startDate = start).isApplicableOn(start))
    }

    @Test
    fun isApplicableOn_afterEndDate_returnsFalse() {
        val d = discount(endDate = LocalDate.of(2026, 6, 30))
        assertFalse(d.isApplicableOn(LocalDate.of(2026, 7, 1)))
    }

    @Test
    fun isApplicableOn_onEndDate_returnsTrue() {
        val end = LocalDate.of(2026, 6, 30)
        assertTrue(discount(endDate = end).isApplicableOn(end))
    }

    @Test
    fun isApplicableOn_insideRange_returnsTrue() {
        val d = discount(
            startDate = LocalDate.of(2026, 6, 1),
            endDate = LocalDate.of(2026, 6, 30)
        )
        assertTrue(d.isApplicableOn(LocalDate.of(2026, 6, 15)))
    }

    // ── amountFor ─────────────────────────────────────────────────────────────

    @Test
    fun amountFor_validSubtotal_returnsCorrectPercentage() {
        assertEquals(20.0, discount(percent = 20.0).amountFor(100.0), 1e-9)
    }

    @Test
    fun amountFor_zeroPercent_returnsZero() {
        assertEquals(0.0, discount(percent = 0.0).amountFor(100.0))
    }

    @Test
    fun amountFor_zeroSubtotal_returnsZero() {
        assertEquals(0.0, discount(percent = 20.0).amountFor(0.0))
    }

    @Test
    fun amountFor_negativeSubtotal_returnsZero() {
        assertEquals(0.0, discount(percent = 20.0).amountFor(-50.0))
    }

    @Test
    fun amountFor_oneHundredPercent_cappedAtSubtotal() {
        assertEquals(100.0, discount(percent = 100.0).amountFor(100.0))
    }

    @Test
    fun amountFor_fractionalPercent_returnsCorrectAmount() {
        assertEquals(12.5, discount(percent = 12.5).amountFor(100.0), 1e-9)
    }

    // ── displayLabel ──────────────────────────────────────────────────────────

    @Test
    fun displayLabel_integerPercent_dropsDecimal() =
        assertEquals("Test (20%)", discount(percent = 20.0).displayLabel())

    @Test
    fun displayLabel_fractionalPercent_keepsOneDecimal() =
        assertEquals("Test (12.5%)", discount(percent = 12.5).displayLabel())
}
