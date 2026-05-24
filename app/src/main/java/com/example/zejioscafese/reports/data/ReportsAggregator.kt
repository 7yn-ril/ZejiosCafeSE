package com.example.zejioscafese.reports.data

import com.example.zejioscafese.pos.data.model.CategorySalesRecord
import com.example.zejioscafese.pos.data.model.ProductSalesRecord
import com.example.zejioscafese.reports.data.model.DatasetOrder
import com.example.zejioscafese.reports.data.model.DatasetOrderItem
import com.example.zejioscafese.reports.data.model.ProductGroupClassifier
import com.example.zejioscafese.reports.data.model.ProductGroupFilter
import com.example.zejioscafese.reports.data.model.ReportTransaction
import com.example.zejioscafese.reports.data.model.ReportsDataset
import com.example.zejioscafese.reports.data.model.SalesTimelinePoint
import com.example.zejioscafese.reports.data.model.TimelineGranularity
import com.example.zejioscafese.reports.data.model.TypeBreakdown
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Pure aggregation over a [ReportsDataset]. Lets the ViewModel re-compute
 * the visible KPIs whenever the user picks a different bucket (e.g. clicks
 * a bar in the revenue chart) or flips the Food/Drinks filter, without
 * hitting Supabase again.
 *
 * **Filter semantics**: every revenue figure (totals, category /
 * product breakdowns, timeline buckets) is computed from each item's
 * *effective* line total — the raw line total scaled by its order's
 * discount factor (`order.total / order.subtotal`). That way discounts
 * stay reflected in the numbers AND `FOOD + DRINKS = ALL` holds at every
 * level, so the two filters can never report a higher revenue than the
 * unfiltered view.
 *
 * An order is counted once under FOOD/DRINKS if it contains ≥1 item in
 * the filtered group.
 */
object ReportsAggregator {

    data class Result(
        val totalRevenue: Double,
        val totalOrders: Int,
        val averageOrderValue: Double,
        val bestProduct: String,
        val salesByDateRange: List<SalesTimelinePoint>,
        val salesByCategory: List<CategorySalesRecord>,
        val salesByProduct: List<ProductSalesRecord>,
        val salesByOrderType: List<TypeBreakdown>,
        val salesByPaymentMethod: List<TypeBreakdown>,
        val transactions: List<ReportTransaction>,
        // The chronological list of bucket labels (matches salesByDateRange).
        // The Fragment uses this to map a clicked bar's label back to the
        // bucket key the ViewModel selected.
        val bucketLabels: List<String>,
        // Y-axis ceiling the chart should use, derived from the
        // unfiltered (ALL) timeline. Flipping to a Food or Drinks filter
        // keeps this fixed, so the bars shrink to show how much of the
        // total each filter actually contributes — instead of every
        // filter re-scaling to fill the chart.
        val referenceMaxSales: Double
    )

