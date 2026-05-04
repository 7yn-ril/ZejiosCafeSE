package com.example.zejioscafese.inventory.data.remote.dto

import com.example.zejioscafese.pos.data.model.Ingredient
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class IngredientDto(
    @SerialName("ingredient_id")
    val ingredientId: String,
    @SerialName("ingredient_name")
    val ingredientName: String,
    @SerialName("ingredient_category")
    val ingredientCategory: String? = null,
    @SerialName("ingredient_unit")
    val ingredientUnit: String,
    @SerialName("ingredient_current_stock")
    val ingredientCurrentStock: Double,
    @SerialName("ingredient_minimum_stock")
    val ingredientMinimumStock: Double,
    @SerialName("ingredient_cost_per_unit")
    val ingredientCostPerUnit: Double,
    @SerialName("ingredient_last_restocked_at")
    val ingredientLastRestockedAt: String? = null,
    @SerialName("ingredient_ml_per_serving")
    val ingredientMlPerServing: Double? = null,
    @SerialName("ingredient_ml_per_bottle")
    val ingredientMlPerBottle: Double? = null
) {

    fun toIngredient(): Ingredient {
        return Ingredient(
            id = ingredientId,
            name = ingredientName,
            category = ingredientCategory?.takeIf(String::isNotBlank) ?: deriveCategory(),
            unit = ingredientUnit,
            currentStock = ingredientCurrentStock,
            minimumStock = ingredientMinimumStock,
            costPerUnit = ingredientCostPerUnit,
            lastRestocked = ingredientLastRestockedAt.toDisplayDate(),
            mlPerServing = ingredientMlPerServing?.takeIf { it > 0.0 },
            mlPerBottle = ingredientMlPerBottle?.takeIf { it > 0.0 }
        )
    }

    private fun deriveCategory(): String {
        val normalizedName = ingredientName.lowercase(Locale.getDefault())
        return when {
            normalizedName.contains("milk") ||
                normalizedName.contains("cream") ||
                normalizedName.contains("cheese") ||
                normalizedName.contains("mayo") ||
                normalizedName.contains("egg") -> "Dairy"

            normalizedName.contains("syrup") ||
                normalizedName.contains("sauce") -> "Syrups & Sauces"

            normalizedName.contains("powder") ||
                normalizedName.contains("mix") ||
                normalizedName.contains("creamer") -> "Powders & Mixes"

            normalizedName.contains("banana") ||
                normalizedName.contains("lettuce") ||
                normalizedName.contains("tomato") ||
                normalizedName.contains("pineapple") -> "Produce"

            normalizedName.contains("beef") ||
                normalizedName.contains("chicken") ||
                normalizedName.contains("pork") ||
                normalizedName.contains("calamari") ||
                normalizedName.contains("fish") ||
                normalizedName.contains("sausage") ||
                normalizedName.contains("longganisa") ||
                normalizedName.contains("patty") ||
                normalizedName.contains("wings") ||
                normalizedName.contains("siomai") -> "Proteins"

            normalizedName.contains("bun") ||
                normalizedName.contains("tortilla") ||
                normalizedName.contains("pasta") ||
                normalizedName.contains("rice") ||
                normalizedName.contains("bread") -> "Pantry"

            normalizedName.contains("fries") ||
                normalizedName.contains("mojos") ||
                normalizedName.contains("nacho") ||
                normalizedName.contains("ice") -> "Frozen & Sides"

            normalizedName.contains("water") ||
                normalizedName.contains("yakult") ||
                normalizedName.contains("tea base") ||
                normalizedName.contains("lemonade base") -> "Beverages"

            else -> "Pantry"
        }
    }

    private fun String?.toDisplayDate(): String {
        if (this.isNullOrBlank()) return ""
        return try {
            OffsetDateTime.parse(this)
                .format(DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: DateTimeParseException) {
            this.take(10)
        }
    }
}
