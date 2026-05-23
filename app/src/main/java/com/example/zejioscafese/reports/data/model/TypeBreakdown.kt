package com.example.zejioscafese.reports.data.model

/**
 * Generic count-based breakdown row. Used for the Order Type and Payment
 * Method mini-bars next to the Sales-by-Category donut. Percentage is
 * order-count based (not revenue-weighted) since "75% of orders were
 * Dine-In" reads more naturally than the revenue equivalent for these
 * dimensions.
 */
data class TypeBreakdown(
    val label: String,
    val count: Int,
    val revenue: Double,
    val percentage: Double
)
