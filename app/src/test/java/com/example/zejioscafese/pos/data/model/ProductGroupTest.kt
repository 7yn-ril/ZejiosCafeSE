package com.example.zejioscafese.pos.data.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProductGroupTest {

    private fun product(
        id: String = "VAR-001",
        category: String = "Coffee",
        variantName: String = "Standard",
        stockLeft: Int = 10,
        unavailableReason: String? = null
    ) = Product(
        id = id,
        name = "Americano",
        category = category,
        price = 90.0,
        stockLeft = stockLeft,
        sourceProductId = "PRD-001",
        sourceProductName = "Americano",
        sourceVariantName = variantName,
        unavailableReason = unavailableReason
    )

    private fun group(
        category: String,
        variants: List<Product> = listOf(product(category = category))
    ) = ProductGroup(
        groupId = "PRD-001",
        displayName = "Americano",
        category = category,
        variants = variants,
        imageUrl = null
    )

    @Test
    fun singleCoffeeVariant_requiresSizePicker() {
        val group = group(category = "Coffee")

        assertTrue(group.requiresVariantPicker)
        assertTrue(group.hasMultipleVariants)
        assertNull(group.singleVariant)
    }

    @Test
    fun singleNamedDrinkCategories_requireSizePicker() {
        listOf("Special Drinks", "Breve Coffee", "Non-Coffee").forEach { category ->
            val group = group(category = category)

            assertTrue(group.requiresVariantPicker, "$category should open the size picker")
            assertNull(group.singleVariant, "$category should not add directly")
        }
    }

    @Test
    fun singleFoodVariant_canAddDirectly() {
        val variant = product(category = "Burgers")
        val group = group(category = "Burgers", variants = listOf(variant))

        assertFalse(group.requiresVariantPicker)
        assertFalse(group.hasMultipleVariants)
        assertSame(variant, group.singleVariant)
    }

    @Test
    fun multipleFoodVariants_stillRequirePicker() {
        val group = group(
            category = "Burgers",
            variants = listOf(
                product(id = "VAR-001", category = "Burgers", variantName = "Ala Carte"),
                product(id = "VAR-002", category = "Burgers", variantName = "Combo")
            )
        )

        assertTrue(group.requiresVariantPicker)
        assertTrue(group.hasMultipleVariants)
        assertNull(group.singleVariant)
    }

    @Test
    fun unavailableGroup_exposesDistinctReasons() {
        val group = group(
            category = "Burgers",
            variants = listOf(
                product(
                    id = "VAR-001",
                    category = "Burgers",
                    stockLeft = 0,
                    unavailableReason = "Beef Patty is out of stock."
                ),
                product(
                    id = "VAR-002",
                    category = "Burgers",
                    stockLeft = 0,
                    unavailableReason = "Beef Patty is out of stock."
                )
            )
        )

        assertFalse(group.isOrderable)
        assertEquals("Beef Patty is out of stock.", group.unavailableReason)
    }
}
