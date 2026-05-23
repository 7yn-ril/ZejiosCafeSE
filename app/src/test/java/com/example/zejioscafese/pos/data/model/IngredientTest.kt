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
        assertTrue(ingredient(unit = "mL").isLiquid)

    @Test
    fun isLiquid_unitMLUpperCase_returnsTrue() =
        assertTrue(ingredient(unit = "ML").isLiquid)

    @Test
    fun isLiquid_unitMlWithLeadingTrailingSpaces_returnsTrue() =
        assertTrue(ingredient(unit = "  ml  ").isLiquid)

    @ParameterizedTest
    @ValueSource(strings = ["L", "liter", "litre", "shot", "shots"])
    fun isLiquid_liquidMeasuredUnits_returnsTrue(unit: String) =
        assertTrue(ingredient(unit = unit).isLiquid)

    @ParameterizedTest
    @ValueSource(strings = ["g", "gram", "grams", "kg"])
    fun isLiquid_weightUnits_returnsFalse(unit: String) =
        // Weight units normalize to grams now, not mL. They're still
        // bulk-measured (see isBulk), but not liquids.
        assertFalse(ingredient(unit = unit).isLiquid)

    @ParameterizedTest
    @ValueSource(strings = ["pcs", "pc", "piece", "pieces", "bottle", "serving", "oz", ""])
    fun isLiquid_unitNotMl_returnsFalse(unit: String) =
        assertFalse(ingredient(unit = unit).isLiquid)

    // ── isBulk ────────────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = ["mL", "ML", "L", "shot", "g", "gram", "grams", "kg"])
    fun isBulk_measuredUnits_returnsTrue(unit: String) =
        assertTrue(ingredient(unit = unit).isBulk)

    @ParameterizedTest
    @ValueSource(strings = ["pcs", "pc", "piece", "bottle", "serving", ""])
    fun isBulk_pieceUnits_returnsFalse(unit: String) =
        assertFalse(ingredient(unit = unit).isBulk)

    @Test
    fun totalServings_gramsWithValidServingSize_returnsStockDividedByServing() {
        // Grams now go through the same per-serving math as mL —
        // 500g of tapioca pearls at 50g per drink = 10 servings.
        val ing = ingredient(unit = "g", currentStock = 500.0, mlPerServing = 50.0)
        assertEquals(10.0, ing.totalServings)
    }

    // ── totalServings ─────────────────────────────────────────────────────────

    @Test
    fun totalServings_liquidWithValidMlPerServing_returnsStockDividedByServing() {
        val ing = ingredient(unit = "mL", currentStock = 500.0, mlPerServing = 50.0)
        assertEquals(10.0, ing.totalServings)
    }

    @Test
    fun totalServings_liquidWithZeroCurrentStock_returnsZero() {
        val ing = ingredient(unit = "mL", currentStock = 0.0, mlPerServing = 25.0)
        assertEquals(0.0, ing.totalServings)
    }

    @Test
    fun totalServings_liquidWithNullMlPerServing_returnsNull() {
        assertNull(ingredient(unit = "mL", currentStock = 500.0, mlPerServing = null).totalServings)
    }

    @Test
    fun totalServings_liquidWithZeroMlPerServing_returnsNull() {
        assertNull(ingredient(unit = "mL", currentStock = 500.0, mlPerServing = 0.0).totalServings)
    }

    @Test
    fun totalServings_notLiquidEvenWithMlPerServingSet_returnsNull() {
        assertNull(ingredient(unit = "pcs", currentStock = 50.0, mlPerServing = 10.0).totalServings)
    }

    @Test
    fun totalServings_fractionalResult_returnsCorrectValue() {
        val ing = ingredient(unit = "mL", currentStock = 100.0, mlPerServing = 30.0)
        assertEquals(100.0 / 30.0, ing.totalServings!!, 1e-9)
    }

    // ── costPerServing ────────────────────────────────────────────────────────

    @Test
    fun costPerServing_liquidWithValidValues_returnsCostPerUnitTimesServingSize() {
        val ing = ingredient(unit = "mL", costPerUnit = 0.10, mlPerServing = 30.0)
        assertEquals(3.0, ing.costPerServing!!, 1e-9)
    }

    @Test
    fun costPerServing_notLiquid_returnsNull() {
        assertNull(ingredient(unit = "pcs", costPerUnit = 10.0, mlPerServing = 30.0).costPerServing)
    }

    @Test
    fun costPerServing_liquidWithNullMlPerServing_returnsNull() {
        assertNull(ingredient(unit = "mL", costPerUnit = 5.0, mlPerServing = null).costPerServing)
    }

    @Test
    fun costPerServing_liquidWithZeroMlPerServing_returnsNull() {
        assertNull(ingredient(unit = "mL", costPerUnit = 5.0, mlPerServing = 0.0).costPerServing)
    }

    @Test
    fun costPerServing_liquidWithZeroCostPerUnit_returnsZero() {
        val ing = ingredient(unit = "mL", costPerUnit = 0.0, mlPerServing = 20.0)
        assertEquals(0.0, ing.costPerServing!!, 1e-9)
    }

    // stockStatus

    @Test
    fun stockStatus_zeroStock_returnsNoStock() {
        assertEquals(
            IngredientStockStatus.NO_STOCK,
            ingredient(currentStock = 0.0, minimumStock = 10.0).stockStatus
        )
    }

    @Test
    fun stockStatus_equalToMinimum_returnsLowStock() {
        assertEquals(
            IngredientStockStatus.LOW_STOCK,
            ingredient(currentStock = 1.0, minimumStock = 1.0).stockStatus
        )
    }

    @Test
    fun stockStatus_belowMinimum_returnsLowStock() {
        assertEquals(
            IngredientStockStatus.LOW_STOCK,
            ingredient(currentStock = 5.0, minimumStock = 10.0).stockStatus
        )
    }

    @Test
    fun stockStatus_aboveMinimum_returnsInStock() {
        assertEquals(
            IngredientStockStatus.IN_STOCK,
            ingredient(currentStock = 11.0, minimumStock = 10.0).stockStatus
        )
    }
}
