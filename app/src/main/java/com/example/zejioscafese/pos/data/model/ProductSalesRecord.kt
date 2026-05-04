package com.example.zejioscafese.pos.data.model

data class ProductSalesRecord(
    val productName: String,
    val totalRevenue: Double,
    val itemsSold: Int,
    val percentageOfTotal: Double
)
