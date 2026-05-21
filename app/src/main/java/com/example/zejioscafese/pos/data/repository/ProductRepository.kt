package com.example.zejioscafese.pos.data.repository

import com.example.zejioscafese.core.supabase.SupabaseProvider
import com.example.zejioscafese.core.supabase.SupabaseSessionHelper
import com.example.zejioscafese.pos.data.local.ProductImageResolver
import com.example.zejioscafese.pos.data.model.Product
import com.example.zejioscafese.pos.data.remote.dto.ProductVariantStockDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class ProductRepository(
    private val clientProvider: () -> SupabaseClient = { SupabaseProvider.client }
) {

    private val supabaseClient: SupabaseClient
        get() = clientProvider()

    suspend fun fetchProducts(): List<Product> {
        return withContext(Dispatchers.IO) {
            SupabaseSessionHelper.withJwtRetry(supabaseClient) {
                val products = supabaseClient
                    .from("product_variant_stock_view")
                    .select {
                        order(column = "category_name", order = Order.ASCENDING)
                        order(column = "product_name", order = Order.ASCENDING)
                        order(column = "variant_name", order = Order.ASCENDING)
                    }
                    .decodeList<ProductVariantStockDto>()
                    .asSequence()
                    .filter { it.productIsActive && it.variantIsActive }
                    .map(ProductVariantStockDto::toProduct)
                    .toList()

                val unavailableReasons = runCatching {
                    fetchUnavailableReasons(products)
                }.getOrDefault(emptyMap())

                products
                    .map { product ->
                        val productWithAvailability = product.copy(
                            unavailableReason = unavailableReasons[product.id]
                        )
                        ProductImageResolver.resolve(
                            productWithAvailability.sourceProductName ?: productWithAvailability.name
                        )
                            ?.let { assetImageUrl ->
                                productWithAvailability.copy(imageUrl = assetImageUrl)
                            } ?: productWithAvailability
                    }
                    .toList()
            }
        }
    }

    private suspend fun fetchUnavailableReasons(products: List<Product>): Map<String, String> {
        val unavailableVariantIds = products
            .asSequence()
            .filterNot(Product::isOrderable)
            .map(Product::id)
            .toSet()
        if (unavailableVariantIds.isEmpty()) {
            return emptyMap()
        }

        val recipeLinks = supabaseClient
            .from(VARIANT_INGREDIENTS_TABLE)
            .select {
                order(column = "product_variant_id", order = Order.ASCENDING)
                order(column = "variant_ingredient_id", order = Order.ASCENDING)
            }
            .decodeList<VariantIngredientStockDto>()
            .filter { it.productVariantId in unavailableVariantIds }
            .groupBy(VariantIngredientStockDto::productVariantId)

        val ingredientStocks = supabaseClient
            .from(INGREDIENTS_TABLE)
            .select {
                order(column = "ingredient_name", order = Order.ASCENDING)
            }
            .decodeList<IngredientStockDto>()
            .associateBy(IngredientStockDto::ingredientId)

        return products
            .filter { it.id in unavailableVariantIds }
            .mapNotNull { product ->
                product.id to buildUnavailableReason(
                    recipeLinks = recipeLinks[product.id].orEmpty(),
                    ingredientStocks = ingredientStocks
                )
            }
            .toMap()
    }

    private fun buildUnavailableReason(
        recipeLinks: List<VariantIngredientStockDto>,
        ingredientStocks: Map<String, IngredientStockDto>
    ): String {
        if (recipeLinks.isEmpty()) {
            return "No recipe is assigned to this item yet."
        }

        val shortage = recipeLinks
            .mapNotNull { link ->
                val ingredient = ingredientStocks[link.ingredientId] ?: return@mapNotNull null
                val currentStock = ingredient.ingredientCurrentStock
                if (currentStock >= link.requiredQuantity) {
                    null
                } else {
                    IngredientShortage(
                        name = ingredient.ingredientName,
                        unit = ingredient.ingredientUnit,
                        requiredQuantity = link.requiredQuantity,
                        currentStock = currentStock
                    )
                }
            }
            .sortedWith(
                compareBy<IngredientShortage> { if (it.currentStock <= 0.0) 0 else 1 }
                    .thenBy { it.name.lowercase(Locale.US) }
            )
            .firstOrNull()

        return shortage?.toReason() ?: "This item has no sellable stock left."
    }

    private fun IngredientShortage.toReason(): String {
        if (currentStock <= 0.0) {
            return "$name is out of stock."
        }

        return "Not enough $name. Needs ${requiredQuantity.toDisplayQuantity()} $unit, " +
            "only ${currentStock.toDisplayQuantity()} $unit available."
    }

    private fun Double.toDisplayQuantity(): String {
        if (this % 1.0 == 0.0) {
            return toInt().toString()
        }
        return String.format(Locale.US, "%.2f", this)
            .trimEnd('0')
            .trimEnd('.')
    }

    private data class IngredientShortage(
        val name: String,
        val unit: String,
        val requiredQuantity: Double,
        val currentStock: Double
    )

    @Serializable
    private data class IngredientStockDto(
        @SerialName("ingredient_id")
        val ingredientId: String,
        @SerialName("ingredient_name")
        val ingredientName: String,
        @SerialName("ingredient_unit")
        val ingredientUnit: String,
        @SerialName("ingredient_current_stock")
        val ingredientCurrentStock: Double
    )

    @Serializable
    private data class VariantIngredientStockDto(
        @SerialName("variant_ingredient_id")
        val variantIngredientId: String,
        @SerialName("product_variant_id")
        val productVariantId: String,
        @SerialName("ingredient_id")
        val ingredientId: String,
        @SerialName("required_quantity")
        val requiredQuantity: Double
    )

    private companion object {
        const val INGREDIENTS_TABLE = "ingredients"
        const val VARIANT_INGREDIENTS_TABLE = "variant_ingredients"
    }
}