    fun aggregate(
        dataset: ReportsDataset,
        selectedBucketLabel: String? = null,
        productGroupFilter: ProductGroupFilter = ProductGroupFilter.ALL
    ): Result {
        val isFiltered = productGroupFilter != ProductGroupFilter.ALL

        // discountFactor scales each item's raw line total down to what the
        // customer actually paid for it, by spreading the order's discount
        // (and any tax) proportionally across its items. Without this, a
        // FOOD/DRINKS filter sums pre-discount item subtotals while ALL
        // sums post-discount order totals — and FOOD ends up looking
        // larger than ALL, which is exactly the bug the user hit.
        val discountFactorByOrderId: Map<String, Double> = dataset.orders.associate { order ->
            val factor = if (order.subtotal > 0.0) order.total / order.subtotal else 1.0
            order.orderId to factor
        }
        fun effectiveRevenue(item: DatasetOrderItem): Double {
            val factor = discountFactorByOrderId[item.orderId] ?: 1.0
            return item.lineTotal * factor
        }

        // Filter items by category group up front. The same predicate
        // determines which orders "count" under the filter.
        val filteredItems = dataset.items.filter { item ->
            val categoryName = dataset.categoryByProductId[item.productId].orEmpty()
            ProductGroupClassifier.matches(productGroupFilter, categoryName)
        }
        val orderIdsWithFilteredItems = filteredItems.map(DatasetOrderItem::orderId).toHashSet()

        val ordersInScope = if (isFiltered) {
            dataset.orders.filter { it.orderId in orderIdsWithFilteredItems }
        } else {
            dataset.orders
        }

        // Build full timeline (so the chart shows every bucket in the
        // window, even empty ones). This drives the bar chart regardless
        // of bucket selection.
        val timeline = buildTimeline(
            orders = ordersInScope,
            filteredItemsByOrderId = filteredItems.groupBy(DatasetOrderItem::orderId),
            effectiveRevenueOf = ::effectiveRevenue,
            startDate = dataset.startDate,
            endDate = dataset.endDate,
            granularity = dataset.timelineGranularity
        )
        val bucketLabels = timeline.map(SalesTimelinePoint::label)

        // Unfiltered timeline max — the Y-axis ceiling. When isFiltered
        // is true, this is the only way to keep the chart's scale stable
        // across All/Food/Drinks toggles. When false it equals
        // timeline.max, so the chart still uses headroom from niceMax().
        val referenceMaxSales = if (isFiltered) {
            buildTimeline(
                orders = dataset.orders,
                filteredItemsByOrderId = dataset.items.groupBy(DatasetOrderItem::orderId),
                effectiveRevenueOf = ::effectiveRevenue,
                startDate = dataset.startDate,
                endDate = dataset.endDate,
                granularity = dataset.timelineGranularity
            ).maxOfOrNull(SalesTimelinePoint::totalSales) ?: 0.0
        } else {
            timeline.maxOfOrNull(SalesTimelinePoint::totalSales) ?: 0.0
        }

        // Scope orders to the selected bucket if one was chosen.
        val activeOrders = if (selectedBucketLabel != null) {
            ordersInScope.filter { bucketLabelFor(it, dataset.timelineGranularity) == selectedBucketLabel }
        } else {
            ordersInScope
        }
        val activeOrderIds = activeOrders.map(DatasetOrder::orderId).toHashSet()
        val activeItems = filteredItems.filter { it.orderId in activeOrderIds }

        // Single revenue path for every filter. For ALL filter,
        // activeItems = every item, and the sum collapses back to
        // sum(order.total) — preserving the old unfiltered behavior.
        val totalRevenue = activeItems.sumOf(::effectiveRevenue)
        val totalOrders = activeOrders.size
        val averageOrderValue = if (totalOrders > 0) totalRevenue / totalOrders else 0.0

        val itemsByCategory = activeItems.groupBy { item ->
            dataset.categoryByProductId[item.productId].orEmpty().ifBlank { UNCATEGORIZED }
        }
        val salesByCategory = itemsByCategory.map { (categoryName, items) ->
            val categoryRevenue = items.sumOf(::effectiveRevenue)
            CategorySalesRecord(
                categoryName = categoryName,
                totalRevenue = categoryRevenue,
                itemsSold = items.sumOf(DatasetOrderItem::quantity),
                percentageOfTotal = if (totalRevenue > 0.0) {
                    (categoryRevenue / totalRevenue) * 100.0
                } else {
                    0.0
                }
            )
        }.sortedByDescending(CategorySalesRecord::totalRevenue)

        val salesByProduct = activeItems
            .groupBy { item -> productDisplayName(item) }
            .map { (productName, items) ->
                val productRevenue = items.sumOf(::effectiveRevenue)
                ProductSalesRecord(
                    productName = productName,
                    totalRevenue = productRevenue,
                    itemsSold = items.sumOf(DatasetOrderItem::quantity),
                    percentageOfTotal = if (totalRevenue > 0.0) {
                        (productRevenue / totalRevenue) * 100.0
                    } else {
                        0.0
                    }
                )
            }
            .sortedByDescending(ProductSalesRecord::totalRevenue)

        val transactions = activeOrders
            .sortedByDescending(DatasetOrder::createdAt)
            .map { order ->
                val orderItems = activeItems.filter { it.orderId == order.orderId }
                val orderedItems = orderItems.map(::detailedDisplayLabel)
                val effectiveTotal = orderItems.sumOf(::effectiveRevenue)
                val effectiveSubtotal = orderItems.sumOf(DatasetOrderItem::lineTotal)
                ReportTransaction(
                    orderId = order.orderNumber,
                    items = orderedItems.joinToString(separator = "; ").ifBlank { NO_ITEMS },
                    total = if (isFiltered) effectiveTotal else order.total,
                    status = order.statusDisplay,
                    date = order.createdAt.format(TRANSACTION_DATE_FORMATTER),
                    orderType = order.orderType.toDisplayToken(),
                    paymentMethod = order.paymentReportLabel(),
                    itemCount = orderItems.sumOf(DatasetOrderItem::quantity),
                    subtotal = if (isFiltered) effectiveSubtotal else order.subtotal,
                    discountLabel = order.discountLabel,
                    discountPercent = order.discountPercent,
                    discountAmount = if (isFiltered) {
                        (effectiveSubtotal - effectiveTotal).coerceAtLeast(0.0)
                    } else {
                        order.discountAmount
                    }
                )
            }

        // Per-order revenue under the active filter — used by the order
        // type / payment method legends so they reflect the same
        // discount-adjusted picture as the KPI strip.
        val activeRevenueByOrderId: Map<String, Double> = activeItems
            .groupBy(DatasetOrderItem::orderId)
            .mapValues { (_, items) -> items.sumOf(::effectiveRevenue) }

        val salesByOrderType = countBreakdown(
            orders = activeOrders,
            revenueByOrderId = activeRevenueByOrderId,
            classify = { it.orderType.toDisplayToken().ifBlank { "Dine In" } }
        )
        val salesByPaymentMethod = countBreakdown(
            orders = activeOrders,
            revenueByOrderId = activeRevenueByOrderId,
            classify = ::paymentBreakdownLabel
        )

        return Result(
            totalRevenue = totalRevenue,
            totalOrders = totalOrders,
            averageOrderValue = averageOrderValue,
            bestProduct = salesByProduct.firstOrNull()?.productName ?: NO_PRODUCT,
            salesByDateRange = timeline,
            salesByCategory = salesByCategory,
            salesByProduct = salesByProduct,
            salesByOrderType = salesByOrderType,
            salesByPaymentMethod = salesByPaymentMethod,
            transactions = transactions,
            bucketLabels = bucketLabels,
            referenceMaxSales = referenceMaxSales
        )
    }

