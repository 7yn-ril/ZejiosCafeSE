package com.example.zejioscafese.orders.data.repository

import com.example.zejioscafese.core.supabase.SupabaseProvider
import com.example.zejioscafese.orders.model.CafeOrder
import com.example.zejioscafese.orders.model.CafeOrderStatus
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class CheckoutOrderPayload(
    val customerName: String? = null,
    val tableLabel: String? = null,
    val subtotal: Double,
    val tax: Double,
    val total: Double,
    val paymentMethod: String,
    val status: String,
    val items: List<CheckoutOrderLine>
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
        return try {
            ensureAuthenticatedUserId()
            supabaseClient.postgrest
                .rpc(PEEK_NEXT_ORDER_RPC)
                .decodeSingle<NextOrderNumberRpcDto>()
                .nextOrderNumber
        } catch (_: Exception) {
            formatOrderNumber(DEFAULT_ORDER_COUNTER)
        }
    }

    suspend fun saveCheckoutOrder(
        payload: CheckoutOrderPayload,
        suggestedOrderNumber: String? = null
    ): CafeOrder {
        require(payload.items.isNotEmpty()) {
            "Add at least one item before saving an order."
        }

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

        return payload.toCafeOrder(rpcResult)
    }

    suspend fun fetchOrders(): List<CafeOrder> {
        ensureAuthenticatedUserId()

        val orderRows = supabaseClient
            .from(ORDERS_TABLE)
            .select {
                order(column = "created_at", order = Order.DESCENDING)
            }
            .decodeList<OrderRowDto>()

        if (orderRows.isEmpty()) {
            return emptyList()
        }

        val itemsByOrderId = supabaseClient
            .from(ORDER_ITEMS_TABLE)
            .select {
                order(column = "created_at", order = Order.ASCENDING)
            }
            .decodeList<OrderItemRowDto>()
            .groupBy(OrderItemRowDto::orderId)

        return orderRows.map { row ->
            val orderedItems = itemsByOrderId[row.orderId]
                .orEmpty()
                .map { it.toDisplayLabel() }
            val customerName = row.orderCustomerName
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: DEFAULT_CUSTOMER_NAME

            CafeOrder(
                id = row.orderNumber,
                customerName = customerName,
                tableLabel = row.orderTableLabel.orEmpty(),
                itemsSummary = orderedItems.joinToString(", ").ifBlank { EMPTY_ORDER_SUMMARY },
                itemCount = itemsByOrderId[row.orderId].orEmpty().sumOf(OrderItemRowDto::orderItemQuantity),
                timeLabel = row.createdAt.toTimeLabel(),
                status = row.orderStatus.toCafeOrderStatus(),
                total = row.orderTotal,
                initials = customerName.toInitials(),
                orderedItems = orderedItems
            )
        }
    }

    private suspend fun ensureAuthenticatedUserId(): String {
        val auth = supabaseClient.pluginManager.getPlugin(Auth)
        auth.awaitInitialization()

        val existingUserId = auth.currentUserOrNull()?.id
        if (existingUserId != null) {
            return existingUserId
        }

        try {
            auth.signInAnonymously()
        } catch (exception: Exception) {
            throw IllegalStateException(
                "Enable Anonymous Sign-Ins in Supabase Authentication so the app can save orders.",
                exception
            )
        }

        return auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("Supabase authentication did not return a staff session.")
    }

    private fun buildCheckoutRpcPayload(
        payload: CheckoutOrderPayload,
        suggestedOrderNumber: String?
    ) = buildJsonObject {
        putNullableText("p_customer_name", payload.customerName?.trim()?.takeIf(String::isNotBlank))
        putNullableText("p_table_label", payload.tableLabel?.trim()?.takeIf(String::isNotBlank))
        putNullableText("p_requested_order_number", suggestedOrderNumber)
        put("p_payment_method", payload.paymentMethod.lowercase(Locale.US))
        put("p_status", payload.status.lowercase(Locale.US))
        put("p_tax", payload.tax)
        put("p_items", Json.encodeToJsonElement(ListSerializer(CheckoutOrderLine.serializer()), payload.items))
    }

    private fun CheckoutOrderPayload.toCafeOrder(
        rpcResult: ProcessCheckoutRpcResultDto
    ): CafeOrder {
        val customerName = customerName?.trim()?.takeIf(String::isNotBlank) ?: DEFAULT_CUSTOMER_NAME
        val orderedItems = items.map { it.toDisplayLabel() }

        return CafeOrder(
            id = rpcResult.orderNumber,
            customerName = customerName,
            tableLabel = tableLabel.orEmpty(),
            itemsSummary = orderedItems.joinToString(", ").ifBlank { EMPTY_ORDER_SUMMARY },
            itemCount = items.sumOf(CheckoutOrderLine::quantity),
            timeLabel = rpcResult.createdAt.toTimeLabel(),
            status = status.toCafeOrderStatus(),
            total = rpcResult.orderTotal,
            initials = customerName.toInitials(),
            orderedItems = orderedItems
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

    @Serializable
    private data class OrderRowDto(
        @SerialName("order_id")
        val orderId: String,
        @SerialName("order_number")
        val orderNumber: String,
        @SerialName("order_customer_name")
        val orderCustomerName: String? = null,
        @SerialName("order_table_label")
        val orderTableLabel: String? = null,
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
        @SerialName("order_item_product_name")
        val orderItemProductName: String,
        @SerialName("order_item_variant_name")
        val orderItemVariantName: String,
        @SerialName("order_item_quantity")
        val orderItemQuantity: Int
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
    }
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableText(key: String, value: String?) {
    put(key, value?.let(::JsonPrimitive) ?: JsonNull)
}
