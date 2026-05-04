package com.example.zejioscafese.orders.model

data class CafeOrder(
    val id: String,
    val customerName: String,
    val itemsSummary: String,
    val itemCount: Int,
    val timeLabel: String,
    val status: CafeOrderStatus,
    val total: Double,
    val initials: String,
    val orderedItems: List<String> = emptyList(),
    val orderedItemVariantIds: List<String> = emptyList(),
    val completedItemVariantIds: Set<String> = emptySet(),
    val createdAtMillis: Long = 0L,
    val completedAtMillis: Long? = null
)

enum class CafeOrderStatus {
    PENDING,
    PREPARING,
    COMPLETED
}
