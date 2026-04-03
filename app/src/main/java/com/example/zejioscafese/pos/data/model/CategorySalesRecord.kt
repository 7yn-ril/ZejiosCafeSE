package com.example.zejioscafese.pos.data.model

data class CategorySalesRecord(
    val categoryName: String,
    val totalRevenue: Double,
    val itemsSold: Int,
    val percentageOfTotal: Double
)
