package com.example.zejioscafese.inventory.data.repository

import com.example.zejioscafese.core.supabase.SupabaseProvider
import com.example.zejioscafese.core.supabase.SupabaseSessionHelper
import com.example.zejioscafese.inventory.data.model.ProductCategoryOption
import com.example.zejioscafese.inventory.data.model.ProductEditorDraft
import com.example.zejioscafese.inventory.data.model.ProducibleProduct
import com.example.zejioscafese.inventory.data.remote.dto.IngredientDto
import com.example.zejioscafese.inventory.data.remote.dto.ProductCategoryDto
import com.example.zejioscafese.inventory.data.remote.dto.ProductRecipeLinkDto
import com.example.zejioscafese.inventory.data.remote.dto.ProducibleProductDto
import com.example.zejioscafese.pos.data.model.Ingredient
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class InventoryRepository(
    private val clientProvider: () -> SupabaseClient = { SupabaseProvider.client }
) {

    private val supabaseClient: SupabaseClient
        get() = clientProvider()

    suspend fun fetchIngredients(): List<Ingredient> {
        return withContext(Dispatchers.IO) {
            SupabaseSessionHelper.withJwtRetry(supabaseClient) {
                supabaseClient
                    .from(INGREDIENTS_TABLE)
                    .select {
                        order(column = "ingredient_name", order = Order.ASCENDING)
                    }
                    .decodeList<IngredientDto>()
                    .map(IngredientDto::toIngredient)
            }
        }
    }

    suspend fun fetchProducibleProducts(): List<ProducibleProduct> {
        return withContext(Dispatchers.IO) {
            SupabaseSessionHelper.withJwtRetry(supabaseClient) {
                supabaseClient
                    .from(PRODUCIBLE_PRODUCTS_VIEW)
                    .select {
                        order(column = "category_name", order = Order.ASCENDING)
                        order(column = "product_name", order = Order.ASCENDING)
                        order(column = "variant_name", order = Order.ASCENDING)
                    }
                    .decodeList<ProducibleProductDto>()
                    .asSequence()
                    .filter { it.productIsActive && it.variantIsActive }
                    .map(ProducibleProductDto::toProducibleProduct)
                    .toList()
            }
        }
    }

    suspend fun fetchProductCategories(): List<ProductCategoryOption> {
        return withContext(Dispatchers.IO) {
            SupabaseSessionHelper.withJwtRetry(supabaseClient) {
                supabaseClient
                    .from(CATEGORIES_TABLE)
                    .select {
                        order(column = "category_display_order", order = Order.ASCENDING)
                    }
                    .decodeList<ProductCategoryDto>()
                    .asSequence()
                    .filter { it.categoryIsActive }
                    .map(ProductCategoryDto::toCategoryOption)
                    .toList()
            }
        }
    }

    suspend fun fetchOrderVariantCounts(): Map<String, Int> {
        return withContext(Dispatchers.IO) {
            SupabaseSessionHelper.withJwtRetry(supabaseClient) {
                supabaseClient
                    .from(ORDER_ITEMS_TABLE)
                    .select()
                    .decodeList<OrderItemVariantDto>()
                    .filter { !it.productVariantId.isNullOrBlank() }
                    .groupBy { it.productVariantId!! }
                    .mapValues { (_, items) -> items.sumOf { it.quantity } }
            }
        }
    }

    suspend fun fetchProductRecipeLinks(): List<ProductRecipeLinkDto> {
        return withContext(Dispatchers.IO) {
            SupabaseSessionHelper.withJwtRetry(supabaseClient) {
                supabaseClient
                    .from(VARIANT_INGREDIENTS_TABLE)
                    .select {
                        order(column = "product_variant_id", order = Order.ASCENDING)
                        order(column = "variant_ingredient_id", order = Order.ASCENDING)
                    }
                    .decodeList<ProductRecipeLinkDto>()
            }
        }
    }

    suspend fun addIngredient(ingredient: Ingredient) {
        ensureAuthenticatedSession()
        withContext(Dispatchers.IO) {
            supabaseClient
                .from(INGREDIENTS_TABLE)
                .insert(
                    IngredientInsertDto(
                        ingredientId = ingredient.id,
                        ingredientName = ingredient.name,
                        ingredientCategory = ingredient.category,
                        ingredientUnit = ingredient.unit,
                        ingredientCurrentStock = ingredient.currentStock,
                        ingredientMinimumStock = ingredient.minimumStock,
                        ingredientCostPerUnit = ingredient.costPerUnit,
                        ingredientLastRestockedAt = currentTimestamp(),
                        ingredientMlPerServing = ingredient.mlPerServing,
                        ingredientMlPerBottle = ingredient.mlPerBottle
                    )
                )
        }
    }

    suspend fun updateIngredient(ingredient: Ingredient) {
        ensureAuthenticatedSession()
        withContext(Dispatchers.IO) {
            supabaseClient
                .from(INGREDIENTS_TABLE)
                .update(
                    {
                        set("ingredient_name", ingredient.name)
                        set("ingredient_category", ingredient.category)
                        set("ingredient_unit", ingredient.unit)
                        set("ingredient_current_stock", ingredient.currentStock)
                        set("ingredient_minimum_stock", ingredient.minimumStock)
                        set("ingredient_cost_per_unit", ingredient.costPerUnit)
                        if (ingredient.isLiquid) {
                            set("ingredient_ml_per_serving", ingredient.mlPerServing)
                            set("ingredient_ml_per_bottle", ingredient.mlPerBottle)
                        } else {
                            set("ingredient_ml_per_serving", null as Double?)
                            set("ingredient_ml_per_bottle", null as Double?)
                        }
                    }
                ) {
                    filter {
                        eq("ingredient_id", ingredient.id)
                    }
                }
        }
    }

    suspend fun restockIngredient(ingredientId: String, updatedStock: Double) {
        ensureAuthenticatedSession()
        withContext(Dispatchers.IO) {
            supabaseClient
                .from(INGREDIENTS_TABLE)
                .update(
                    {
                        set("ingredient_current_stock", updatedStock)
                        set("ingredient_last_restocked_at", currentTimestamp())
                    }
                ) {
                    filter {
                        eq("ingredient_id", ingredientId)
                    }
                }
        }
    }

    suspend fun addProduct(draft: ProductEditorDraft) {
        ensureAuthenticatedSession()
        withContext(Dispatchers.IO) {
        val normalizedProductName = draft.productName.trim()
        val normalizedVariantName = draft.variantName.trim()
        val ingredientRows = draft.ingredients

        val existingProducts = fetchProductRows()
        val matchingProduct = existingProducts.firstOrNull {
            it.categoryId == draft.categoryId &&
                it.productName.equals(normalizedProductName, ignoreCase = true)
        }

        val productId = matchingProduct?.productId ?: nextId(
            prefix = PRODUCT_ID_PREFIX,
            existingIds = existingProducts.map(ProductRowDto::productId)
        )

        if (matchingProduct == null) {
            supabaseClient
                .from(PRODUCTS_TABLE)
                .insert(
                    ProductInsertDto(
                        productId = productId,
                        categoryId = draft.categoryId,
                        productName = normalizedProductName,
                        productDisplayOrder = nextProductDisplayOrder(
                            categoryId = draft.categoryId,
                            existingProducts = existingProducts
                        )
                    )
                )
        } else if (!matchingProduct.productIsActive || matchingProduct.categoryId != draft.categoryId) {
            supabaseClient
                .from(PRODUCTS_TABLE)
                .update(
                    {
                        set("category_id", draft.categoryId)
                        set("product_name", normalizedProductName)
                        set("product_is_active", true)
                    }
                ) {
                    filter {
                        eq("product_id", productId)
                    }
                }
        }

        val existingVariants = fetchVariantRows()
        val variantId = nextId(
            prefix = VARIANT_ID_PREFIX,
            existingIds = existingVariants.map(VariantRowDto::productVariantId)
        )

        supabaseClient
            .from(PRODUCT_VARIANTS_TABLE)
            .insert(
                ProductVariantInsertDto(
                    productVariantId = variantId,
                    productId = productId,
                    variantName = normalizedVariantName,
                    variantPrice = draft.price,
                    variantDisplayOrder = nextVariantDisplayOrder(
                        productId = productId,
                        existingVariants = existingVariants
                    ),
                    variantManualStockLeft = 0,
                    variantTrackInventory = ingredientRows.isNotEmpty(),
                    variantIsActive = true
                )
            )

        replaceVariantIngredients(
            productVariantId = variantId,
            ingredients = ingredientRows
        )
        } // end withContext
    }

    suspend fun updateProduct(draft: ProductEditorDraft) {
        ensureAuthenticatedSession()
        withContext(Dispatchers.IO) {
            val productId = requireNotNull(draft.productId) { "Missing product id for update." }
            val productVariantId = requireNotNull(draft.productVariantId) {
                "Missing product variant id for update."
            }

            val normalizedProductName = draft.productName.trim()
            val normalizedVariantName = draft.variantName.trim()

            supabaseClient
                .from(PRODUCTS_TABLE)
                .update(
                    {
                        set("category_id", draft.categoryId)
                        set("product_name", normalizedProductName)
                        set("product_is_active", true)
                    }
                ) {
                    filter {
                        eq("product_id", productId)
                    }
                }

            supabaseClient
                .from(PRODUCT_VARIANTS_TABLE)
                .update(
                    {
                        set("variant_name", normalizedVariantName)
                        set("variant_price", draft.price)
                        set("variant_track_inventory", draft.ingredients.isNotEmpty())
                        set("variant_is_active", true)
                        if (draft.ingredients.isEmpty()) {
                            set("variant_manual_stock_left", 0)
                        }
                    }
                ) {
                    filter {
                        eq("product_variant_id", productVariantId)
                    }
                }

            replaceVariantIngredients(
                productVariantId = productVariantId,
                ingredients = draft.ingredients
            )
        }
    }

    suspend fun softDeleteProduct(product: ProducibleProduct) {
        ensureAuthenticatedSession()
        withContext(Dispatchers.IO) {
            supabaseClient
                .from(PRODUCT_VARIANTS_TABLE)
                .update(
                    {
                        set("variant_is_active", false)
                    }
                ) {
                    filter {
                        eq("product_variant_id", product.id)
                    }
                }

            val remainingActiveVariants = supabaseClient
                .from(PRODUCT_VARIANTS_TABLE)
                .select {
                    filter {
                        eq("product_id", product.productId)
                        eq("variant_is_active", true)
                    }
                }
                .decodeList<VariantRowDto>()

            if (remainingActiveVariants.isEmpty()) {
                supabaseClient
                    .from(PRODUCTS_TABLE)
                    .update(
                        {
                            set("product_is_active", false)
                        }
                    ) {
                        filter {
                            eq("product_id", product.productId)
                        }
                    }
            }
        }
    }

    private suspend fun ensureAuthenticatedSession() {
        SupabaseSessionHelper.ensureValidSession(supabaseClient)
    }

    private fun currentTimestamp(): String {
        return OffsetDateTime.now(ZoneOffset.UTC).toString()
    }

    private suspend fun replaceVariantIngredients(
        productVariantId: String,
        ingredients: List<com.example.zejioscafese.inventory.data.model.ProductRecipeIngredient>
    ) {
        supabaseClient
            .from(VARIANT_INGREDIENTS_TABLE)
            .delete {
                filter {
                    eq("product_variant_id", productVariantId)
                }
            }

        if (ingredients.isEmpty()) {
            return
        }

        val existingRecipeLinks = fetchProductRecipeLinks()
        val newRecipeIds = nextIds(
            prefix = RECIPE_ID_PREFIX,
            existingIds = existingRecipeLinks.map(ProductRecipeLinkDto::variantIngredientId),
            count = ingredients.size
        )

        val inserts = ingredients.mapIndexed { index, ingredient ->
            VariantIngredientInsertDto(
                variantIngredientId = newRecipeIds[index],
                productVariantId = productVariantId,
                ingredientId = ingredient.ingredientId,
                requiredQuantity = ingredient.requiredQuantity
            )
        }

        supabaseClient
            .from(VARIANT_INGREDIENTS_TABLE)
            .insert(inserts)
    }

    private suspend fun fetchProductRows(): List<ProductRowDto> {
        return supabaseClient
            .from(PRODUCTS_TABLE)
            .select {
                order(column = "product_id", order = Order.ASCENDING)
            }
            .decodeList<ProductRowDto>()
    }

    private suspend fun fetchVariantRows(): List<VariantRowDto> {
        return supabaseClient
            .from(PRODUCT_VARIANTS_TABLE)
            .select {
                order(column = "product_variant_id", order = Order.ASCENDING)
            }
            .decodeList<VariantRowDto>()
    }

    private fun nextProductDisplayOrder(
        categoryId: String,
        existingProducts: List<ProductRowDto>
    ): Int {
        return existingProducts
            .filter { it.categoryId == categoryId }
            .maxOfOrNull(ProductRowDto::productDisplayOrder)
            ?.plus(1) ?: 1
    }

    private fun nextVariantDisplayOrder(
        productId: String,
        existingVariants: List<VariantRowDto>
    ): Int {
        return existingVariants
            .filter { it.productId == productId }
            .maxOfOrNull(VariantRowDto::variantDisplayOrder)
            ?.plus(1) ?: 1
    }

    private fun nextId(prefix: String, existingIds: List<String>): String {
        val maxValue = existingIds
            .mapNotNull { id ->
                id.removePrefix(prefix).toIntOrNull()
            }
            .maxOrNull() ?: 0
        return prefix + (maxValue + 1).toString().padStart(3, '0')
    }

    private fun nextIds(prefix: String, existingIds: List<String>, count: Int): List<String> {
        val maxValue = existingIds
            .mapNotNull { id ->
                id.removePrefix(prefix).toIntOrNull()
            }
            .maxOrNull() ?: 0

        return (1..count).map { offset ->
            prefix + (maxValue + offset).toString().padStart(3, '0')
        }
    }

    @Serializable
    private data class IngredientInsertDto(
        @SerialName("ingredient_id")
        val ingredientId: String,
        @SerialName("ingredient_name")
        val ingredientName: String,
        @SerialName("ingredient_category")
        val ingredientCategory: String,
        @SerialName("ingredient_unit")
        val ingredientUnit: String,
        @SerialName("ingredient_current_stock")
        val ingredientCurrentStock: Double,
        @SerialName("ingredient_minimum_stock")
        val ingredientMinimumStock: Double,
        @SerialName("ingredient_cost_per_unit")
        val ingredientCostPerUnit: Double,
        @SerialName("ingredient_last_restocked_at")
        val ingredientLastRestockedAt: String,
        @SerialName("ingredient_ml_per_serving")
        val ingredientMlPerServing: Double? = null,
        @SerialName("ingredient_ml_per_bottle")
        val ingredientMlPerBottle: Double? = null
    )

    @Serializable
    private data class ProductInsertDto(
        @SerialName("product_id")
        val productId: String,
        @SerialName("category_id")
        val categoryId: String,
        @SerialName("product_name")
        val productName: String,
        @SerialName("product_display_order")
        val productDisplayOrder: Int,
        @SerialName("product_is_active")
        val productIsActive: Boolean = true
    )

    @Serializable
    private data class ProductVariantInsertDto(
        @SerialName("product_variant_id")
        val productVariantId: String,
        @SerialName("product_id")
        val productId: String,
        @SerialName("variant_name")
        val variantName: String,
        @SerialName("variant_price")
        val variantPrice: Double,
        @SerialName("variant_display_order")
        val variantDisplayOrder: Int,
        @SerialName("variant_manual_stock_left")
        val variantManualStockLeft: Int,
        @SerialName("variant_track_inventory")
        val variantTrackInventory: Boolean,
        @SerialName("variant_is_active")
        val variantIsActive: Boolean = true
    )

    @Serializable
    private data class VariantIngredientInsertDto(
        @SerialName("variant_ingredient_id")
        val variantIngredientId: String,
        @SerialName("product_variant_id")
        val productVariantId: String,
        @SerialName("ingredient_id")
        val ingredientId: String,
        @SerialName("required_quantity")
        val requiredQuantity: Double
    )

    @Serializable
    private data class ProductRowDto(
        @SerialName("product_id")
        val productId: String,
        @SerialName("category_id")
        val categoryId: String,
        @SerialName("product_name")
        val productName: String,
        @SerialName("product_display_order")
        val productDisplayOrder: Int = 0,
        @SerialName("product_is_active")
        val productIsActive: Boolean = true
    )

    @Serializable
    private data class VariantRowDto(
        @SerialName("product_variant_id")
        val productVariantId: String,
        @SerialName("product_id")
        val productId: String,
        @SerialName("variant_display_order")
        val variantDisplayOrder: Int = 0
    )

    @Serializable
    private data class OrderItemVariantDto(
        @SerialName("product_variant_id")
        val productVariantId: String? = null,
        @SerialName("order_item_quantity")
        val quantity: Int = 1
    )

    private companion object {
        const val CATEGORIES_TABLE = "categories"
        const val INGREDIENTS_TABLE = "ingredients"
        const val ORDER_ITEMS_TABLE = "order_items"
        const val PRODUCTS_TABLE = "products"
        const val PRODUCT_VARIANTS_TABLE = "product_variants"
        const val PRODUCIBLE_PRODUCTS_VIEW = "product_variant_stock_view"
        const val VARIANT_INGREDIENTS_TABLE = "variant_ingredients"
        const val PRODUCT_ID_PREFIX = "PRD-"
        const val VARIANT_ID_PREFIX = "VAR-"
        const val RECIPE_ID_PREFIX = "RCP-"
    }
}
