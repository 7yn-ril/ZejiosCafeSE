package com.example.zejioscafese.pos.data.model

data class DailySalesRecord(
    val date: String,
    val totalSales: Double,
    val totalOrders: Int,
    val averageOrderValue: Double
)
