package com.example.zejioscafese.pos.data.model

data class Ingredient(
    val id: String,
    val name: String,
    val category: String,          // e.g. "Dairy", "Produce", "Dry Goods"
    val unit: String,              // e.g. "kg", "L", "pcs", "g"
    val currentStock: Double,
    val minimumStock: Double,      // threshold for low-stock warning
    val costPerUnit: Double,       // in PHP
    val lastRestocked: String      // date string e.g. "2025-04-01"
)
