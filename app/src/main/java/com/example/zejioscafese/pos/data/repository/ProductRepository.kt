package com.example.zejioscafese.pos.data.repository

import com.example.zejioscafese.core.supabase.SupabaseProvider
import com.example.zejioscafese.core.supabase.SupabaseSessionHelper
import com.example.zejioscafese.pos.data.local.ProductImageResolver
import com.example.zejioscafese.pos.data.model.IngredientUnits
import com.example.zejioscafese.pos.data.model.Product
import com.example.zejioscafese.pos.data.model.ProductRecipeRequirement
import com.example.zejioscafese.pos.data.remote.dto.ProductVariantStockDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import java.util.Locale
import kotlin.math.floor
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

                val recipeFetch = runCatching {
                    fetchRecipeAvailability(products)
                }.getOrDefault(RecipeFetchResult(emptyMap()))
                val recipeAvailability = recipeFetch.availability

                products
                    .map { product ->
                        val availability = recipeAvailability[product.id]
                        val stockAwareProduct = availability
                            ?.let {
                                product.copy(
                                    stockLeft = it.availableQuantity,
                                    recipeIngredients = it.requirements
                                )
                            }
                            ?: product
                        val productWithAvailability = stockAwareProduct.copy(
                            unavailableReason = if (stockAwareProduct.isOrderable) {
                                null
                            } else {
                                availability?.unavailableReason ?: "This item has no sellable stock left."
                            }
                        )
                        if (!productWithAvailability.imageUrl.isNullOrBlank()) {
                            productWithAvailability
                        } else {
                            ProductImageResolver.resolve(
                                productWithAvailability.sourceProductName ?: productWithAvailability.name
                            )
                                ?.let { assetImageUrl ->
                                    productWithAvailability.copy(imageUrl = assetImageUrl)
                                } ?: productWithAvailability
                        }
                    }
                    .toList()
            }
        }
    }

    private suspend fun fetchRecipeAvailability(products: List<Product>): RecipeFetchResult {
        val productVariantIds = products.map(Product::id).toSet()
        if (productVariantIds.isEmpty()) {
            return RecipeFetchResult(emptyMap())
        }

        val recipeLinks = supabaseClient
            .from(VARIANT_INGREDIENTS_TABLE)
            .select {
                order(column = "product_variant_id", order = Order.ASCENDING)
                order(column = "variant_ingredient_id", order = Order.ASCENDING)
            }
            .decodeList<VariantIngredientStockDto>()
            .filter { it.productVariantId in productVariantIds && it.requiredQuantity > 0.0 }
            .groupBy(VariantIngredientStockDto::productVariantId)
        if (recipeLinks.isEmpty()) {
            return RecipeFetchResult(emptyMap())
        }

        val ingredientStocks = supabaseClient
            .from(INGREDIENTS_TABLE)
            .select {
                order(column = "ingredient_name", order = Order.ASCENDING)
            }
            .decodeList<IngredientStockDto>()
            .associateBy(IngredientStockDto::ingredientId)

        val scaledRecipeLinks = applyBeverageRecipeScaling(
            products = products,
            recipeLinks = recipeLinks,
            ingredientStocks = ingredientStocks
        )

        val availability = scaledRecipeLinks
            .mapValues { (_, links) ->
                val availableQuantity = computeAvailableQuantity(
                    recipeLinks = links,
                    ingredientStocks = ingredientStocks
                )
                RecipeAvailability(
                    availableQuantity = availableQuantity,
                    requirements = buildRecipeRequirements(
                        recipeLinks = links,
                        ingredientStocks = ingredientStocks
                    ),
                    unavailableReason = if (availableQuantity <= 0) {
                        buildUnavailableReason(
                            recipeLinks = links,
                            ingredientStocks = ingredientStocks
                        )
                    } else {
                        null
                    }
                )
            }

        return RecipeFetchResult(availability = availability)
    }

    private fun applyBeverageRecipeScaling(
        products: List<Product>,
        recipeLinks: Map<String, List<VariantIngredientStockDto>>,
        ingredientStocks: Map<String, IngredientStockDto>
    ): Map<String, List<VariantIngredientStockDto>> {
        val variantsByProduct = products.groupBy { it.sourceProductId ?: it.id }
        val result = recipeLinks.toMutableMap()

        products.forEach { product ->
            val sizeOz = sizeOzForVariant(product.sourceVariantName ?: product.name) ?: return@forEach
            if (sizeOz == BEVERAGE_BASE_SIZE_OZ) return@forEach

            val parentKey = product.sourceProductId ?: product.id
            val baseSibling = variantsByProduct[parentKey]
                ?.firstOrNull { sizeOzForVariant(it.sourceVariantName ?: it.name) == BEVERAGE_BASE_SIZE_OZ }
                ?: return@forEach

            val myLinks = recipeLinks[product.id].orEmpty()
            val baseLinks = recipeLinks[baseSibling.id].orEmpty()
            if (myLinks.isEmpty() || baseLinks.isEmpty()) return@forEach
            if (!recipesIdentical(myLinks, baseLinks)) return@forEach

            val scaleFactor = sizeOz / BEVERAGE_BASE_SIZE_OZ
            result[product.id] = myLinks.map { link ->
                val unit = ingredientStocks[link.ingredientId]?.ingredientUnit ?: ""
                if (IngredientUnits.isMl(unit)) {
                    link.copy(requiredQuantity = roundCurrency(link.requiredQuantity * scaleFactor))
                } else {
                    link
                }
            }
        }
        return result
    }

    private fun recipesIdentical(
        a: List<VariantIngredientStockDto>,
        b: List<VariantIngredientStockDto>
    ): Boolean {
        if (a.size != b.size) return false
        val aMap = a.associate { it.ingredientId to it.requiredQuantity }
        val bMap = b.associate { it.ingredientId to it.requiredQuantity }
        return aMap == bMap
    }

    private fun roundCurrency(value: Double): Double = kotlin.math.round(value * 100.0) / 100.0

    private fun sizeOzForVariant(variantName: String?): Double? {
        if (variantName == null) return null
        val normalized = variantName.trim().lowercase(Locale.US)
        return when {
            "22" in normalized -> 22.0
            "16" in normalized || normalized == "mezzo" -> BEVERAGE_BASE_SIZE_OZ
            else -> null
        }
    }

    private fun computeAvailableQuantity(
        recipeLinks: List<VariantIngredientStockDto>,
        ingredientStocks: Map<String, IngredientStockDto>
    ): Int {
        return recipeLinks
            .minOfOrNull { link ->
                val currentStock = ingredientStocks[link.ingredientId]
                    ?.ingredientCurrentStock
                    ?.coerceAtLeast(0.0)
                    ?: 0.0
                floor(currentStock / link.requiredQuantity).toInt()
            }
            ?.coerceAtLeast(0)
            ?: 0
    }

    private fun buildRecipeRequirements(
        recipeLinks: List<VariantIngredientStockDto>,
        ingredientStocks: Map<String, IngredientStockDto>
    ): List<ProductRecipeRequirement> {
        return recipeLinks.mapNotNull { link ->
            val ingredient = ingredientStocks[link.ingredientId] ?: return@mapNotNull null
            ProductRecipeRequirement(
                ingredientId = ingredient.ingredientId,
                ingredientName = ingredient.ingredientName,
                ingredientUnit = IngredientUnits.normalize(ingredient.ingredientUnit),
                requiredQuantity = link.requiredQuantity,
                currentStock = ingredient.ingredientCurrentStock
            )
        }
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
                        unit = IngredientUnits.normalize(ingredient.ingredientUnit),
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

    private data class RecipeAvailability(
        val availableQuantity: Int,
        val requirements: List<ProductRecipeRequirement>,
        val unavailableReason: String?
    )

    private data class RecipeFetchResult(
        val availability: Map<String, RecipeAvailability>
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
        val ingredientCurrentStock: Double,
        @SerialName("ingredient_cost_per_unit")
        val ingredientCostPerUnit: Double = 0.0
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
        const val BEVERAGE_BASE_SIZE_OZ = 16.0
    }
}
