package com.example.zejioscafese.reports.data.model

data class SalesTimelinePoint(
    val label: String,
    val totalSales: Double,
    val totalOrders: Int,
    val averageOrderValue: Double
)
