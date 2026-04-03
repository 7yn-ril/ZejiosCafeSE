package com.example.zejioscafese.dashboard.data

import com.example.zejioscafese.dashboard.model.AlertLevel
import com.example.zejioscafese.dashboard.model.DashboardAlert
import com.example.zejioscafese.dashboard.model.DashboardChartPoint
import com.example.zejioscafese.dashboard.model.DashboardInsight
import com.example.zejioscafese.dashboard.model.DashboardPeriod
import com.example.zejioscafese.dashboard.model.DashboardTopItem

object DashboardSampleData {

    val insights = listOf(
        DashboardInsight("Peak Hours", "2PM - 5PM", "Orders jump 28% during the afternoon rush."),
        DashboardInsight("Best Category", "Drinks", "Coffee and frappe items drive most repeat purchases."),
        DashboardInsight("Average Order", "PHP 146", "Strong add-on behavior from pastry bundles."),
        DashboardInsight("Best Channel", "Dine-in", "Most profitable channel based on today's ticket mix.")
    )

    val topItems = listOf(
        DashboardTopItem(
            name = "Iced Caramel Latte",
            orders = 47,
            revenue = 7755.0,
            imageUrl = "https://images.unsplash.com/photo-1517701604599-bb29b565090c?auto=format&fit=crop&w=800&q=80"
        ),
        DashboardTopItem(
            name = "Cappuccino",
            orders = 34,
            revenue = 5100.0,
            imageUrl = "https://images.unsplash.com/photo-1509042239860-f550ce710b93?auto=format&fit=crop&w=800&q=80"
        ),
        DashboardTopItem(
            name = "Chicken Pesto Panini",
            orders = 21,
            revenue = 4935.0,
            imageUrl = "https://images.unsplash.com/photo-1550317138-10000687a72b?auto=format&fit=crop&w=800&q=80"
        ),
        DashboardTopItem(
            name = "Butter Croissant",
            orders = 29,
            revenue = 2755.0,
            imageUrl = "https://images.unsplash.com/photo-1509440159596-0249088772ff?auto=format&fit=crop&w=800&q=80"
        )
    )

    val alerts = listOf(
        DashboardAlert("Low stock: Fresh milk", "Only 8 liters remaining for today's demand.", AlertLevel.WARNING),
        DashboardAlert("Low stock: Coffee beans", "Roast blend is down to 3 refill bins.", AlertLevel.CRITICAL),
        DashboardAlert("Reorder soon: Matcha powder", "Projected to run out within 2 business days.", AlertLevel.WARNING)
    )

    fun chart(period: DashboardPeriod): List<DashboardChartPoint> {
        return when (period) {
            DashboardPeriod.DAILY -> listOf(
                DashboardChartPoint("8AM", 2.4f),
                DashboardChartPoint("10AM", 4.8f),
                DashboardChartPoint("12PM", 8.7f),
                DashboardChartPoint("2PM", 11.1f),
                DashboardChartPoint("4PM", 13.4f),
                DashboardChartPoint("6PM", 10.2f),
                DashboardChartPoint("8PM", 7.1f)
            )
            DashboardPeriod.WEEKLY -> listOf(
                DashboardChartPoint("Mon", 12.5f),
                DashboardChartPoint("Tue", 14.2f),
                DashboardChartPoint("Wed", 13.1f),
                DashboardChartPoint("Thu", 15.6f),
                DashboardChartPoint("Fri", 18.4f),
                DashboardChartPoint("Sat", 22.8f),
                DashboardChartPoint("Sun", 20.3f)
            )
            DashboardPeriod.MONTHLY -> listOf(
                DashboardChartPoint("W1", 72f),
                DashboardChartPoint("W2", 78f),
                DashboardChartPoint("W3", 81f),
                DashboardChartPoint("W4", 94f)
            )
        }
    }
}
