package com.example.zejioscafese.pos.data.model

import java.util.Locale

object IngredientUnits {
    const val PCS = "pcs"
    const val ML = "mL"

    val options: List<String> = listOf(PCS, ML)

    fun normalize(unit: String): String {
        return when (unit.trim().lowercase(Locale.US)) {
            "ml",
            "milliliter",
            "milliliters",
            "millilitre",
            "millilitres",
            "l",
            "liter",
            "liters",
            "litre",
            "litres",
            "g",
            "gram",
            "grams",
            "kg",
            "kilogram",
            "kilograms",
            "shot",
            "shots" -> ML

            "pc",
            "pcs",
            "piece",
            "pieces",
            "bottle",
            "bottles",
            "serving",
            "servings" -> PCS

            else -> PCS
        }
    }

    fun isMl(unit: String): Boolean {
        return normalize(unit) == ML
    }
}
