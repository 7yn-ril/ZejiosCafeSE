package com.example.zejioscafese.inventory.data.model

import com.example.zejioscafese.pos.data.model.Ingredient
import java.util.Locale

object ProductIngredientFilter {

    private data class Rule(
        val ingredientIds: Set<String>,
        val nameKeywords: List<String> = emptyList()
    )

    private val BURGERS_RULE = Rule(
        ingredientIds = setOf(
            "ING-041", "ING-042", "ING-043", "ING-044", "ING-045", "ING-046",
            "ING-047", "ING-048", "ING-049", "ING-050", "ING-051"
        ),
        nameKeywords = listOf("bun", "patty", "burger", "lettuce", "tomato slice", "bacon slice", "mayo")
    )

    private val MILK_TEA_RULE = Rule(
        ingredientIds = setOf(
            "ING-002", "ING-039", "ING-019", "ING-020", "ING-021", "ING-022", "ING-023",
            "ING-024", "ING-025", "ING-026", "ING-027", "ING-028", "ING-007", "ING-040",
            "ING-037", "ING-038"
        ),
        nameKeywords = listOf("tea base", "milk tea", "creamer", "tapioca", "pearl", "nata", "wintermelon", "okinawa", "taro", "brown sugar")
    )

    private val FRUIT_TEA_RULE = Rule(
        ingredientIds = setOf("ING-002", "ING-030", "ING-031", "ING-032", "ING-033", "ING-040"),
        nameKeywords = listOf("tea base", "fruit", "lychee", "strawberry", "blueberry", "apple")
    )

    private val CREMACHEE_RULE = Rule(
        ingredientIds = setOf("ING-002", "ING-007", "ING-030", "ING-031", "ING-032", "ING-033", "ING-040"),
        nameKeywords = listOf("tea base", "cream cheese", "fruit", "lychee", "strawberry", "blueberry")
    )

    private val COFFEE_RULE = Rule(
        ingredientIds = setOf(
            "ING-001", "ING-082", "ING-040", "ING-003", "ING-006", "ING-011", "ING-013",
            "ING-014", "ING-015", "ING-016", "ING-024", "ING-008", "ING-012", "ING-017", "ING-018"
        ),
        nameKeywords = listOf("espresso", "coffee", "latte", "mocha", "whole milk", "condensed milk", "caramel syrup", "almond syrup")
    )

    private val NON_COFFEE_RULE = Rule(
        ingredientIds = setOf("ING-003", "ING-004", "ING-024", "ING-031", "ING-032", "ING-040"),
        nameKeywords = listOf("matcha", "whole milk", "oat milk")
    )

    private val BREVE_RULE = Rule(
        ingredientIds = setOf("ING-001", "ING-005", "ING-006", "ING-010", "ING-011", "ING-013", "ING-040", "ING-003", "ING-018"),
        nameKeywords = listOf("breve", "espresso", "half-and-half", "condensed milk", "vanilla syrup")
    )

    private val COFFEE_FRAPPE_RULE = Rule(
        ingredientIds = setOf("ING-001", "ING-003", "ING-009", "ING-010", "ING-011", "ING-013", "ING-014", "ING-017", "ING-040"),
        nameKeywords = listOf("frappe", "espresso", "whipped cream", "ice", "mocha")
    )

    private val NON_COFFEE_FRAPPE_RULE = Rule(
        ingredientIds = setOf("ING-003", "ING-009", "ING-014", "ING-021", "ING-024", "ING-031", "ING-032", "ING-034", "ING-040"),
        nameKeywords = listOf("frappe", "matcha", "whipped cream", "ice", "strawberry syrup", "blueberry syrup")
    )

    private val LEMONADE_RULE = Rule(
        ingredientIds = setOf("ING-029", "ING-030", "ING-031", "ING-033", "ING-040", "ING-076"),
        nameKeywords = listOf("lemonade", "lemon", "cucumber syrup")
    )

    private val SMOOTHIE_RULE = Rule(
        ingredientIds = setOf("ING-003", "ING-021", "ING-035", "ING-036", "ING-040"),
        nameKeywords = listOf("banana", "smoothie", "peanut butter")
    )

