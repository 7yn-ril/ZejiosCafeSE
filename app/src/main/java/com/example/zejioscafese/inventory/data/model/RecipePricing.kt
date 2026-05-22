package com.example.zejioscafese.inventory.data.model

import com.example.zejioscafese.inventory.data.remote.dto.ProductRecipeLinkDto
import com.example.zejioscafese.pos.data.model.Ingredient
import kotlin.math.round

object RecipePricing {

    const val MARKUP_MULTIPLIER = 3.0

    fun computeCostFromDraft(
        recipe: List<ProductRecipeIngredient>,
        ingredientsById: Map<String, Ingredient>
    ): Double {
        return recipe.sumOf { row ->
            val unitCost = ingredientsById[row.ingredientId]?.costPerUnit ?: 0.0
            row.requiredQuantity * unitCost
        }
    }

    fun computeCostFromLinks(
        recipeLinks: List<ProductRecipeLinkDto>,
        ingredientsById: Map<String, Ingredient>
    ): Double {
        return recipeLinks.sumOf { link ->
            val unitCost = ingredientsById[link.ingredientId]?.costPerUnit ?: 0.0
            link.requiredQuantity * unitCost
        }
    }

    fun computePriceFromLinks(
        recipeLinks: List<ProductRecipeLinkDto>,
        ingredientsById: Map<String, Ingredient>
    ): Double {
        return roundCurrency(computeCostFromLinks(recipeLinks, ingredientsById) * MARKUP_MULTIPLIER)
    }

    fun roundCurrency(value: Double): Double {
        return round(value * 100.0) / 100.0
    }
}
