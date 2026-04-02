package com.example.zejioscafese.orders.model

data class CafeOrder(
    val id: String,
    val customerName: String,
    val tableLabel: String,
    val itemsSummary: String,
    val itemCount: Int,
    val timeLabel: String,
    val status: CafeOrderStatus,
    val total: Double,
    val initials: String
)

enum class CafeOrderStatus {
    PENDING,
    PREPARING,
    COMPLETED
}
