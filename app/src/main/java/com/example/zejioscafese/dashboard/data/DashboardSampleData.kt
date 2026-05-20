package com.example.zejioscafese.dashboard.data

import com.example.zejioscafese.dashboard.model.AlertLevel
import com.example.zejioscafese.dashboard.model.DashboardAlert
import com.example.zejioscafese.dashboard.model.DashboardChartPoint
import com.example.zejioscafese.dashboard.model.DashboardInsight
import com.example.zejioscafese.dashboard.model.DashboardPeriod
import com.example.zejioscafese.dashboard.model.DashboardTopItem
import com.example.zejioscafese.pos.data.local.ProductImageResolver

object DashboardSampleData {

    val insights = listOf(
        DashboardInsight("Best Category", "Drinks", "Coffee and frappe items drive most repeat purchases."),
        DashboardInsight("Best Channel", "Dine-in", "Most profitable channel based on today's ticket mix.")
    )

    val topItems = listOf(
        DashboardTopItem(
            name = "Caramel Macchiato",
            orders = 47,
            revenue = 7755.0,
            imageUrl = ProductImageResolver.resolve("Caramel Macchiato").orEmpty()
        ),
        DashboardTopItem(
            name = "Latte",
            orders = 34,
            revenue = 5100.0,
            imageUrl = ProductImageResolver.resolve("Latte").orEmpty()
        ),
        DashboardTopItem(
            name = "Chicken Poppers",
            orders = 21,
            revenue = 4935.0,
            imageUrl = ProductImageResolver.resolve("Chicken Poppers").orEmpty()
        )
    )

    val alerts = listOf(
        DashboardAlert("Low stock: Fresh milk", "Only 8 liters remaining for today's demand.", AlertLevel.WARNING),
        DashboardAlert("Low stock: Coffee beans", "Roast blend is down to 3 refill bins.", AlertLevel.CRITICAL),
        DashboardAlert("Reorder soon: Matcha powder", "Projected to run out within 2 business days.", AlertLevel.WARNING)
    )

    fun chart(period: DashboardPeriod): List<DashboardChartPoint> {
        return when (period) {
            DashboardPeriod.HOURLY -> listOf(
                DashboardChartPoint("8AM", 2.4f),
                DashboardChartPoint("10AM", 4.8f),
                DashboardChartPoint("12PM", 8.7f),
                DashboardChartPoint("2PM", 11.1f),
                DashboardChartPoint("4PM", 13.4f),
                DashboardChartPoint("6PM", 10.2f),
                DashboardChartPoint("8PM", 7.1f)
            )
            DashboardPeriod.DAILY -> listOf(
                DashboardChartPoint("Mon", 12.5f),
                DashboardChartPoint("Tue", 14.2f),
                DashboardChartPoint("Wed", 13.1f),
                DashboardChartPoint("Thu", 15.6f),
                DashboardChartPoint("Fri", 18.4f),
                DashboardChartPoint("Sat", 22.8f),
                DashboardChartPoint("Sun", 20.3f)
            )
            DashboardPeriod.WEEKLY -> listOf(
                DashboardChartPoint("W1", 72f),
                DashboardChartPoint("W2", 78f),
                DashboardChartPoint("W3", 81f),
                DashboardChartPoint("W4", 94f)
            )
            DashboardPeriod.MONTHLY -> listOf(
                DashboardChartPoint("Jan", 248f),
                DashboardChartPoint("Feb", 262f),
                DashboardChartPoint("Mar", 279f),
                DashboardChartPoint("Apr", 301f)
            )
            DashboardPeriod.YEARLY -> listOf(
                DashboardChartPoint("2022", 2100f),
                DashboardChartPoint("2023", 2480f),
                DashboardChartPoint("2024", 2790f),
                DashboardChartPoint("2025", 3150f),
                DashboardChartPoint("2026", 3380f)
            )
        }
    }
}
