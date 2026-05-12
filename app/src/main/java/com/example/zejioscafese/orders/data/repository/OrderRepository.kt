package com.example.zejioscafese.orders.data.repository

import com.example.zejioscafese.core.supabase.SupabaseProvider
import com.example.zejioscafese.core.supabase.SupabaseSessionHelper
import com.example.zejioscafese.orders.model.CafeOrder
import com.example.zejioscafese.orders.model.CafeOrderStatus
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// CHANGE: Orders — orderType added (dine_in | takeout | delivery).
// Defaults to dine_in for backwards compatibility with any caller that
// hasn't been updated yet.
@Serializable
data class CheckoutOrderPayload(
    val customerName: String? = null,
    val subtotal: Double,
    val tax: Double,
    val total: Double,
    val paymentMethod: String,
    val status: String,
    val items: List<CheckoutOrderLine>,
    val orderType: String = "dine_in"
)

@Serializable
data class CheckoutOrderLine(
    @SerialName("product_variant_id")
    val productVariantId: String,
    @SerialName("source_product_id")
    val sourceProductId: String,
    @SerialName("source_product_name")
    val sourceProductName: String,
    @SerialName("source_variant_name")
    val sourceVariantName: String,
    @SerialName("unit_price")
    val unitPrice: Double,
    @SerialName("quantity")
    val quantity: Int
)

