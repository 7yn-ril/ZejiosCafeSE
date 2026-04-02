package com.example.zejioscafese.dashboard.model

data class DashboardMetric(
    val title: String,
    val value: String,
    val delta: String,
    val positive: Boolean
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

enum class DashboardPeriod {
    DAILY,
    WEEKLY,
    MONTHLY
}
