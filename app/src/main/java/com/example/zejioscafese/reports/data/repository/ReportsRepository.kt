package com.example.zejioscafese.reports.data.repository

import com.example.zejioscafese.core.supabase.SupabaseProvider
import com.example.zejioscafese.core.supabase.SupabaseSessionHelper
import com.example.zejioscafese.pos.data.model.CategorySalesRecord
import com.example.zejioscafese.pos.data.model.ProductSalesRecord
import com.example.zejioscafese.pos.data.remote.dto.ProductVariantStockDto
import com.example.zejioscafese.reports.data.model.ReportTransaction
import com.example.zejioscafese.reports.data.model.ReportsSnapshot
import com.example.zejioscafese.reports.data.model.SalesTimelinePoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class ReportsRepository(
    private val clientProvider: () -> SupabaseClient = { SupabaseProvider.client }
) {

    enum class TimelineGranularity { HOURLY, DAILY, WEEKLY, MONTHLY }

    data class ReportDateWindow(
        val startDate: LocalDate,
        val endDate: LocalDate,
        val timelineGranularity: TimelineGranularity
    )

    private val supabaseClient: SupabaseClient
        get() = clientProvider()

    suspend fun fetchReportsSnapshot(
        dateWindow: ReportDateWindow,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ReportsSnapshot = SupabaseSessionHelper.withJwtRetry(supabaseClient) {
        buildReportsSnapshot(dateWindow, zoneId)
    }

    private suspend fun buildReportsSnapshot(
        dateWindow: ReportDateWindow,
        zoneId: ZoneId
    ): ReportsSnapshot {
        val (orderRows, orderItemRows, catalogRows) = coroutineScope {
            val orderRowsDeferred = async { fetchOrders() }
            val orderItemsDeferred = async { fetchOrderItems() }
            val catalogRowsDeferred = async { fetchCatalogRows() }

            Triple(
                orderRowsDeferred.await(),
                orderItemsDeferred.await(),
                catalogRowsDeferred.await()
            )
        }

        val categoryByProductId = catalogRows
            .associateBy(ProductVariantStockDto::productId, ProductVariantStockDto::categoryName)

        val ordersInRange = orderRows
            .mapNotNull { it.toOrderRecord(zoneId) }
            .filter { record ->
                record.isCompleted && record.localDate in dateWindow.startDate..dateWindow.endDate
            }
            .sortedByDescending(OrderRecord::createdAt)

        val orderIds = ordersInRange.map(OrderRecord::orderId).toHashSet()
        val itemsByOrderId = orderItemRows
            .filter { it.orderId in orderIds }
            .groupBy(OrderItemRowDto::orderId)

        val totalRevenue = ordersInRange.sumOf(OrderRecord::total)
        val totalOrders = ordersInRange.size
        val averageOrderValue = if (totalOrders > 0) totalRevenue / totalOrders else 0.0

        val salesByDate = buildSalesTimeline(
            orders = ordersInRange,
            dateWindow = dateWindow
        )

        val salesByCategory = itemsByOrderId
            .values
            .flatten()
            .groupBy { item ->
                categoryByProductId[item.productId].orEmpty().ifBlank { UNCATEGORIZED }
            }
            .map { (categoryName, items) ->
                val categoryRevenue = items.sumOf(OrderItemRowDto::orderItemLineTotal)
                CategorySalesRecord(
                    categoryName = categoryName,
                    totalRevenue = categoryRevenue,
                    itemsSold = items.sumOf(OrderItemRowDto::orderItemQuantity),
                    percentageOfTotal = if (totalRevenue > 0.0) {
                        (categoryRevenue / totalRevenue) * 100.0
                    } else {
                        0.0
                    }
                )
            }
            .sortedByDescending(CategorySalesRecord::totalRevenue)

        val salesByProduct = itemsByOrderId
            .values
            .flatten()
            .groupBy { item -> item.toProductDisplayName() }
            .map { (productName, items) ->
                val productRevenue = items.sumOf(OrderItemRowDto::orderItemLineTotal)
                ProductSalesRecord(
                    productName = productName,
                    totalRevenue = productRevenue,
                    itemsSold = items.sumOf(OrderItemRowDto::orderItemQuantity),
                    percentageOfTotal = if (totalRevenue > 0.0) {
                        (productRevenue / totalRevenue) * 100.0
                    } else {
                        0.0
                    }
                )
            }
            .sortedByDescending(ProductSalesRecord::totalRevenue)

        val transactions = ordersInRange.map { order ->
            val orderedItems = itemsByOrderId[order.orderId]
                .orEmpty()
                .map { item -> item.toDisplayLabel() }
            ReportTransaction(
                orderId = order.orderNumber,
                items = buildItemsPreview(orderedItems),
                total = order.total,
                status = order.statusDisplay,
                date = order.createdAt.format(TRANSACTION_DATE_FORMATTER)
            )
        }

        return ReportsSnapshot(
            totalRevenue = totalRevenue,
            totalOrders = totalOrders,
            averageOrderValue = averageOrderValue,
            bestProduct = salesByProduct.firstOrNull()?.productName ?: NO_CATEGORY,
            salesByDateRange = salesByDate,
            salesByCategory = salesByCategory,
            salesByProduct = salesByProduct,
            transactions = transactions
        )
    }

    // The product list groups by the displayed product+variant label so that
    // a "Coffee (Iced)" sale doesn't collapse into a sibling "Coffee (Hot)".
    private fun OrderItemRowDto.toProductDisplayName(): String {
        return when {
            orderItemVariantName.equals(STANDARD_VARIANT, ignoreCase = true) -> orderItemProductName
            orderItemVariantName.equals(COMBO_VARIANT, ignoreCase = true) -> orderItemProductName
            else -> "$orderItemProductName ($orderItemVariantName)"
        }
    }

    private suspend fun fetchOrders(): List<OrderRowDto> {
        return supabaseClient
            .from(ORDERS_TABLE)
            .select {
                order(column = "created_at", order = Order.DESCENDING)
            }
            .decodeList<OrderRowDto>()
    }

    private suspend fun fetchOrderItems(): List<OrderItemRowDto> {
        return supabaseClient
            .from(ORDER_ITEMS_TABLE)
            .select {
                order(column = "created_at", order = Order.DESCENDING)
            }
            .decodeList<OrderItemRowDto>()
    }

    private suspend fun fetchCatalogRows(): List<ProductVariantStockDto> {
        return supabaseClient
            .from(PRODUCT_VARIANT_STOCK_VIEW)
            .select {
                order(column = "product_name", order = Order.ASCENDING)
                order(column = "variant_name", order = Order.ASCENDING)
            }
            .decodeList<ProductVariantStockDto>()
    }

    private fun buildSalesTimeline(
        orders: List<OrderRecord>,
        dateWindow: ReportDateWindow
    ): List<SalesTimelinePoint> {
        return when (dateWindow.timelineGranularity) {
            TimelineGranularity.HOURLY -> buildHourlySales(orders)
            TimelineGranularity.DAILY -> buildDailySales(orders, dateWindow)
            TimelineGranularity.WEEKLY -> buildWeeklySales(orders, dateWindow)
            TimelineGranularity.MONTHLY -> buildMonthlySales(orders, dateWindow)
        }
    }

    private fun buildHourlySales(orders: List<OrderRecord>): List<SalesTimelinePoint> {
        val ordersByHour = orders.groupBy { it.createdAt.hour }
        return (0..23).map { hour ->
            val hourOrders = ordersByHour[hour].orEmpty()
            val totalSales = hourOrders.sumOf(OrderRecord::total)
            val totalOrders = hourOrders.size
            SalesTimelinePoint(
                label = LocalTime.of(hour, 0).format(HOURLY_LABEL_FORMATTER).uppercase(Locale.getDefault()),
                totalSales = totalSales,
                totalOrders = totalOrders,
                averageOrderValue = if (totalOrders > 0) totalSales / totalOrders else 0.0
            )
        }
    }

    private fun buildDailySales(
        orders: List<OrderRecord>,
        dateWindow: ReportDateWindow
    ): List<SalesTimelinePoint> {
        val ordersByDate = orders.groupBy(OrderRecord::localDate)
        return generateSequence(dateWindow.startDate) { current ->
            current.plusDays(1).takeIf { !it.isAfter(dateWindow.endDate) }
        }.map { date ->
            val dailyOrders = ordersByDate[date].orEmpty()
            val totalSales = dailyOrders.sumOf(OrderRecord::total)
            val totalOrders = dailyOrders.size
            SalesTimelinePoint(
                label = date.format(DAILY_LABEL_FORMATTER),
                totalSales = totalSales,
                totalOrders = totalOrders,
                averageOrderValue = if (totalOrders > 0) totalSales / totalOrders else 0.0
            )
        }.toList()
    }

    private fun buildWeeklySales(
        orders: List<OrderRecord>,
        dateWindow: ReportDateWindow
    ): List<SalesTimelinePoint> {
        val ordersByWeek = orders.groupBy { order ->
            order.localDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }
        val startWeek = dateWindow.startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val endWeek = dateWindow.endDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return generateSequence(startWeek) { current ->
            current.plusWeeks(1).takeIf { !it.isAfter(endWeek) }
        }.map { weekStart ->
            val weeklyOrders = ordersByWeek[weekStart].orEmpty()
            val totalSales = weeklyOrders.sumOf(OrderRecord::total)
            val totalOrders = weeklyOrders.size
            SalesTimelinePoint(
                label = weekStart.format(WEEKLY_LABEL_FORMATTER),
                totalSales = totalSales,
                totalOrders = totalOrders,
                averageOrderValue = if (totalOrders > 0) totalSales / totalOrders else 0.0
            )
        }.toList()
    }

    private fun buildMonthlySales(
        orders: List<OrderRecord>,
        dateWindow: ReportDateWindow
    ): List<SalesTimelinePoint> {
        val ordersByMonth = orders.groupBy { order -> YearMonth.from(order.localDate) }
        val startMonth = YearMonth.from(dateWindow.startDate)
        val endMonth = YearMonth.from(dateWindow.endDate)
        return generateSequence(startMonth) { current ->
            current.plusMonths(1).takeIf { !it.isAfter(endMonth) }
        }.map { month ->
            val monthlyOrders = ordersByMonth[month].orEmpty()
            val totalSales = monthlyOrders.sumOf(OrderRecord::total)
            val totalOrders = monthlyOrders.size
            SalesTimelinePoint(
                label = month.format(MONTHLY_LABEL_FORMATTER),
                totalSales = totalSales,
                totalOrders = totalOrders,
                averageOrderValue = if (totalOrders > 0) totalSales / totalOrders else 0.0
            )
        }.toList()
    }

    private fun buildItemsPreview(items: List<String>): String {
        return when {
            items.isEmpty() -> NO_ITEMS
            items.size == 1 -> items.first()
            else -> "${items.first()} + ${items.size - 1} more"
        }
    }

    private fun OrderRowDto.toOrderRecord(zoneId: ZoneId): OrderRecord? {
        val createdAtDateTime = runCatching {
            OffsetDateTime.parse(createdAt).atZoneSameInstant(zoneId)
        }.getOrNull() ?: return null

        val normalizedStatus = orderStatus.trim().lowercase(Locale.US)
        return OrderRecord(
            orderId = orderId,
            orderNumber = orderNumber,
            createdAt = createdAtDateTime,
            localDate = createdAtDateTime.toLocalDate(),
            total = orderTotal,
            statusDisplay = normalizedStatus.replaceFirstChar { character ->
                if (character.isLowerCase()) character.titlecase(Locale.getDefault()) else character.toString()
            },
            isCompleted = normalizedStatus == STATUS_COMPLETED
        )
    }

    private fun OrderItemRowDto.toDisplayLabel(): String {
        val displayName = when {
            orderItemVariantName.equals(STANDARD_VARIANT, ignoreCase = true) -> orderItemProductName
            orderItemVariantName.equals(COMBO_VARIANT, ignoreCase = true) -> orderItemProductName
            else -> "$orderItemProductName ($orderItemVariantName)"
        }
        return if (orderItemQuantity > 1) {
            "$orderItemQuantity x $displayName"
        } else {
            displayName
        }
    }

    private data class OrderRecord(
        val orderId: String,
        val orderNumber: String,
        val createdAt: java.time.ZonedDateTime,
        val localDate: LocalDate,
        val total: Double,
        val statusDisplay: String,
        val isCompleted: Boolean
    )

    @Serializable
    private data class OrderRowDto(
        @SerialName("order_id")
        val orderId: String,
        @SerialName("order_number")
        val orderNumber: String,
        @SerialName("order_total")
        val orderTotal: Double,
        @SerialName("order_status")
        val orderStatus: String,
        @SerialName("created_at")
        val createdAt: String
    )

    @Serializable
    private data class OrderItemRowDto(
        @SerialName("order_id")
        val orderId: String,
        @SerialName("product_id")
        val productId: String,
        @SerialName("order_item_product_name")
        val orderItemProductName: String,
        @SerialName("order_item_variant_name")
        val orderItemVariantName: String,
        @SerialName("order_item_quantity")
        val orderItemQuantity: Int,
        @SerialName("order_item_line_total")
        val orderItemLineTotal: Double
    )

    private companion object {
        const val ORDERS_TABLE = "orders"
        const val ORDER_ITEMS_TABLE = "order_items"
        const val PRODUCT_VARIANT_STOCK_VIEW = "product_variant_stock_view"
        const val STATUS_COMPLETED = "completed"
        const val STANDARD_VARIANT = "standard"
        const val COMBO_VARIANT = "combo"
        const val NO_CATEGORY = "N/A"
        const val NO_ITEMS = "No items"
        const val UNCATEGORIZED = "Uncategorized"
        val HOURLY_LABEL_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("ha", Locale.getDefault())
        val DAILY_LABEL_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
        val WEEKLY_LABEL_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
        val MONTHLY_LABEL_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
        val TRANSACTION_DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMM dd", Locale.getDefault())
    }
}
