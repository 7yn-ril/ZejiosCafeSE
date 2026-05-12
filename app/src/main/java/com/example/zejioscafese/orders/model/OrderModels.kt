package com.example.zejioscafese.orders.model

// CHANGE: Orders — paymentMethod and orderType added so the Orders list
// can render the Take Out badge and apply GCash / Take Out filters. The
// strings mirror the lowercase tokens stored in Supabase ("cash" |
// "gcash" | ..., "dine_in" | "takeout" | "delivery").
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
    val completedAtMillis: Long? = null,
    val paymentMethod: String = "cash",
    val orderType: String = "dine_in"
) {
    val isTakeout: Boolean get() = orderType.equals("takeout", ignoreCase = true)
    val isGcash: Boolean get() = paymentMethod.equals("gcash", ignoreCase = true)
}

enum class CafeOrderStatus {
    PENDING,
    PREPARING,
    COMPLETED
}
