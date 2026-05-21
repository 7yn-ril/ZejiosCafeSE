package com.example.zejioscafese.pos.data.repository

import com.example.zejioscafese.core.supabase.SupabaseProvider
import com.example.zejioscafese.core.supabase.SupabaseSessionHelper
import com.example.zejioscafese.pos.data.model.Discount
import com.example.zejioscafese.pos.data.remote.dto.DiscountDto
import com.example.zejioscafese.pos.data.remote.dto.DiscountInsertDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DiscountRepository(
    private val clientProvider: () -> SupabaseClient = { SupabaseProvider.client }
) {

    private val supabaseClient: SupabaseClient
        get() = clientProvider()

    suspend fun fetchActiveDiscounts(today: LocalDate = LocalDate.now()): List<Discount> {
        return withContext(Dispatchers.IO) {
            SupabaseSessionHelper.withJwtRetry(supabaseClient) {
                supabaseClient
                    .from(ACTIVE_DISCOUNTS_VIEW)
                    .select {
                        order(column = "discount_is_built_in", order = Order.DESCENDING)
                        order(column = "discount_name", order = Order.ASCENDING)
                    }
                    .decodeList<DiscountDto>()
                    .map(DiscountDto::toDiscount)
                    .filter { it.isApplicableOn(today) }
            }
        }
    }

    suspend fun createCustomDiscount(
        name: String,
        percent: Double,
        startDate: LocalDate?,
        endDate: LocalDate?,
        existingIds: List<String>
    ): Discount {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Discount name cannot be empty." }
        require(percent > 0.0 && percent <= 100.0) { "Discount percent must be between 0 and 100." }
        require(
            startDate == null || endDate == null || !startDate.isAfter(endDate)
        ) { "Start date must be on or before end date." }

        val newId = nextId(existingIds)

        return withContext(Dispatchers.IO) {
            SupabaseSessionHelper.withJwtRetry(supabaseClient) {
                supabaseClient
                    .from(DISCOUNTS_TABLE)
                    .insert(
                        DiscountInsertDto(
                            discountId = newId,
                            discountName = normalizedName,
                            discountPercent = percent,
                            discountStartDate = startDate?.toString(),
                            discountEndDate = endDate?.toString(),
                            discountIsBuiltIn = false,
                            discountIsActive = true
                        )
                    )

                Discount(
                    id = newId,
                    name = normalizedName,
                    percent = percent,
                    startDate = startDate,
                    endDate = endDate,
                    isBuiltIn = false
                )
            }
        }
    }

    private fun nextId(existingIds: List<String>): String {
        val maxValue = existingIds
            .mapNotNull { it.removePrefix(CUSTOM_ID_PREFIX).toIntOrNull() }
            .maxOrNull() ?: 0
        return CUSTOM_ID_PREFIX + (maxValue + 1).toString().padStart(3, '0')
    }

    private companion object {
        const val DISCOUNTS_TABLE = "discounts"
        const val ACTIVE_DISCOUNTS_VIEW = "active_discounts"
        const val CUSTOM_ID_PREFIX = "DSC-"
    }
}
