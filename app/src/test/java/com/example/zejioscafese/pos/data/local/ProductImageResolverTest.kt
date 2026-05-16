package com.example.zejioscafese.pos.data.local

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ProductImageResolverTest {

    private val assetPrefix = "asset:///product_images/"

    // ── known products ────────────────────────────────────────────────────────

    @Test
    fun resolve_exactProductName_returnsAssetUrl() {
        val url = ProductImageResolver.resolve("Americano")
        assertNotNull(url)
        assertTrue(url!!.startsWith(assetPrefix))
        assertTrue(url.contains("Americano.png"))
    }

    @Test
    fun resolve_productNameWithSpaces_returnsUrl() {
        val url = ProductImageResolver.resolve("Banana Choco")
        assertNotNull(url)
        assertTrue(url!!.startsWith(assetPrefix))
    }

    @Test
    fun resolve_productNameAllUpperCase_returnsUrl() {
        val url = ProductImageResolver.resolve("AMERICANO")
        assertNotNull(url)
    }

    @Test
    fun resolve_productNameAllLowerCase_returnsUrl() {
        val url = ProductImageResolver.resolve("americano")
        assertNotNull(url)
    }

    @Test
    fun resolve_productNameWithSpecialChars_returnsUrl() {
        val url = ProductImageResolver.resolve("Biscoffee Latte!")
        assertNotNull(url)
    }

    @Test
    fun resolve_matchaLatte_returnsCorrectFile() {
        val url = ProductImageResolver.resolve("Matcha Latte")
        assertNotNull(url)
        assertTrue(url!!.contains("MatchaLatte.png"))
    }

    // ── unknown products ──────────────────────────────────────────────────────

    @Test
    fun resolve_unknownProductName_returnsNull() =
        assertNull(ProductImageResolver.resolve("XyZCompletelyUnknown999"))

    @Test
    fun resolve_emptyProductName_returnsNull() =
        assertNull(ProductImageResolver.resolve(""))

    @Test
    fun resolve_nullProductName_returnsNull() =
        assertNull(ProductImageResolver.resolve(null))

    @Test
    fun resolve_whitespaceOnlyName_returnsNull() =
        assertNull(ProductImageResolver.resolve("   "))

    // ── URL format ────────────────────────────────────────────────────────────

    @Test
    fun resolve_knownProduct_urlStartsWithAssetPrefix() {
        val url = ProductImageResolver.resolve("Latte")
        assertNotNull(url)
        assertTrue(url!!.startsWith(assetPrefix))
    }

    @Test
    fun resolve_whiteMochaFrappuccino_resolvesCorrectly() {
        val url = ProductImageResolver.resolve("White Mocha Frappuccino")
        assertNotNull(url)
    }

    @ParameterizedTest
    @ValueSource(strings = ["Yakult", "Milk", "Espresso Shot", "Dark Mocha"])
    fun resolve_sampledKnownProducts_allReturnNonNullUrls(name: String) =
        assertNotNull(ProductImageResolver.resolve(name))
}