    private fun countBreakdown(
        orders: List<DatasetOrder>,
        revenueByOrderId: Map<String, Double>,
        classify: (DatasetOrder) -> String
    ): List<TypeBreakdown> {
        if (orders.isEmpty()) return emptyList()
        val totalCount = orders.size
        return orders.groupBy(classify)
            .map { (label, group) ->
                val count = group.size
                TypeBreakdown(
                    label = label,
                    count = count,
                    revenue = group.sumOf { revenueByOrderId[it.orderId] ?: 0.0 },
                    percentage = (count.toDouble() / totalCount) * 100.0
                )
            }
            .sortedByDescending(TypeBreakdown::count)
    }

    /**
     * Resolves the default bucket label the UI should select when a range
     * first loads. Picks the bucket containing "now" if it exists in the
     * timeline; otherwise the last (most recent) bucket.
     */
    fun defaultBucketLabel(
        bucketLabels: List<String>,
        granularity: TimelineGranularity,
        today: LocalDate = LocalDate.now()
    ): String? {
        if (bucketLabels.isEmpty()) return null
        val todayLabel = when (granularity) {
            TimelineGranularity.HOURLY -> LocalTime.now().withMinute(0).withSecond(0).withNano(0)
                .format(HOURLY_LABEL_FORMATTER).uppercase(Locale.getDefault())
            TimelineGranularity.DAILY -> today.format(DAILY_LABEL_FORMATTER)
            TimelineGranularity.WEEKLY -> today
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .format(WEEKLY_LABEL_FORMATTER)
            TimelineGranularity.MONTHLY -> YearMonth.from(today).format(MONTHLY_LABEL_FORMATTER)
            TimelineGranularity.YEARLY -> today.year.toString()
        }
        return bucketLabels.firstOrNull { it == todayLabel } ?: bucketLabels.last()
    }

    // ── Timeline building ────────────────────────────────────────────────

    private fun buildTimeline(
        orders: List<DatasetOrder>,
        filteredItemsByOrderId: Map<String, List<DatasetOrderItem>>,
        effectiveRevenueOf: (DatasetOrderItem) -> Double,
        startDate: LocalDate,
        endDate: LocalDate,
        granularity: TimelineGranularity
    ): List<SalesTimelinePoint> {
        return when (granularity) {
            TimelineGranularity.HOURLY -> hourly(orders, filteredItemsByOrderId, effectiveRevenueOf)
            TimelineGranularity.DAILY -> daily(orders, filteredItemsByOrderId, effectiveRevenueOf, startDate, endDate)
            TimelineGranularity.WEEKLY -> weekly(orders, filteredItemsByOrderId, effectiveRevenueOf, startDate, endDate)
            TimelineGranularity.MONTHLY -> monthly(orders, filteredItemsByOrderId, effectiveRevenueOf, startDate, endDate)
            TimelineGranularity.YEARLY -> yearly(orders, filteredItemsByOrderId, effectiveRevenueOf, startDate, endDate)
        }
    }

