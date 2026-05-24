package com.example.zejioscafese.reports.data.repository

import com.example.zejioscafese.core.supabase.SupabaseProvider
import com.example.zejioscafese.core.supabase.SupabaseSessionHelper
import com.example.zejioscafese.pos.data.remote.dto.ProductVariantStockDto
import com.example.zejioscafese.reports.data.ReportsAggregator
import com.example.zejioscafese.reports.data.model.DatasetOrder
import com.example.zejioscafese.reports.data.model.DatasetOrderItem
import com.example.zejioscafese.reports.data.model.ProductGroupFilter
import com.example.zejioscafese.reports.data.model.ReportsDataset
import com.example.zejioscafese.reports.data.model.ReportsSnapshot
import com.example.zejioscafese.reports.data.model.TimelineGranularity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class ReportsRepository(
    private val clientProvider: () -> SupabaseClient = { SupabaseProvider.client }
) {

    // Re-exported for callers that still reference the old type path. The
    // canonical enum lives in [ReportsDataset.kt] now.
    enum class TimelineGranularity { HOURLY, DAILY, WEEKLY, MONTHLY, YEARLY;

        internal fun toModel(): com.example.zejioscafese.reports.data.model.TimelineGranularity {
            return when (this) {
                HOURLY -> com.example.zejioscafese.reports.data.model.TimelineGranularity.HOURLY
                DAILY -> com.example.zejioscafese.reports.data.model.TimelineGranularity.DAILY
                WEEKLY -> com.example.zejioscafese.reports.data.model.TimelineGranularity.WEEKLY
                MONTHLY -> com.example.zejioscafese.reports.data.model.TimelineGranularity.MONTHLY
                YEARLY -> com.example.zejioscafese.reports.data.model.TimelineGranularity.YEARLY
            }
        }
    }

    data class ReportDateWindow(
        val startDate: LocalDate,
        val endDate: LocalDate,
        val timelineGranularity: TimelineGranularity
    )

    private val supabaseClient: SupabaseClient
        get() = clientProvider()

    /**
     * Pulls the raw orders + items + catalog for the window. The ViewModel
     * slices this with [ReportsAggregator] on every bucket/filter change
     * so the network isn't hit when the user just taps a bar.
     */
    suspend fun fetchReportsDataset(
        dateWindow: ReportDateWindow,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ReportsDataset = SupabaseSessionHelper.withJwtRetry(supabaseClient) {
        buildDataset(dateWindow, zoneId)
    }

    /**
     * Convenience that returns the unfiltered, window-wide aggregation in
     * the legacy [ReportsSnapshot] shape. Kept so the Excel export path
     * and any older callers/tests don't need to know about the dataset
     * refactor.
     */
    suspend fun fetchReportsSnapshot(
        dateWindow: ReportDateWindow,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ReportsSnapshot {
        val dataset = fetchReportsDataset(dateWindow, zoneId)
        val result = ReportsAggregator.aggregate(
            dataset = dataset,
            selectedBucketLabel = null,
            productGroupFilter = ProductGroupFilter.ALL
        )
        return ReportsSnapshot(
            totalRevenue = result.totalRevenue,
            totalOrders = result.totalOrders,
            averageOrderValue = result.averageOrderValue,
            bestProduct = result.bestProduct,
            salesByDateRange = result.salesByDateRange,
            salesByCategory = result.salesByCategory,
            salesByProduct = result.salesByProduct,
            transactions = result.transactions
        )
    }

    private suspend fun buildDataset(
        dateWindow: ReportDateWindow,
        zoneId: ZoneId
    ): ReportsDataset {
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
            .mapNotNull { it.toDatasetOrder(zoneId) }
            .filter { record ->
                record.localDate in dateWindow.startDate..dateWindow.endDate
            }
            .sortedByDescending(DatasetOrder::createdAt)

        val orderIds = ordersInRange.map(DatasetOrder::orderId).toHashSet()
        val datasetItems = orderItemRows
            .filter { it.orderId in orderIds }
            .map { it.toDatasetItem() }

        return ReportsDataset(
            startDate = dateWindow.startDate,
            endDate = dateWindow.endDate,
            timelineGranularity = dateWindow.timelineGranularity.toModel(),
            zoneId = zoneId,
            orders = ordersInRange,
            items = datasetItems,
            categoryByProductId = categoryByProductId
        )
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

    private fun OrderRowDto.toDatasetOrder(zoneId: ZoneId): DatasetOrder? {
        val createdAtDateTime = runCatching {
            OffsetDateTime.parse(createdAt).atZoneSameInstant(zoneId)
        }.getOrNull() ?: return null

        val normalizedStatus = orderStatus.trim().lowercase(Locale.US)
        // Only completed orders feed reports — same rule as before.
        if (normalizedStatus != STATUS_COMPLETED) return null

        val normalizedPaymentMethod = orderPaymentMethod.orEmpty()
            .ifBlank { DEFAULT_PAYMENT_METHOD }
            .lowercase(Locale.US)
            .toReportPaymentMethod()
        val normalizedPaymentProvider = orderPaymentProvider
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.lowercase(Locale.US)
        val normalizedOrderType = orderType.orEmpty().ifBlank { DEFAULT_ORDER_TYPE }
            .lowercase(Locale.US)
        val tax = orderTax ?: 0.0
        val storedDiscountAmount = orderDiscountAmount?.coerceAtLeast(0.0)
        val subtotal = orderSubtotal ?: (orderTotal + (storedDiscountAmount ?: 0.0) - tax)
            .coerceAtLeast(0.0)
        val inferredDiscount = (subtotal + tax - orderTotal).coerceAtLeast(0.0)
        val discountAmount = storedDiscountAmount ?: inferredDiscount
        val discountLabel = orderDiscountLabel
            ?.trim()
            ?.takeIf { it.isNotBlank() && discountAmount > 0.0 }
        return DatasetOrder(
            orderId = orderId,
            orderNumber = orderNumber,
            createdAt = createdAtDateTime,
            localDate = createdAtDateTime.toLocalDate(),
            total = orderTotal,
            statusDisplay = normalizedStatus.replaceFirstChar { character ->
                if (character.isLowerCase()) character.titlecase(Locale.getDefault()) else character.toString()
            },
            subtotal = subtotal,
            discountLabel = discountLabel,
            discountPercent = orderDiscountPercent,
            discountAmount = discountAmount,
            paymentMethod = normalizedPaymentMethod,
            paymentProvider = normalizedPaymentProvider,
            orderType = normalizedOrderType
        )
    }

    private fun OrderItemRowDto.toDatasetItem(): DatasetOrderItem {
        return DatasetOrderItem(
            orderId = orderId,
            productId = productId,
            productName = orderItemProductName,
            variantName = orderItemVariantName,
            quantity = orderItemQuantity,
            unitPrice = orderItemUnitPrice,
            lineTotal = orderItemLineTotal
        )
    }

    @Serializable
    private data class OrderRowDto(
        @SerialName("order_id")
        val orderId: String,
        @SerialName("order_number")
        val orderNumber: String,
        @SerialName("order_total")
        val orderTotal: Double,
        @SerialName("order_subtotal")
        val orderSubtotal: Double? = null,
        @SerialName("order_tax")
        val orderTax: Double? = null,
        @SerialName("order_discount_label")
        val orderDiscountLabel: String? = null,
        @SerialName("order_discount_percent")
        val orderDiscountPercent: Double? = null,
        @SerialName("order_discount_amount")
        val orderDiscountAmount: Double? = null,
        @SerialName("order_payment_method")
        val orderPaymentMethod: String? = null,
        @SerialName("order_payment_provider")
        val orderPaymentProvider: String? = null,
        @SerialName("order_type")
        val orderType: String? = null,
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
        @SerialName("order_item_unit_price")
        val orderItemUnitPrice: Double = 0.0,
        @SerialName("order_item_line_total")
        val orderItemLineTotal: Double
    )

    private companion object {
        const val ORDERS_TABLE = "orders"
        const val ORDER_ITEMS_TABLE = "order_items"
        const val PRODUCT_VARIANT_STOCK_VIEW = "product_variant_stock_view"
        const val STATUS_COMPLETED = "completed"
        const val DEFAULT_PAYMENT_METHOD = "cash"
        const val DEFAULT_ORDER_TYPE = "dine_in"
    }
}

private fun String.toReportPaymentMethod(): String {
    return if (this in setOf("gcash", "maya", "paymaya", "qr_ph")) "qrph" else this
}
