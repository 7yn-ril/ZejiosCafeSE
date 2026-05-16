package com.example.zejioscafese.pos.data.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class IngredientTest {

    private fun ingredient(
        unit: String = "pcs",
        currentStock: Double = 100.0,
        minimumStock: Double = 10.0,
        costPerUnit: Double = 5.0,
        mlPerServing: Double? = null,
        mlPerBottle: Double? = null
    ) = Ingredient(
        id = "ING-001",
        name = "Test Ingredient",
        category = "Pantry",
        unit = unit,
        currentStock = currentStock,
        minimumStock = minimumStock,
        costPerUnit = costPerUnit,
        lastRestocked = "2025-01-01",
        mlPerServing = mlPerServing,
        mlPerBottle = mlPerBottle
    )

    // ── isLiquid ──────────────────────────────────────────────────────────────

    @Test
    fun isLiquid_unitMl_returnsTrue() =
        assertTrue(ingredient(unit = "ml").isLiquid)

    @Test
    fun isLiquid_unitMLUpperCase_returnsTrue() =
        assertTrue(ingredient(unit = "ML").isLiquid)

    @Test
    fun isLiquid_unitMlWithLeadingTrailingSpaces_returnsTrue() =
        assertTrue(ingredient(unit = "  ml  ").isLiquid)

    @ParameterizedTest
    @ValueSource(strings = ["pcs", "kg", "L", "g", "oz", ""])
    fun isLiquid_unitNotMl_returnsFalse(unit: String) =
        assertFalse(ingredient(unit = unit).isLiquid)

    // ── totalServings ─────────────────────────────────────────────────────────

    @Test
    fun totalServings_liquidWithValidMlPerServing_returnsStockDividedByServing() {
        val ing = ingredient(unit = "ml", currentStock = 500.0, mlPerServing = 50.0)
        assertEquals(10.0, ing.totalServings)
    }

    @Test
    fun totalServings_liquidWithZeroCurrentStock_returnsZero() {
        val ing = ingredient(unit = "ml", currentStock = 0.0, mlPerServing = 25.0)
        assertEquals(0.0, ing.totalServings)
    }

    @Test
    fun totalServings_liquidWithNullMlPerServing_returnsNull() {
        assertNull(ingredient(unit = "ml", currentStock = 500.0, mlPerServing = null).totalServings)
    }

    @Test
    fun totalServings_liquidWithZeroMlPerServing_returnsNull() {
        assertNull(ingredient(unit = "ml", currentStock = 500.0, mlPerServing = 0.0).totalServings)
    }

    @Test
    fun totalServings_notLiquidEvenWithMlPerServingSet_returnsNull() {
        assertNull(ingredient(unit = "pcs", currentStock = 50.0, mlPerServing = 10.0).totalServings)
    }

    @Test
    fun totalServings_fractionalResult_returnsCorrectValue() {
        val ing = ingredient(unit = "ml", currentStock = 100.0, mlPerServing = 30.0)
        assertEquals(100.0 / 30.0, ing.totalServings!!, 1e-9)
    }

    // ── costPerServing ────────────────────────────────────────────────────────

    @Test
    fun costPerServing_liquidWithValidValues_returnsCostPerUnitTimesServingSize() {
        val ing = ingredient(unit = "ml", costPerUnit = 0.10, mlPerServing = 30.0)
        assertEquals(3.0, ing.costPerServing!!, 1e-9)
    }

    @Test
    fun costPerServing_notLiquid_returnsNull() {
        assertNull(ingredient(unit = "pcs", costPerUnit = 10.0, mlPerServing = 30.0).costPerServing)
    }

    @Test
    fun costPerServing_liquidWithNullMlPerServing_returnsNull() {
        assertNull(ingredient(unit = "ml", costPerUnit = 5.0, mlPerServing = null).costPerServing)
    }

    @Test
    fun costPerServing_liquidWithZeroMlPerServing_returnsNull() {
        assertNull(ingredient(unit = "ml", costPerUnit = 5.0, mlPerServing = 0.0).costPerServing)
    }

    @Test
    fun costPerServing_liquidWithZeroCostPerUnit_returnsZero() {
        val ing = ingredient(unit = "ml", costPerUnit = 0.0, mlPerServing = 20.0)
        assertEquals(0.0, ing.costPerServing!!, 1e-9)
    }
}
