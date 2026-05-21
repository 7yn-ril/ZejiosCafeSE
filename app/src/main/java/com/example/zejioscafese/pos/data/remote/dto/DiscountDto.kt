package com.example.zejioscafese.pos.data.remote.dto

import com.example.zejioscafese.pos.data.model.Discount
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DiscountDto(
    @SerialName("discount_id")
    val discountId: String,
    @SerialName("discount_name")
    val discountName: String,
    @SerialName("discount_percent")
    val discountPercent: Double,
    @SerialName("discount_start_date")
    val discountStartDate: String? = null,
    @SerialName("discount_end_date")
    val discountEndDate: String? = null,
    @SerialName("discount_is_built_in")
    val discountIsBuiltIn: Boolean = false
) {
    fun toDiscount(): Discount = Discount(
        id = discountId,
        name = discountName,
        percent = discountPercent,
        startDate = discountStartDate.toLocalDateOrNull(),
        endDate = discountEndDate.toLocalDateOrNull(),
        isBuiltIn = discountIsBuiltIn
    )

    private fun String?.toLocalDateOrNull(): LocalDate? {
        if (this.isNullOrBlank()) return null
        return try {
            LocalDate.parse(this)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}

@Serializable
data class DiscountInsertDto(
    @SerialName("discount_id")
    val discountId: String,
    @SerialName("discount_name")
    val discountName: String,
    @SerialName("discount_percent")
    val discountPercent: Double,
    @SerialName("discount_start_date")
    val discountStartDate: String? = null,
    @SerialName("discount_end_date")
    val discountEndDate: String? = null,
    @SerialName("discount_is_built_in")
    val discountIsBuiltIn: Boolean = false,
    @SerialName("discount_is_active")
    val discountIsActive: Boolean = true
)