class OrderRepository(
    private val clientProvider: () -> SupabaseClient = { SupabaseProvider.client }
) {

    private val supabaseClient: SupabaseClient
        get() = clientProvider()

    suspend fun fetchNextOrderNumber(): String {
        return withContext(Dispatchers.IO) {
            try {
                SupabaseSessionHelper.withJwtRetry(supabaseClient) {
                    supabaseClient.postgrest
                        .rpc(PEEK_NEXT_ORDER_RPC)
                        .decodeSingle<NextOrderNumberRpcDto>()
                        .nextOrderNumber
                }
            } catch (_: Exception) {
                formatOrderNumber(DEFAULT_ORDER_COUNTER)
            }
        }
    }

    suspend fun saveCheckoutOrder(
        payload: CheckoutOrderPayload,
        suggestedOrderNumber: String? = null
    ): CafeOrder {
        require(payload.items.isNotEmpty()) {
            "Add at least one item before saving an order."
        }

        return withContext(Dispatchers.IO) {
            ensureAuthenticatedUserId()

            val rpcResult = supabaseClient.postgrest
                .rpc(
                    PROCESS_CHECKOUT_RPC,
                    buildCheckoutRpcPayload(
                        payload = payload,
                        suggestedOrderNumber = suggestedOrderNumber
                    )
                )
                .decodeSingle<ProcessCheckoutRpcResultDto>()

            // Best-effort: persist order_type on databases that have the
            // column. The in-memory CafeOrder always carries the correct
            // orderType so the UI (Take Out badge, filters) works either way.
            tagOrderTypeBestEffort(rpcResult.orderId, payload.orderType)

            payload.toCafeOrder(rpcResult)
        }
    }

    suspend fun fetchOrders(): List<CafeOrder> = withContext(Dispatchers.IO) {
        SupabaseSessionHelper.withJwtRetry(supabaseClient) {
            // Ascending so the queue reads oldest-first: customer 1 (who
            // ordered earliest) sits at the top of the Orders list and the
            // Preparing tab, since they should be served first.
            val orderRows = supabaseClient
                .from(ORDERS_TABLE)
                .select {
                    order(column = "created_at", order = Order.ASCENDING)
                }
                .decodeList<OrderRowDto>()

            if (orderRows.isEmpty()) {
                return@withJwtRetry emptyList()
            }

            val itemsByOrderId = supabaseClient
                .from(ORDER_ITEMS_TABLE)
                .select {
                    order(column = "created_at", order = Order.ASCENDING)
                }
                .decodeList<OrderItemRowDto>()
                .groupBy(OrderItemRowDto::orderId)

            orderRows.map { row ->
                val orderItemRows = itemsByOrderId[row.orderId].orEmpty()
                val orderedItems = orderItemRows.map { it.toDisplayLabel() }
                val customerName = row.orderCustomerName
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?: DEFAULT_CUSTOMER_NAME

                CafeOrder(
                    id = row.orderNumber,
                    customerName = customerName,
                    itemsSummary = orderedItems.joinToString(", ").ifBlank { EMPTY_ORDER_SUMMARY },
                    itemCount = orderItemRows.sumOf(OrderItemRowDto::orderItemQuantity),
                    timeLabel = row.createdAt.toTimeLabel(),
                    status = row.orderStatus.toCafeOrderStatus(),
                    total = row.orderTotal,
                    initials = customerName.toInitials(),
                    orderedItems = orderedItems,
                    orderedItemVariantIds = orderItemRows.map(OrderItemRowDto::productVariantId),
                    completedItemVariantIds = orderItemRows
                        .filter(OrderItemRowDto::orderItemIsCompleted)
                        .map(OrderItemRowDto::productVariantId)
                        .toSet(),
                    createdAtMillis = row.createdAt.toEpochMillis(),
                    completedAtMillis = row.orderCompletedAt?.toEpochMillis(),
                    // CHANGE: Orders — surface payment method + order type
                    // so the list UI can render the Take Out badge and
                    // apply the GCash / Take Out filters.
                    paymentMethod = row.orderPaymentMethod.orEmpty().ifBlank { "cash" }.lowercase(Locale.US),
                    orderType = row.orderType.orEmpty().ifBlank { "dine_in" }.lowercase(Locale.US)
                )
            }
        }
    }

    suspend fun updateOrderStatus(orderNumber: String, status: CafeOrderStatus) = withContext(Dispatchers.IO) {
        SupabaseSessionHelper.withJwtRetry(supabaseClient) {
            if (status == CafeOrderStatus.COMPLETED) {
                supabaseClient.postgrest.rpc(
                    COMPLETE_ORDER_RPC,
                    buildJsonObject {
                        put("p_order_number", orderNumber)
                    }
                )
            } else {
                supabaseClient
                    .from(ORDERS_TABLE)
                    .update(
                        {
                            set("order_status", status.toDatabaseValue())
                        }
                    ) {
                        filter {
                            eq("order_number", orderNumber)
                        }
                    }
            }
        }
    }

    suspend fun deleteOrder(orderNumber: String) = withContext(Dispatchers.IO) {
        SupabaseSessionHelper.withJwtRetry(supabaseClient) {
            val orderId = supabaseClient
                .from(ORDERS_TABLE)
                .select {
                    filter {
                        eq("order_number", orderNumber)
                    }
                }
                .decodeSingle<OrderIdentityDto>()
                .orderId

            supabaseClient
                .from(ORDER_ITEMS_TABLE)
                .delete {
                    filter {
                        eq("order_id", orderId)
                    }
                }

            supabaseClient
                .from(ORDERS_TABLE)
                .delete {
                    filter {
                        eq("order_id", orderId)
                    }
                }
        }
    }

    suspend fun updateOrderItemCompletion(
        orderNumber: String,
        completedVariantIds: Set<String>
    ) = withContext(Dispatchers.IO) {
        SupabaseSessionHelper.withJwtRetry(supabaseClient) {
            val orderId = supabaseClient
                .from(ORDERS_TABLE)
                .select {
                    filter {
                        eq("order_number", orderNumber)
                    }
                }
                .decodeSingle<OrderIdentityDto>()
                .orderId

            supabaseClient
                .from(ORDER_ITEMS_TABLE)
                .update(
                    {
                        set("order_item_is_completed", false)
                    }
                ) {
                    filter {
                        eq("order_id", orderId)
                    }
                }

            completedVariantIds.forEach { productVariantId ->
                supabaseClient
                    .from(ORDER_ITEMS_TABLE)
                    .update(
                        {
                            set("order_item_is_completed", true)
                        }
                    ) {
                        filter {
                            eq("order_id", orderId)
                            eq("product_variant_id", productVariantId)
                        }
                    }
            }
        }
    }

    private suspend fun ensureAuthenticatedUserId(): String {
        return SupabaseSessionHelper.ensureValidSession(
            client = supabaseClient,
            signInErrorMessage = "Enable Anonymous Sign-Ins in Supabase Authentication so the app can save orders."
        )
            ?: throw IllegalStateException("Supabase authentication did not return a staff session.")
    }

    // CHANGE: Orders — main RPC stays on the original 6-arg signature so
    // checkout keeps working on databases that have not yet applied
    // database/add_takeout_support.sql. After the order is saved, we
    // best-effort tag order_type via a follow-up update so the column
    // gets populated on databases that DO have the new column. If the
    // column is missing, the update fails silently — the order is
    // already saved and the rest of the flow continues.
    private fun buildCheckoutRpcPayload(
        payload: CheckoutOrderPayload,
        suggestedOrderNumber: String?
    ) = buildJsonObject {
        putNullableText("p_customer_name", payload.customerName?.trim()?.takeIf(String::isNotBlank))
        putNullableText("p_requested_order_number", suggestedOrderNumber)
        put("p_payment_method", payload.paymentMethod.lowercase(Locale.US))
        put("p_status", payload.status.lowercase(Locale.US))
        put("p_tax", payload.tax)
        put("p_items", Json.encodeToJsonElement(ListSerializer(CheckoutOrderLine.serializer()), payload.items))
    }

    // Best-effort tag for order_type. Swallows any failure (e.g. column
    // does not exist yet) so a missing migration does not block checkout.
    private suspend fun tagOrderTypeBestEffort(orderId: String, orderType: String) {
        runCatching {
            supabaseClient
                .from(ORDERS_TABLE)
                .update({ set("order_type", orderType.lowercase(Locale.US)) }) {
                    filter { eq("order_id", orderId) }
                }
        }
    }

    private fun CheckoutOrderPayload.toCafeOrder(
        rpcResult: ProcessCheckoutRpcResultDto
    ): CafeOrder {
        val customerName = customerName?.trim()?.takeIf(String::isNotBlank) ?: DEFAULT_CUSTOMER_NAME
        val orderedItems = items.map { it.toDisplayLabel() }

        return CafeOrder(
            id = rpcResult.orderNumber,
            customerName = customerName,
            itemsSummary = orderedItems.joinToString(", ").ifBlank { EMPTY_ORDER_SUMMARY },
            itemCount = items.sumOf(CheckoutOrderLine::quantity),
            timeLabel = rpcResult.createdAt.toTimeLabel(),
            status = status.toCafeOrderStatus(),
            total = rpcResult.orderTotal,
            initials = customerName.toInitials(),
            orderedItems = orderedItems,
            orderedItemVariantIds = items.map(CheckoutOrderLine::productVariantId),
            createdAtMillis = rpcResult.createdAt.toEpochMillis(),
            // CHANGE: Orders — propagate the just-saved payment method and
            // order type onto the in-memory CafeOrder so the list reflects
            // them immediately without a refetch.
            paymentMethod = paymentMethod.lowercase(Locale.US),
            orderType = orderType.lowercase(Locale.US)
        )
    }

    private fun CheckoutOrderLine.toDisplayLabel(): String {
        val displayName = formatProductDisplayName(
            productName = sourceProductName,
            variantName = sourceVariantName
        )
        return if (quantity > 1) {
            "$quantity x $displayName"
        } else {
            displayName
        }
    }

    private fun OrderItemRowDto.toDisplayLabel(): String {
        val displayName = formatProductDisplayName(
            productName = orderItemProductName,
            variantName = orderItemVariantName
        )
        return if (orderItemQuantity > 1) {
            "$orderItemQuantity x $displayName"
        } else {
            displayName
        }
    }

    private fun formatProductDisplayName(productName: String, variantName: String): String {
        return when {
            variantName.equals("standard", ignoreCase = true) -> productName
            variantName.equals("combo", ignoreCase = true) -> productName
            else -> "$productName ($variantName)"
        }
    }

    private fun String.toCafeOrderStatus(): CafeOrderStatus {
        return when (trim().lowercase(Locale.US)) {
            "pending" -> CafeOrderStatus.PENDING
            "preparing" -> CafeOrderStatus.PREPARING
            else -> CafeOrderStatus.COMPLETED
        }
    }

    private fun CafeOrderStatus.toDatabaseValue(): String {
        return when (this) {
            CafeOrderStatus.PENDING -> "pending"
            CafeOrderStatus.PREPARING -> "preparing"
            CafeOrderStatus.COMPLETED -> "completed"
        }
    }

    private fun String.toInitials(): String {
        val parts = trim().split("\\s+".toRegex()).filter(String::isNotBlank)
        return when {
            parts.isEmpty() -> DEFAULT_INITIALS
            parts.size == 1 -> parts.first().take(2).uppercase(Locale.getDefault())
            else -> buildString {
                append(parts.first().first())
                append(parts.last().first())
            }.uppercase(Locale.getDefault())
        }
    }

    private fun OffsetDateTime.toTimeLabel(): String {
        return atZoneSameInstant(ZoneId.systemDefault())
            .format(TIME_FORMATTER)
    }

    private fun String.toTimeLabel(): String {
        return try {
            OffsetDateTime.parse(this).toTimeLabel()
        } catch (_: Exception) {
            ""
        }
    }

    private fun String.toEpochMillis(): Long {
        return try {
            OffsetDateTime.parse(this).toInstant().toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }

    private fun formatOrderNumber(counter: Int): String {
        return ORDER_NUMBER_TEMPLATE.format(counter)
    }

    @Serializable
    private data class ProcessCheckoutRpcResultDto(
        @SerialName("order_id")
        val orderId: String,
        @SerialName("order_number")
        val orderNumber: String,
        @SerialName("created_at")
        val createdAt: String,
        @SerialName("order_total")
        val orderTotal: Double
    )

    // CHANGE: Orders — pull payment_method and order_type from Supabase.
    // order_type was added by add_takeout_support.sql (defaults to 'dine_in').
    @Serializable
    private data class OrderRowDto(
        @SerialName("order_id")
        val orderId: String,
        @SerialName("order_number")
        val orderNumber: String,
        @SerialName("order_customer_name")
        val orderCustomerName: String? = null,
        @SerialName("order_total")
        val orderTotal: Double,
        @SerialName("order_status")
        val orderStatus: String,
        @SerialName("order_payment_method")
        val orderPaymentMethod: String? = null,
        @SerialName("order_type")
        val orderType: String? = null,
        @SerialName("order_completed_at")
        val orderCompletedAt: String? = null,
        @SerialName("created_at")
        val createdAt: String
    )

    @Serializable
    private data class OrderItemRowDto(
        @SerialName("order_id")
        val orderId: String,
        @SerialName("product_variant_id")
        val productVariantId: String,
        @SerialName("order_item_product_name")
        val orderItemProductName: String,
        @SerialName("order_item_variant_name")
        val orderItemVariantName: String,
        @SerialName("order_item_quantity")
        val orderItemQuantity: Int,
        @SerialName("order_item_is_completed")
        val orderItemIsCompleted: Boolean = false
    )

    @Serializable
    private data class OrderIdentityDto(
        @SerialName("order_id")
        val orderId: String
    )

    @Serializable
    private data class NextOrderNumberRpcDto(
        @SerialName("next_order_number")
        val nextOrderNumber: String
    )

    private companion object {
        const val DEFAULT_CUSTOMER_NAME = "Walk-in Customer"
        const val DEFAULT_INITIALS = "WC"
        const val DEFAULT_ORDER_COUNTER = 1024
        const val EMPTY_ORDER_SUMMARY = "No items"
        const val ORDERS_TABLE = "orders"
        const val ORDER_ITEMS_TABLE = "order_items"
        val ORDER_NUMBER_TEMPLATE = "#POS-%04d"
        val TIME_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())
        const val PEEK_NEXT_ORDER_RPC = "peek_next_pos_order_number"
        const val PROCESS_CHECKOUT_RPC = "process_checkout_order"
        const val COMPLETE_ORDER_RPC = "complete_order"
    }
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableText(key: String, value: String?) {
    put(key, value?.let(::JsonPrimitive) ?: JsonNull)
}
