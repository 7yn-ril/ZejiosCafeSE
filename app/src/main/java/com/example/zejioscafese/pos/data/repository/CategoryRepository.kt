package com.example.zejioscafese.pos.data.repository

import com.example.zejioscafese.core.supabase.SupabaseProvider
import com.example.zejioscafese.core.supabase.SupabaseSessionHelper
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class CategoryRepository(
    private val clientProvider: () -> SupabaseClient = { SupabaseProvider.client }
) {

    private val supabaseClient: SupabaseClient
        get() = clientProvider()

    suspend fun fetchCategories(): List<String> {
        return withContext(Dispatchers.IO) {
            SupabaseSessionHelper.withJwtRetry(supabaseClient) {
                val categories = supabaseClient
                    .from("categories")
                    .select {
                        order(column = "category_display_order", order = Order.ASCENDING)
                        order(column = "category_name", order = Order.ASCENDING)
                    }
                    .decodeList<CategoryDto>()
                    .asSequence()
                    .filter { it.isActive }
                    .map(CategoryDto::name)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .toList()

                listOf(ALL_CATEGORY) + categories
            }
        }
    }

    @Serializable
    private data class CategoryDto(
        @SerialName("category_name")
        val name: String,
        @SerialName("category_is_active")
        val isActive: Boolean
    )

    companion object {
        const val ALL_CATEGORY: String = "All"
    }
}
