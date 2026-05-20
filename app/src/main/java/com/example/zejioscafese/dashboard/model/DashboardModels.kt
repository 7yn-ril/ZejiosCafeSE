package com.example.zejioscafese.dashboard.model

import androidx.annotation.StringRes
import com.example.zejioscafese.R

data class DashboardMetric(
    val title: String,
    val value: String,
    val delta: String,
    val positive: Boolean
)

data class DashboardSnapshot(
    val salesMetric: DashboardMetric,
    val ordersMetric: DashboardMetric,
    val profitMetric: DashboardMetric,
    val activeOrdersMetric: DashboardMetric,
    val lowStockMetric: DashboardMetric,
    val insights: List<DashboardInsight>,
    val topItems: List<DashboardTopItem>,
    val alerts: List<DashboardAlert>,
    val charts: Map<DashboardPeriod, List<DashboardChartPoint>>,
    val recentOrders: List<DashboardRecentOrder>
) {

    companion object {
        fun empty(): DashboardSnapshot {
            val emptyCharts = DashboardPeriod.entries.associateWith { emptyList<DashboardChartPoint>() }
            return DashboardSnapshot(
                salesMetric = DashboardMetric(
                    title = "Total Sales Today",
                    value = "PHP 0.00",
                    delta = "No sales yet today",
                    positive = true
                ),
                ordersMetric = DashboardMetric(
                    title = "Total Orders Today",
                    value = "0",
                    delta = "No completed orders yet",
                    positive = true
                ),
                profitMetric = DashboardMetric(
                    title = "Net Profit",
                    value = "PHP 0.00",
                    delta = "No profit recorded yet",
                    positive = true
                ),
                activeOrdersMetric = DashboardMetric(
                    title = "Active Orders",
                    value = "0",
                    delta = "No active orders right now",
                    positive = true
                ),
                lowStockMetric = DashboardMetric(
                    title = "Low Stock Items",
                    value = "0",
                    delta = "All ingredients are above minimum stock",
                    positive = true
                ),
                insights = emptyList(),
                topItems = emptyList(),
                alerts = emptyList(),
                charts = emptyCharts,
                recentOrders = emptyList()
            )
        }
    }
}

data class DashboardRecentOrder(
    val orderNumber: String,
    val customerName: String,
    val itemsPreview: String,
    val items: List<DashboardRecentOrderItem>,
    val total: Double,
    val status: String
)

data class DashboardRecentOrderItem(
    val productName: String,
    val variantName: String,
    val quantity: Int,
    val lineTotal: Double
)

data class DashboardInsight(
    val title: String,
    val value: String,
    val supportingText: String
)

data class DashboardTopItem(
    val name: String,
    val orders: Int,
    val revenue: Double,
    val imageUrl: String
)

data class DashboardAlert(
    val title: String,
    val detail: String,
    val level: AlertLevel
)

data class DashboardChartPoint(
    val label: String,
    val sales: Float
)

enum class AlertLevel {
    WARNING,
    CRITICAL
}

enum class DashboardPeriod(
    @StringRes val labelRes: Int
) {
    HOURLY(R.string.hourly),
    DAILY(R.string.daily),
    WEEKLY(R.string.weekly),
    MONTHLY(R.string.monthly),
    YEARLY(R.string.yearly)
}
