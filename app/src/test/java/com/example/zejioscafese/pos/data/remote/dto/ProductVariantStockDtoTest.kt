package com.example.zejioscafese.pos.data.remote.dto

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ProductVariantStockDtoTest {

    private fun dto(
        variantName: String = "Standard",
        productName: String = "Latte",
        stockLeft: Int = 5,
        imageUrl: String? = null
    ) = ProductVariantStockDto(
        productVariantId = "VAR-001",
        productId = "PRD-001",
        categoryId = "CAT-001",
        categoryName = "Coffee",
        productName = productName,
        variantName = variantName,
        variantPrice = 100.0,
        variantStockLeft = stockLeft,
        productIsActive = true,
        variantIsActive = true,
        productImageUrl = imageUrl
    )

    // ── displayName (via toProduct().name) ────────────────────────────────────

    @Test
    fun toProduct_standardVariant_displayNameEqualsProductName() =
        assertEquals("Latte", dto(variantName = "Standard").toProduct().name)

    @Test
    fun toProduct_standardVariantLowerCase_displayNameEqualsProductName() =
        assertEquals("Latte", dto(variantName = "standard").toProduct().name)

    @Test
    fun toProduct_comboVariant_displayNameEqualsProductName() =
        assertEquals("Latte", dto(variantName = "Combo").toProduct().name)

    @Test
    fun toProduct_comboVariantUpperCase_displayNameEqualsProductName() =
        assertEquals("Latte", dto(variantName = "COMBO").toProduct().name)

    @ParameterizedTest
    @ValueSource(strings = ["Iced", "Hot", "Large", "Small"])
    fun toProduct_namedVariant_displayNameContainsParenthesisedVariant(variant: String) {
        val product = dto(variantName = variant, productName = "Latte").toProduct()
        assertEquals("Latte ($variant)", product.name)
    }

    // ── stockLeft clamping ────────────────────────────────────────────────────

    @Test
    fun toProduct_negativeStock_clampsToZero() =
        assertEquals(0, dto(stockLeft = -10).toProduct().stockLeft)

    @Test
    fun toProduct_zeroStock_remainsZero() =
        assertEquals(0, dto(stockLeft = 0).toProduct().stockLeft)

    @Test
    fun toProduct_positiveStock_preserved() =
        assertEquals(7, dto(stockLeft = 7).toProduct().stockLeft)

    // ── source fields ─────────────────────────────────────────────────────────

    @Test
    fun toProduct_sourceProductIdMappedCorrectly() =
        assertEquals("PRD-001", dto().toProduct().sourceProductId)

    @Test
    fun toProduct_sourceProductNameMappedCorrectly() =
        assertEquals("Latte", dto(productName = "Latte").toProduct().sourceProductName)

    @Test
    fun toProduct_sourceVariantNameMappedCorrectly() =
        assertEquals("Iced", dto(variantName = "Iced").toProduct().sourceVariantName)

    @Test
    fun toProduct_imageUrlNullWhenDtoHasNoImageUrl() =
        assertNull(dto(imageUrl = null).toProduct().imageUrl)

    @Test
    fun toProduct_imageUrlPreservedWhenPresent() =
        assertEquals("https://img.test/latte.png", dto(imageUrl = "https://img.test/latte.png").toProduct().imageUrl)

    @Test
    fun toProduct_idIsProductVariantId() =
        assertEquals("VAR-001", dto().toProduct().id)
}