    private fun hourly(
        orders: List<DatasetOrder>,
        filteredItemsByOrderId: Map<String, List<DatasetOrderItem>>,
        effectiveRevenueOf: (DatasetOrderItem) -> Double
    ): List<SalesTimelinePoint> {
        val ordersByHour = orders.groupBy { it.createdAt.hour }
        return (0..23).map { hour ->
            val hourOrders = ordersByHour[hour].orEmpty()
            timelinePoint(
                label = LocalTime.of(hour, 0).format(HOURLY_LABEL_FORMATTER).uppercase(Locale.getDefault()),
                orders = hourOrders,
                filteredItemsByOrderId = filteredItemsByOrderId,
                effectiveRevenueOf = effectiveRevenueOf
            )
        }
    }

    private fun daily(
        orders: List<DatasetOrder>,
        filteredItemsByOrderId: Map<String, List<DatasetOrderItem>>,
        effectiveRevenueOf: (DatasetOrderItem) -> Double,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<SalesTimelinePoint> {
        val ordersByDate = orders.groupBy(DatasetOrder::localDate)
        return generateSequence(startDate) { current ->
            current.plusDays(1).takeIf { !it.isAfter(endDate) }
        }.map { date ->
            timelinePoint(
                label = date.format(DAILY_LABEL_FORMATTER),
                orders = ordersByDate[date].orEmpty(),
                filteredItemsByOrderId = filteredItemsByOrderId,
                effectiveRevenueOf = effectiveRevenueOf
            )
        }.toList()
    }

    private fun weekly(
        orders: List<DatasetOrder>,
        filteredItemsByOrderId: Map<String, List<DatasetOrderItem>>,
        effectiveRevenueOf: (DatasetOrderItem) -> Double,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<SalesTimelinePoint> {
        val ordersByWeek = orders.groupBy { order ->
            order.localDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }
        val startWeek = startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val endWeek = endDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return generateSequence(startWeek) { current ->
            current.plusWeeks(1).takeIf { !it.isAfter(endWeek) }
        }.map { weekStart ->
            timelinePoint(
                label = weekStart.format(WEEKLY_LABEL_FORMATTER),
                orders = ordersByWeek[weekStart].orEmpty(),
                filteredItemsByOrderId = filteredItemsByOrderId,
                effectiveRevenueOf = effectiveRevenueOf
            )
        }.toList()
    }

    private fun monthly(
        orders: List<DatasetOrder>,
        filteredItemsByOrderId: Map<String, List<DatasetOrderItem>>,
        effectiveRevenueOf: (DatasetOrderItem) -> Double,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<SalesTimelinePoint> {
        val ordersByMonth = orders.groupBy { order -> YearMonth.from(order.localDate) }
        val startMonth = YearMonth.from(startDate)
        val endMonth = YearMonth.from(endDate)
        return generateSequence(startMonth) { current ->
            current.plusMonths(1).takeIf { !it.isAfter(endMonth) }
        }.map { month ->
            timelinePoint(
                label = month.format(MONTHLY_LABEL_FORMATTER),
                orders = ordersByMonth[month].orEmpty(),
                filteredItemsByOrderId = filteredItemsByOrderId,
                effectiveRevenueOf = effectiveRevenueOf
            )
        }.toList()
    }

    private fun yearly(
        orders: List<DatasetOrder>,
        filteredItemsByOrderId: Map<String, List<DatasetOrderItem>>,
        effectiveRevenueOf: (DatasetOrderItem) -> Double,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<SalesTimelinePoint> {
        val ordersByYear = orders.groupBy { order -> order.localDate.year }
        return (startDate.year..endDate.year).map { year ->
            timelinePoint(
                label = year.toString(),
                orders = ordersByYear[year].orEmpty(),
                filteredItemsByOrderId = filteredItemsByOrderId,
                effectiveRevenueOf = effectiveRevenueOf
            )
        }
    }

    private fun timelinePoint(
        label: String,
        orders: List<DatasetOrder>,
        filteredItemsByOrderId: Map<String, List<DatasetOrderItem>>,
        effectiveRevenueOf: (DatasetOrderItem) -> Double
    ): SalesTimelinePoint {
        // Always sum per-item effective revenue. For ALL filter, every
        // item is present and the per-order sum collapses back to
        // order.total. For FOOD/DRINKS, only the relevant items
        // contribute, and they're scaled by the same discount factor as
        // the headline KPI — so the chart and the KPI never disagree.
        val totalSales = orders.sumOf { order ->
            filteredItemsByOrderId[order.orderId].orEmpty().sumOf(effectiveRevenueOf)
        }
        val totalOrders = orders.size
        return SalesTimelinePoint(
            label = label,
            totalSales = totalSales,
            totalOrders = totalOrders,
            averageOrderValue = if (totalOrders > 0) totalSales / totalOrders else 0.0
        )
    }

    fun bucketLabelFor(order: DatasetOrder, granularity: TimelineGranularity): String {
        return when (granularity) {
            TimelineGranularity.HOURLY ->
                LocalTime.of(order.createdAt.hour, 0)
                    .format(HOURLY_LABEL_FORMATTER)
                    .uppercase(Locale.getDefault())
            TimelineGranularity.DAILY -> order.localDate.format(DAILY_LABEL_FORMATTER)
            TimelineGranularity.MONTHLY -> YearMonth.from(order.localDate).format(MONTHLY_LABEL_FORMATTER)
            TimelineGranularity.WEEKLY -> order.localDate
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .format(WEEKLY_LABEL_FORMATTER)
            TimelineGranularity.YEARLY -> order.localDate.year.toString()
        }
    }

    private fun productDisplayName(item: DatasetOrderItem): String {
        return when {
            item.variantName.equals(STANDARD_VARIANT, ignoreCase = true) -> item.productName
            item.variantName.equals(COMBO_VARIANT, ignoreCase = true) -> item.productName
            else -> "${item.productName} (${item.variantName})"
        }
    }

    private fun detailedDisplayLabel(item: DatasetOrderItem): String {
        return String.format(
            Locale.US,
            "%d x %s @ PHP %,.2f = PHP %,.2f",
            item.quantity,
            productDisplayName(item),
            item.unitPrice,
            item.lineTotal
        )
    }

    private fun String.toDisplayToken(): String {
        val normalized = trim().lowercase(Locale.US)
        if (normalized.isQrPhToken()) return "QR Ph"
        if (normalized == "card") return "Card"
        if (normalized == "paymongo") return "PayMongo"
        return normalized
            .split("_", "-", " ")
            .filter(String::isNotBlank)
            .joinToString(" ") { token ->
                token.lowercase(Locale.US).replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
                }
            }
    }

    // Legacy gateway rows used to store gcash/maya as the instrument.
    // The counter workflow is now dynamic QR Ph, so reports collapse
    // those old e-wallet tokens into the QR Ph bucket.
    private fun paymentBreakdownLabel(order: DatasetOrder): String {
        return order.paymentReportLabel().ifBlank { "Cash" }
    }

    private fun DatasetOrder.paymentReportLabel(): String {
        val normalizedMethod = paymentMethod.trim().lowercase(Locale.US)
        return if (normalizedMethod.isQrPhToken()) {
            "QR Ph"
        } else {
            paymentMethod.toDisplayToken()
        }
    }

    private fun String.isQrPhToken(): Boolean {
        return this in setOf("qrph", "qr_ph", "gcash", "maya", "paymaya")
    }

    private const val STANDARD_VARIANT = "standard"
    private const val COMBO_VARIANT = "combo"
    private const val UNCATEGORIZED = "Uncategorized"
    private const val NO_ITEMS = "No items"
    private const val NO_PRODUCT = "N/A"

    private val HOURLY_LABEL_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("ha", Locale.getDefault())
    private val DAILY_LABEL_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    private val WEEKLY_LABEL_FORMATTER: DateTimeFormatter = DAILY_LABEL_FORMATTER
    // Just the short month name — the Monthly view spans Jan-Dec of a
    // single calendar year, so no two bars can collide and the year
    // suffix would only add noise.
    private val MONTHLY_LABEL_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
    private val TRANSACTION_DATE_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())
}