    private val RICE_MEAL_RULE = Rule(
        ingredientIds = setOf(
            "ING-042", "ING-047", "ING-049", "ING-066", "ING-067", "ING-068",
            "ING-069", "ING-070", "ING-071", "ING-072", "ING-073"
        ),
        nameKeywords = listOf("rice serving", "longa", "sisig", "tapa", "tocino", "hungarian", "fish fillet", "chicken poppers", "egg")
    )

    private val WINGS_RULE = Rule(
        ingredientIds = setOf("ING-060", "ING-061", "ING-062", "ING-063"),
        nameKeywords = listOf("chicken wing", "buffalo sauce", "teriyaki", "honey garlic")
    )

    private val PASTA_RULE = Rule(
        ingredientIds = setOf("ING-047", "ING-048", "ING-064", "ING-065"),
        nameKeywords = listOf("pasta", "fettuccine", "carbonara", "noodle")
    )

    private val SIDES_RULE = Rule(
        ingredientIds = setOf(
            "ING-050", "ING-052", "ING-053", "ING-054", "ING-055", "ING-056",
            "ING-057", "ING-058", "ING-059", "ING-073", "ING-074", "ING-079"
        ),
        nameKeywords = listOf("fries", "mojos", "nacho", "siomai", "calamari", "popper", "finger", "quesadilla", "tortilla", "ground beef", "cheese sauce", "dip sauce", "toyomansi")
    )

    private val PITCHER_RULE = Rule(
        ingredientIds = setOf("ING-029", "ING-040", "ING-076", "ING-077", "ING-078", "ING-082"),
        nameKeywords = listOf("iced tea base", "lemonade base", "cucumber syrup", "four seasons", "water")
    )

    private val rulesByNormalizedProductCategory: Map<String, Rule> = mapOf(
        "burgers" to BURGERS_RULE,
        "milktea" to MILK_TEA_RULE,
        "fruittea" to FRUIT_TEA_RULE,
        "cremachee" to CREMACHEE_RULE,
        "coffee" to COFFEE_RULE,
        "noncoffee" to NON_COFFEE_RULE,
        "brevecoffee" to BREVE_RULE,
        "specialdrinks" to COFFEE_RULE,
        "specialdrink" to COFFEE_RULE,
        "coffeefrappes" to COFFEE_FRAPPE_RULE,
        "coffeefrappe" to COFFEE_FRAPPE_RULE,
        "noncoffeefrappes" to NON_COFFEE_FRAPPE_RULE,
        "noncoffeefrappe" to NON_COFFEE_FRAPPE_RULE,
        "frappes" to COFFEE_FRAPPE_RULE,
        "frappe" to COFFEE_FRAPPE_RULE,
        "frappuccino" to COFFEE_FRAPPE_RULE,
        "lemonades" to LEMONADE_RULE,
        "lemonade" to LEMONADE_RULE,
        "smoothies" to SMOOTHIE_RULE,
        "smoothie" to SMOOTHIE_RULE,
        "wings" to WINGS_RULE,
        "ricemeals" to RICE_MEAL_RULE,
        "ricemeal" to RICE_MEAL_RULE,
        "pasta" to PASTA_RULE,
        "appetizersandsides" to SIDES_RULE,
        "appetizers" to SIDES_RULE,
        "sides" to SIDES_RULE,
        "pitcherdrinks" to PITCHER_RULE,
        "pitcher" to PITCHER_RULE
    )

    fun filterForProductCategory(
        productCategoryName: String?,
        ingredients: List<Ingredient>
    ): List<Ingredient> {
        if (ingredients.isEmpty()) return ingredients

        val rule = rulesByNormalizedProductCategory[normalize(productCategoryName)]
            ?: return ingredients

        val filtered = ingredients.filter { ingredient ->
            ingredient.id in rule.ingredientIds || rule.nameKeywords.any { keyword ->
                ingredient.name.contains(keyword, ignoreCase = true)
            }
        }

        return if (filtered.isEmpty()) ingredients else filtered
    }

    private fun normalize(value: String?): String {
        return value
            .orEmpty()
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]"), "")
    }
}
