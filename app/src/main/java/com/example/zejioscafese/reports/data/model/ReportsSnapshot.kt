package com.example.zejioscafese.reports.data.model

import com.example.zejioscafese.pos.data.model.CategorySalesRecord

data class ReportsSnapshot(
    val totalRevenue: Double,
    val totalOrders: Int,
    val averageOrderValue: Double,
    val bestCategory: String,
    val salesByDateRange: List<SalesTimelinePoint>,
    val salesByCategory: List<CategorySalesRecord>,
    val transactions: List<ReportTransaction>
)
