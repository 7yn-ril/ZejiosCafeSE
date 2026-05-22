package com.example.zejioscafese.reports.data.model

data class ReportTransaction(
    val orderId: String,
    val items: String,
    val total: Double,
    val status: String,
    val date: String,
    val orderType: String = "",
    val paymentMethod: String = "",
    val itemCount: Int = 0,
    val subtotal: Double = total,
    val discountLabel: String? = null,
    val discountPercent: Double? = null,
    val discountAmount: Double = 0.0
)
