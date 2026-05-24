package com.example.zejioscafese.orders.model

// CHANGE: Orders — paymentMethod and orderType added so the Orders list
// can render the Take Out badge and apply QR Ph / Take Out filters.
// The strings mirror the lowercase tokens stored in Supabase ("cash" |
// "paymongo" | ..., "dine_in" | "takeout" | "delivery").
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
    // CHANGE: Partial completion — items whose recipe ingredients have been
    // subtracted from stock. The items dialog renders these as a static
    // green check (no editable checkbox) since they're effectively locked in.
    val deductedItemVariantIds: Set<String> = emptySet(),
    val createdAtMillis: Long = 0L,
    val completedAtMillis: Long? = null,
    val paymentMethod: String = "cash",
    val paymentProvider: String? = null,
    val paymentStatus: String? = null,
    val paymentReference: String? = null,
    val orderType: String = "dine_in",
    val subtotal: Double = total,
    val tax: Double = 0.0,
    val lineItems: List<CafeOrderLine> = emptyList(),
    val discountLabel: String? = null,
    val discountAmount: Double = 0.0
) {
    val isTakeout: Boolean get() = orderType.equals("takeout", ignoreCase = true)
    // PayMongo-routed orders carry the actual instrument (gcash / maya
    // / card) in paymentMethod, so we identify "this went through the
    // gateway" via the provider tag, not the method.
    val isPayMongo: Boolean get() = paymentProvider.equals("paymongo", ignoreCase = true)
    val isQrPhPayment: Boolean
        get() = paymentMethod.lowercase() in setOf("qrph", "qr_ph", "gcash", "maya", "paymaya")
}

data class CafeOrderLine(
    val productName: String,
    val variantName: String,
    val quantity: Int,
    val unitPrice: Double,
    val lineTotal: Double
)

enum class CafeOrderStatus {
    PENDING,
    PREPARING,
    COMPLETED,
    CANCELLED
}
