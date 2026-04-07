package com.example.zejioscafese.reports.data.model

data class ReportTransaction(
    val orderId: String,
    val items: String,
    val total: Double,
    val status: String,
    val date: String
)
