package com.example.zejioscafese.reports.data.model

import java.util.Locale

enum class ProductGroupFilter { ALL, FOOD, DRINKS }

object ProductGroupClassifier {

    // Keyword match against the category name. Anything that looks like a
    // beverage (milk tea, coffee, smoothie, etc.) is DRINKS; everything
    // else falls through to FOOD. Kept loose on purpose so newly-added
    // drink categories don't need a code change unless their name is
    // unusual.
    private val DRINK_KEYWORDS = listOf(
        "milk tea",
        "milktea",
        "non-coffee",
        "non coffee",
        "coffee",
        "tea",
        "beverage",
        "drink",
        "smoothie",
        "juice",
        "soda",
        "shake",
        "frappe",
        "latte",
        "espresso"
    )

    fun matches(filter: ProductGroupFilter, categoryName: String): Boolean {
        if (filter == ProductGroupFilter.ALL) return true
        val isDrink = isDrinkCategory(categoryName)
        return when (filter) {
            ProductGroupFilter.FOOD -> !isDrink
            ProductGroupFilter.DRINKS -> isDrink
            ProductGroupFilter.ALL -> true
        }
    }

    fun isDrinkCategory(categoryName: String): Boolean {
        val lower = categoryName.lowercase(Locale.US)
        return DRINK_KEYWORDS.any { keyword -> lower.contains(keyword) }
    }
}
