package com.example.zejioscafese.reports.data.model

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Raw, filter-agnostic snapshot of the orders and order-items that fall
 * inside a [com.example.zejioscafese.reports.data.repository.ReportsRepository.ReportDateWindow].
 *
 * The repository fetches this once per range change; the
 * [com.example.zejioscafese.reports.data.ReportsAggregator] then slices it
 * for whatever bucket (single day/hour/week) and product-group filter the
 * UI is currently showing. Keeps Supabase requests off the hot path of
 * filter toggles.
 */
data class ReportsDataset(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val timelineGranularity: TimelineGranularity,
    val zoneId: ZoneId,
    val orders: List<DatasetOrder>,
    val items: List<DatasetOrderItem>,
    val categoryByProductId: Map<String, String>
)

enum class TimelineGranularity { HOURLY, DAILY, WEEKLY, MONTHLY, YEARLY }

data class DatasetOrder(
    val orderId: String,
    val orderNumber: String,
    val createdAt: ZonedDateTime,
    val localDate: LocalDate,
    val total: Double,
    val subtotal: Double,
    val statusDisplay: String,
    val discountLabel: String?,
    val discountPercent: Double?,
    val discountAmount: Double,
    val paymentMethod: String,
    val orderType: String
)

data class DatasetOrderItem(
    val orderId: String,
    val productId: String,
    val productName: String,
    val variantName: String,
    val quantity: Int,
    val unitPrice: Double,
    val lineTotal: Double
)
