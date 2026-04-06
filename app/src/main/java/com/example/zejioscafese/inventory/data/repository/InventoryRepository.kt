package com.example.zejioscafese.inventory.data.repository

import com.example.zejioscafese.core.supabase.SupabaseProvider
import com.example.zejioscafese.inventory.data.model.ProducibleProduct
import com.example.zejioscafese.inventory.data.remote.dto.IngredientDto
import com.example.zejioscafese.inventory.data.remote.dto.ProducibleProductDto
import com.example.zejioscafese.pos.data.model.Ingredient
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class InventoryRepository(
    private val supabaseClient: SupabaseClient = SupabaseProvider.client
) {

    suspend fun fetchIngredients(): List<Ingredient> {
        return supabaseClient
            .from(INGREDIENTS_TABLE)
            .select {
                order(column = "ingredient_name", order = Order.ASCENDING)
            }
            .decodeList<IngredientDto>()
            .map(IngredientDto::toIngredient)
    }

    suspend fun fetchProducibleProducts(): List<ProducibleProduct> {
        return supabaseClient
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

    suspend fun addIngredient(ingredient: Ingredient) {
        ensureAuthenticatedSession()

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
                    ingredientLastRestockedAt = currentTimestamp()
                )
            )
    }

    suspend fun updateIngredient(ingredient: Ingredient) {
        ensureAuthenticatedSession()

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
                }
            ) {
                filter {
                    eq("ingredient_id", ingredient.id)
                }
            }
    }

    suspend fun restockIngredient(ingredientId: String, updatedStock: Double) {
        ensureAuthenticatedSession()

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

    private suspend fun ensureAuthenticatedSession() {
        val auth = supabaseClient.pluginManager.getPlugin(Auth)
        auth.awaitInitialization()

        if (auth.currentUserOrNull() != null) {
            return
        }

        auth.signInAnonymously()
    }

    private fun currentTimestamp(): String {
        return OffsetDateTime.now(ZoneOffset.UTC).toString()
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

    private companion object {
        const val INGREDIENTS_TABLE = "ingredients"
        const val PRODUCIBLE_PRODUCTS_VIEW = "product_variant_stock_view"
    }
}
