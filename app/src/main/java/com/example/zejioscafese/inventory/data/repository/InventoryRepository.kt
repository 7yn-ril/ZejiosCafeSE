package com.example.zejioscafese.inventory.data.repository

import com.example.zejioscafese.core.supabase.SupabaseProvider
import com.example.zejioscafese.core.supabase.SupabaseSessionHelper
import com.example.zejioscafese.inventory.data.model.ProductCategoryOption
import com.example.zejioscafese.inventory.data.model.ProductEditorDraft
import com.example.zejioscafese.inventory.data.model.ProductRecipeIngredient
import com.example.zejioscafese.inventory.data.model.ProducibleProduct
import com.example.zejioscafese.inventory.data.remote.dto.IngredientDto
import com.example.zejioscafese.inventory.data.remote.dto.ProductCategoryDto
import com.example.zejioscafese.inventory.data.remote.dto.ProductRecipeLinkDto
import com.example.zejioscafese.inventory.data.remote.dto.ProducibleProductDto
import com.example.zejioscafese.pos.data.model.Ingredient
import com.example.zejioscafese.pos.data.model.IngredientUnits
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.round
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class InventoryRepository(
    private val clientProvider: () -> SupabaseClient = { SupabaseProvider.client }
) {

    private val supabaseClient: SupabaseClient
        get() = clientProvider()

    @Volatile
    private var includeIngredientMlPerServingColumn = true

    @Volatile
    private var includeIngredientMlPerBottleColumn = true

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
            withIngredientLiquidColumnFallback {
                includeServingColumn,
                includeBottleColumn ->
                insertIngredient(
                    ingredient = ingredient,
                    includeServingColumn = includeServingColumn,
                    includeBottleColumn = includeBottleColumn
                )
            }
        }
    }

    suspend fun updateIngredient(ingredient: Ingredient) {
        ensureAuthenticatedSession()
        withContext(Dispatchers.IO) {
            withIngredientLiquidColumnFallback {
                includeServingColumn,
                includeBottleColumn ->
                updateIngredientRow(
                    ingredient = ingredient,
                    includeServingColumn = includeServingColumn,
                    includeBottleColumn = includeBottleColumn
                )
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
            val normalizedImageUrl = draft.imageUrl?.trim()?.takeIf(String::isNotBlank)
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
                            productImageUrl = normalizedImageUrl,
                            productDisplayOrder = nextProductDisplayOrder(
                                categoryId = draft.categoryId,
                                existingProducts = existingProducts
                            )
                        )
                    )
            } else if (
                !matchingProduct.productIsActive ||
                matchingProduct.categoryId != draft.categoryId ||
                matchingProduct.productImageUrl != normalizedImageUrl
            ) {
                supabaseClient
                    .from(PRODUCTS_TABLE)
                    .update(
                        {
                            set("category_id", draft.categoryId)
                            set("product_name", normalizedProductName)
                            set("product_image_url", normalizedImageUrl)
                            set("product_is_active", true)
                        }
                    ) {
                        filter {
                            eq("product_id", productId)
                        }
                    }
            }

            val existingVariants = fetchVariantRows()
            val variantNames = if (draft.createDefaultBeverageSizes) {
                BEVERAGE_SIZE_VARIANTS
            } else {
                listOf(normalizedVariantName.ifBlank { STANDARD_VARIANT_NAME })
            }
            val variantIds = nextIds(
                prefix = VARIANT_ID_PREFIX,
                existingIds = existingVariants.map(VariantRowDto::productVariantId),
                count = variantNames.size
            )
            val firstVariantDisplayOrder = nextVariantDisplayOrder(
                productId = productId,
                existingVariants = existingVariants
            )
            val variantRows = variantNames.mapIndexed { index, variantName ->
                ProductVariantInsertDto(
                    productVariantId = variantIds[index],
                    productId = productId,
                    variantName = variantName,
                    variantPrice = priceForVariant(draft.price, variantName),
                    variantDisplayOrder = firstVariantDisplayOrder + index,
                    variantManualStockLeft = 0,
                    variantTrackInventory = ingredientRows.isNotEmpty(),
                    variantIsActive = true
                )
            }

            supabaseClient
                .from(PRODUCT_VARIANTS_TABLE)
                .insert(variantRows)

            variantRows.forEach { variant ->
                replaceVariantIngredients(
                    productVariantId = variant.productVariantId,
                    ingredients = ingredientsForVariant(
                        ingredients = ingredientRows,
                        variantName = variant.variantName
                    )
                )
            }
        }
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
            val normalizedImageUrl = draft.imageUrl?.trim()?.takeIf(String::isNotBlank)

            supabaseClient
                .from(PRODUCTS_TABLE)
                .update(
                    {
                        set("category_id", draft.categoryId)
                        set("product_name", normalizedProductName)
                        set("product_image_url", normalizedImageUrl)
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

    suspend fun updateProductVariantPrice(productVariantId: String, newPrice: Double) {
        ensureAuthenticatedSession()
        withContext(Dispatchers.IO) {
            supabaseClient
                .from(PRODUCT_VARIANTS_TABLE)
                .update(
                    {
                        set("variant_price", newPrice)
                    }
                ) {
                    filter {
                        eq("product_variant_id", productVariantId)
                    }
                }
        }
    }

    suspend fun updateVariantIngredientQuantity(
        productVariantId: String,
        ingredientId: String,
        newRequiredQuantity: Double
    ) {
        ensureAuthenticatedSession()
        withContext(Dispatchers.IO) {
            supabaseClient
                .from(VARIANT_INGREDIENTS_TABLE)
                .update(
                    {
                        set("required_quantity", newRequiredQuantity)
                    }
                ) {
                    filter {
                        eq("product_variant_id", productVariantId)
                        eq("ingredient_id", ingredientId)
                    }
                }
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

    suspend fun restoreProduct(product: ProducibleProduct) {
        ensureAuthenticatedSession()
        withContext(Dispatchers.IO) {
            supabaseClient
                .from(PRODUCTS_TABLE)
                .update(
                    {
                        set("product_is_active", true)
                    }
                ) {
                    filter {
                        eq("product_id", product.productId)
                    }
                }

            supabaseClient
                .from(PRODUCT_VARIANTS_TABLE)
                .update(
                    {
                        set("variant_is_active", true)
                    }
                ) {
                    filter {
                        eq("product_variant_id", product.id)
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

    private suspend fun withIngredientLiquidColumnFallback(
        block: suspend (
            includeServingColumn: Boolean,
            includeBottleColumn: Boolean
        ) -> Unit
    ) {
        repeat(INGREDIENT_OPTIONAL_COLUMN_RETRY_LIMIT) {
            try {
                block(
                    includeIngredientMlPerServingColumn,
                    includeIngredientMlPerBottleColumn
                )
                return
            } catch (exception: Exception) {
                val missingColumn = exception.missingIngredientLiquidColumn()
                    ?: throw exception
                if (!disableIngredientLiquidColumn(missingColumn)) {
                    throw exception
                }
            }
        }

        block(
            includeIngredientMlPerServingColumn,
            includeIngredientMlPerBottleColumn
        )
    }

    private fun disableIngredientLiquidColumn(column: String): Boolean {
        return when (column) {
            INGREDIENT_ML_PER_SERVING_COLUMN -> {
                val wasEnabled = includeIngredientMlPerServingColumn
                includeIngredientMlPerServingColumn = false
                wasEnabled
            }

            INGREDIENT_ML_PER_BOTTLE_COLUMN -> {
                val wasEnabled = includeIngredientMlPerBottleColumn
                includeIngredientMlPerBottleColumn = false
                wasEnabled
            }

            else -> false
        }
    }

    private suspend fun insertIngredient(
        ingredient: Ingredient,
        includeServingColumn: Boolean,
        includeBottleColumn: Boolean
    ) {
        val timestamp = currentTimestamp()
        val normalizedUnit = IngredientUnits.normalize(ingredient.unit)
        // Bulk = mL or grams. Both share the per-serving / per-bottle math
        // and therefore both need those columns populated. Only the
        // beverage cup-size scaling path stays mL-only (grams scoops
        // don't scale by cup size).
        val isBulk = IngredientUnits.isBulk(normalizedUnit)
        if (isBulk && includeServingColumn && includeBottleColumn) {
            supabaseClient
                .from(INGREDIENTS_TABLE)
                .insert(
                    IngredientInsertWithLiquidFieldsDto(
                        ingredientId = ingredient.id,
                        ingredientName = ingredient.name,
                        ingredientCategory = ingredient.category,
                        ingredientUnit = normalizedUnit,
                        ingredientCurrentStock = ingredient.currentStock,
                        ingredientMinimumStock = ingredient.minimumStock,
                        ingredientCostPerUnit = ingredient.costPerUnit,
                        ingredientLastRestockedAt = timestamp,
                        ingredientMlPerServing = ingredient.mlPerServing,
                        ingredientMlPerBottle = ingredient.mlPerBottle
                    )
                )
        } else if (isBulk && includeServingColumn) {
            supabaseClient
                .from(INGREDIENTS_TABLE)
                .insert(
                    IngredientInsertWithServingDto(
                        ingredientId = ingredient.id,
                        ingredientName = ingredient.name,
                        ingredientCategory = ingredient.category,
                        ingredientUnit = normalizedUnit,
                        ingredientCurrentStock = ingredient.currentStock,
                        ingredientMinimumStock = ingredient.minimumStock,
                        ingredientCostPerUnit = ingredient.costPerUnit,
                        ingredientLastRestockedAt = timestamp,
                        ingredientMlPerServing = ingredient.mlPerServing
                    )
                )
        } else {
            supabaseClient
                .from(INGREDIENTS_TABLE)
                .insert(
                    IngredientInsertDto(
                        ingredientId = ingredient.id,
                        ingredientName = ingredient.name,
                        ingredientCategory = ingredient.category,
                        ingredientUnit = normalizedUnit,
                        ingredientCurrentStock = ingredient.currentStock,
                        ingredientMinimumStock = ingredient.minimumStock,
                        ingredientCostPerUnit = ingredient.costPerUnit,
                        ingredientLastRestockedAt = timestamp
                    )
                )
        }
    }

    private suspend fun updateIngredientRow(
        ingredient: Ingredient,
        includeServingColumn: Boolean,
        includeBottleColumn: Boolean
    ) {
        val normalizedUnit = IngredientUnits.normalize(ingredient.unit)
        val isBulk = IngredientUnits.isBulk(normalizedUnit)
        supabaseClient
            .from(INGREDIENTS_TABLE)
            .update(
                {
                    set("ingredient_name", ingredient.name)
                    set("ingredient_category", ingredient.category)
                    set("ingredient_unit", normalizedUnit)
                    set("ingredient_current_stock", ingredient.currentStock)
                    set("ingredient_minimum_stock", ingredient.minimumStock)
                    set("ingredient_cost_per_unit", ingredient.costPerUnit)
                    if (isBulk && includeServingColumn) {
                        set(
                            INGREDIENT_ML_PER_SERVING_COLUMN,
                            ingredient.mlPerServing
                        )
                    }
                    if (isBulk && includeBottleColumn) {
                        set(
                            INGREDIENT_ML_PER_BOTTLE_COLUMN,
                            ingredient.mlPerBottle
                        )
                    }
                }
            ) {
                filter {
                    eq("ingredient_id", ingredient.id)
                }
            }
    }

    private suspend fun replaceVariantIngredients(
        productVariantId: String,
        ingredients: List<ProductRecipeIngredient>
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

    private fun priceForVariant(basePrice: Double, variantName: String): Double {
        return if (variantName.equals(BEVERAGE_LARGE_VARIANT_NAME, ignoreCase = true)) {
            basePrice + BEVERAGE_LARGE_PRICE_PREMIUM
        } else {
            basePrice
        }
    }

    private fun ingredientsForVariant(
        ingredients: List<ProductRecipeIngredient>,
        variantName: String
    ): List<ProductRecipeIngredient> {
        val targetSizeOz = beverageVariantSizeOz(variantName)
        if (targetSizeOz == BEVERAGE_BASE_SIZE_OZ) {
            return ingredients
        }

        return ingredients.map { ingredient ->
            if (IngredientUnits.isMl(ingredient.ingredientUnit)) {
                ingredient.copy(
                    requiredQuantity = roundToTwoDecimals(
                        ingredient.requiredQuantity * targetSizeOz / BEVERAGE_BASE_SIZE_OZ
                    )
                )
            } else {
                ingredient
            }
        }
    }

    private fun beverageVariantSizeOz(variantName: String): Double {
        return if (variantName.contains("22")) {
            22.0
        } else {
            BEVERAGE_BASE_SIZE_OZ
        }
    }

    private fun roundToTwoDecimals(value: Double): Double {
        return round(value * 100.0) / 100.0
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
        val ingredientLastRestockedAt: String
    )

    @Serializable
    private data class IngredientInsertWithServingDto(
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
        val ingredientMlPerServing: Double? = null
    )

    @Serializable
    private data class IngredientInsertWithLiquidFieldsDto(
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
        @SerialName("product_image_url")
        val productImageUrl: String? = null,
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
        @SerialName("product_image_url")
        val productImageUrl: String? = null,
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
        const val STANDARD_VARIANT_NAME = "Standard"
        const val BEVERAGE_LARGE_VARIANT_NAME = "22oz"
        const val BEVERAGE_BASE_SIZE_OZ = 16.0
        const val BEVERAGE_LARGE_PRICE_PREMIUM = 20.0
        val BEVERAGE_SIZE_VARIANTS = listOf("16oz", BEVERAGE_LARGE_VARIANT_NAME)
        const val INGREDIENT_ML_PER_SERVING_COLUMN = "ingredient_ml_per_serving"
        const val INGREDIENT_ML_PER_BOTTLE_COLUMN = "ingredient_ml_per_bottle"
        const val INGREDIENT_OPTIONAL_COLUMN_RETRY_LIMIT = 2
    }
}

private fun Throwable.missingIngredientLiquidColumn(): String? {
    var current: Throwable? = this
    var depth = 0

    while (current != null && depth < MAX_ERROR_CAUSE_DEPTH) {
        val message = current.message.orEmpty()
        if (message.isMissingPostgrestColumnError()) {
            return INGREDIENT_LIQUID_COLUMNS.firstOrNull { column ->
                message.contains(column, ignoreCase = true)
            }
        }

        current = current.cause
        depth += 1
    }

    return null
}

private fun String.isMissingPostgrestColumnError(): Boolean {
    return contains("Could not find", ignoreCase = true) ||
        contains("schema cache", ignoreCase = true) ||
        contains("PGRST204", ignoreCase = true)
}

private val INGREDIENT_LIQUID_COLUMNS = listOf(
    "ingredient_ml_per_serving",
    "ingredient_ml_per_bottle"
)
private const val MAX_ERROR_CAUSE_DEPTH = 10
