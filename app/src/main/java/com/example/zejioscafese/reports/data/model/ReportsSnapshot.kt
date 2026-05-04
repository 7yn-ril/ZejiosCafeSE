package com.example.zejioscafese.reports.data.model

import com.example.zejioscafese.pos.data.model.CategorySalesRecord
import com.example.zejioscafese.pos.data.model.ProductSalesRecord

data class ReportsSnapshot(
    val totalRevenue: Double,
    val totalOrders: Int,
    val averageOrderValue: Double,
    val bestProduct: String,
    val salesByDateRange: List<SalesTimelinePoint>,
    val salesByCategory: List<CategorySalesRecord>,
    val salesByProduct: List<ProductSalesRecord>,
    val transactions: List<ReportTransaction>
)
