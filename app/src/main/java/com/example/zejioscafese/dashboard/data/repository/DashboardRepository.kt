package com.example.zejioscafese.dashboard.data.repository

import android.util.Log
import com.example.zejioscafese.core.supabase.SupabaseProvider
import com.example.zejioscafese.core.supabase.SupabaseSessionHelper
import com.example.zejioscafese.dashboard.model.AlertLevel
import com.example.zejioscafese.dashboard.model.DashboardAlert
import com.example.zejioscafese.dashboard.model.DashboardChartPoint
import com.example.zejioscafese.dashboard.model.DashboardInsight
import com.example.zejioscafese.dashboard.model.DashboardMetric
import com.example.zejioscafese.dashboard.model.DashboardPeriod
import com.example.zejioscafese.dashboard.model.DashboardRecentOrder
import com.example.zejioscafese.dashboard.model.DashboardRecentOrderItem
import com.example.zejioscafese.dashboard.model.DashboardSnapshot
import com.example.zejioscafese.dashboard.model.DashboardTopItem
import com.example.zejioscafese.dashboard.model.InventoryStockNotice
import com.example.zejioscafese.dashboard.model.InventoryStockStatus
import com.example.zejioscafese.pos.data.local.ProductImageResolver
import com.example.zejioscafese.pos.data.model.IngredientUnits
import com.example.zejioscafese.pos.data.remote.dto.ProductVariantStockDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class DashboardRepository(
    private val clientProvider: () -> SupabaseClient = { SupabaseProvider.client }
) {

    private val supabaseClient: SupabaseClient
        get() = clientProvider()

    suspend fun fetchDashboardSnapshot(
        now: OffsetDateTime = OffsetDateTime.now()
    ): DashboardSnapshot = withContext(Dispatchers.IO) {
        SupabaseSessionHelper.withJwtRetry(supabaseClient) {
            buildDashboardSnapshot(now)
        }
    }

    private suspend fun buildDashboardSnapshot(now: OffsetDateTime): DashboardSnapshot {
        val zoneId = ZoneId.systemDefault()
        val today = now.atZoneSameInstant(zoneId).toLocalDate()
        val yesterday = today.minusDays(1)
        val monthStart = today.withDayOfMonth(1)
        val lastSevenDaysStart = today.minusDays(6)
        val lastEightWeeksStart = today
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .minusWeeks(7)
        val lastTwelveMonthsStart = today.withDayOfMonth(1).minusMonths(11)
        val lastFiveYearsStart = today.withDayOfYear(1).minusYears(4)

        val rawData = coroutineScope {
            val ordersDeferred = async {
                fetchOrders().mapNotNull { it.toOrderRecord(zoneId) }
            }
            val orderItemsDeferred = async {
                fetchOptionalData("order_items") { fetchOrderItems() }
            }
            val ingredientsDeferred = async {
                fetchOptionalData("ingredients") { fetchIngredients() }
            }
            val recipesDeferred = async {
                fetchOptionalData("variant_ingredients") { fetchRecipes() }
            }
            val productCatalogDeferred = async {
                fetchOptionalData("product_variant_stock_view") { fetchProductCatalog() }
            }

            RawDashboardData(
                orders = ordersDeferred.await(),
                orderItems = orderItemsDeferred.await(),
                ingredients = ingredientsDeferred.await(),
                recipes = recipesDeferred.await(),
                productCatalog = productCatalogDeferred.await()
            )
        }

        val completedOrders = rawData.orders.filter { it.isCompleted }
        val activeOrders = rawData.orders.filter { it.isActive }

        val todayCompleted = completedOrders.filter { it.localDate == today }
        val yesterdayCompleted = completedOrders.filter { it.localDate == yesterday }
        val monthCompleted = completedOrders.filter { !it.localDate.isBefore(monthStart) }

        val todayOrderIds = todayCompleted.map(OrderRecord::id).toHashSet()
        val yesterdayOrderIds = yesterdayCompleted.map(OrderRecord::id).toHashSet()
        val monthOrderIds = monthCompleted.map(OrderRecord::id).toHashSet()

        val todayOrderItems = rawData.orderItems.filter { it.orderId in todayOrderIds }
        val yesterdayOrderItems = rawData.orderItems.filter { it.orderId in yesterdayOrderIds }
        val monthOrderItems = rawData.orderItems.filter { it.orderId in monthOrderIds }

        val ingredientCostById = rawData.ingredients.associate { it.ingredientId to it.costPerUnit }
        val estimatedVariantCostById = rawData.recipes
            .groupBy(RecipeRowDto::productVariantId)
            .mapValues { (_, recipeRows) ->
                recipeRows.sumOf { row ->
                    row.requiredQuantity * (ingredientCostById[row.ingredientId] ?: 0.0)
                }
            }

        val productMetadataByVariantId = rawData.productCatalog.associateBy(ProductVariantStockDto::productVariantId)

        val salesToday = todayCompleted.sumOf(OrderRecord::total)
        val salesYesterday = yesterdayCompleted.sumOf(OrderRecord::total)
        val ordersToday = todayCompleted.size
        val ordersYesterday = yesterdayCompleted.size
        val profitToday = estimateProfit(todayOrderItems, estimatedVariantCostById)
        val profitYesterday = estimateProfit(yesterdayOrderItems, estimatedVariantCostById)

        val lowStockIngredients = rawData.ingredients
            .filter { it.minimumStock > 0.0 && it.currentStock <= it.minimumStock }
            .sortedWith(
                compareBy<IngredientRowDto> { if (it.currentStock <= it.minimumStock * CRITICAL_THRESHOLD_RATIO) 0 else 1 }
                    .thenBy { stockRatio(it) }
                    .thenBy { it.ingredientName.lowercase(Locale.getDefault()) }
            )

        val criticalAlerts = lowStockIngredients.count { it.currentStock <= it.minimumStock * CRITICAL_THRESHOLD_RATIO }
        val warningAlerts = (lowStockIngredients.size - criticalAlerts).coerceAtLeast(0)
        val pendingOrders = activeOrders.count { it.status == STATUS_PENDING }
        val preparingOrders = activeOrders.count { it.status == STATUS_PREPARING }

        val alerts = lowStockIngredients.map { ingredient ->
            val isCritical = ingredient.currentStock <= ingredient.minimumStock * CRITICAL_THRESHOLD_RATIO
            val unit = IngredientUnits.normalize(ingredient.ingredientUnit)
            DashboardAlert(
                title = "Low stock: ${ingredient.ingredientName}",
                detail = "Only ${formatStock(ingredient.currentStock)} $unit left. Minimum target is ${formatStock(ingredient.minimumStock)} $unit.",
                level = if (isCritical) AlertLevel.CRITICAL else AlertLevel.WARNING
            )
        }

        // Structured notices for the system notification layer. Includes
        // truly out-of-stock items even when the dashboard alerts list omits
        // ingredients with a zero minimum threshold.
        val inventoryNotices = rawData.ingredients.mapNotNull { ingredient ->
            when {
                ingredient.currentStock <= 0.0 -> InventoryStockNotice(
                    ingredientId = ingredient.ingredientId,
                    ingredientName = ingredient.ingredientName,
                    unit = IngredientUnits.normalize(ingredient.ingredientUnit),
                    currentStock = ingredient.currentStock,
                    minimumStock = ingredient.minimumStock,
                    status = InventoryStockStatus.OUT
                )
                ingredient.minimumStock > 0.0 && ingredient.currentStock <= ingredient.minimumStock -> InventoryStockNotice(
                    ingredientId = ingredient.ingredientId,
                    ingredientName = ingredient.ingredientName,
                    unit = IngredientUnits.normalize(ingredient.ingredientUnit),
                    currentStock = ingredient.currentStock,
                    minimumStock = ingredient.minimumStock,
                    status = InventoryStockStatus.LOW
                )
                else -> null
            }
        }

        val topItems = monthOrderItems
            .groupBy { item -> item.displayName() }
            .map { (displayName, rows) ->
                val firstRow = rows.first()
                val metadata = firstRow.productVariantId
                    ?.let(productMetadataByVariantId::get)

                DashboardTopItem(
                    name = displayName,
                    orders = rows.size,
                    revenue = rows.sumOf(OrderItemRowDto::lineTotal),
                    imageUrl = ProductImageResolver.resolve(metadata?.productName ?: firstRow.productName)
                        ?: metadata?.productImageUrl.orEmpty()
                )
            }
            .sortedWith(
                compareByDescending<DashboardTopItem> { it.revenue }
                    .thenByDescending { it.orders }
                    .thenBy { it.name.lowercase(Locale.getDefault()) }
            )
            .take(MAX_TOP_ITEMS)

        val categoryRevenue = monthOrderItems
            .groupBy { item ->
                item.productVariantId
                    ?.let(productMetadataByVariantId::get)
                    ?.categoryName
                    .orEmpty()
                    .ifBlank { UNCATEGORIZED }
            }
            .mapValues { (_, rows) -> rows.sumOf(OrderItemRowDto::lineTotal) }

        val bestCategory = categoryRevenue.maxByOrNull(Map.Entry<String, Double>::value)
        val topSeller = topItems.firstOrNull()

        val insights = listOf(
            DashboardInsight(
                title = "Best Category",
                value = bestCategory?.key ?: "No sales yet",
                supportingText = bestCategory?.value?.let { "${formatCurrency(it)} in revenue this month." }
                    ?: "Sales by category will appear once completed orders come in."
            ),
            DashboardInsight(
                title = "Top Seller",
                value = topSeller?.name ?: "No top item yet",
                supportingText = topSeller?.let { "Featured in ${it.orders} completed orders and generated ${formatCurrency(it.revenue)} this month." }
                    ?: "Best-selling items will appear after the first completed sales."
            )
        )

        val charts = mapOf(
            DashboardPeriod.HOURLY to buildHourlyChart(todayCompleted),
            DashboardPeriod.DAILY to buildDailyChart(completedOrders, lastSevenDaysStart, today),
            DashboardPeriod.WEEKLY to buildWeeklyChart(completedOrders, lastEightWeeksStart, today),
            DashboardPeriod.MONTHLY to buildMonthlyChart(completedOrders, lastTwelveMonthsStart, today),
            DashboardPeriod.YEARLY to buildYearlyChart(completedOrders, lastFiveYearsStart, today)
        )

        val itemsByOrderId = rawData.orderItems.groupBy(OrderItemRowDto::orderId)
        val recentOrders = rawData.orders
            .sortedByDescending(OrderRecord::createdAt)
            .take(MAX_RECENT_ORDERS)
            .map { record ->
                val items = itemsByOrderId[record.id].orEmpty()
                val itemSubtotal = items.sumOf(OrderItemRowDto::lineTotal)
                val subtotal = when {
                    record.subtotal != null && record.subtotal > 0.0 -> record.subtotal
                    itemSubtotal > 0.0 -> itemSubtotal
                    else -> record.total + record.discountAmount
                }
                DashboardRecentOrder(
                    orderNumber = record.orderNumber,
                    customerName = record.customerName,
                    itemsPreview = buildRecentItemsPreview(items),
                    items = items.map { row ->
                        DashboardRecentOrderItem(
                            productName = row.productName,
                            variantName = row.variantName,
                            quantity = row.quantity,
                            lineTotal = row.lineTotal
                        )
                    },
                    subtotal = subtotal,
                    discountLabel = record.discountLabel,
                    discountAmount = record.discountAmount,
                    total = record.total,
                    status = formatStatusLabel(record.status)
                )
            }

        return DashboardSnapshot(
            salesMetric = buildComparisonMetric(
                title = "Total Sales Today",
                current = salesToday,
                previous = salesYesterday,
                value = formatCurrency(salesToday)
            ),
            ordersMetric = buildComparisonMetric(
                title = "Total Orders Today",
                current = ordersToday.toDouble(),
                previous = ordersYesterday.toDouble(),
                value = ordersToday.toString()
            ),
            profitMetric = buildComparisonMetric(
                title = "Net Profit",
                current = profitToday,
                previous = profitYesterday,
                value = formatCurrency(profitToday)
            ),
            activeOrdersMetric = DashboardMetric(
                title = "Active Orders",
                value = activeOrders.size.toString(),
                delta = when {
                    activeOrders.isEmpty() -> "No active orders right now"
                    pendingOrders > 0 && preparingOrders > 0 -> "$pendingOrders pending, $preparingOrders preparing"
                    preparingOrders > 0 -> "$preparingOrders order(s) being prepared"
                    else -> "$pendingOrders order(s) waiting to be prepared"
                },
                positive = activeOrders.isEmpty()
            ),
            lowStockMetric = DashboardMetric(
                title = "Low Stock Items",
                value = lowStockIngredients.size.toString(),
                delta = when {
                    lowStockIngredients.isEmpty() -> "All ingredients are above minimum stock"
                    warningAlerts == 0 -> "$criticalAlerts critical item(s) need action"
                    else -> "$criticalAlerts critical, $warningAlerts warning"
                },
                positive = lowStockIngredients.isEmpty()
            ),
            insights = insights,
            topItems = topItems,
            alerts = alerts,
            charts = charts,
            recentOrders = recentOrders,
            inventoryNotices = inventoryNotices
        )
    }

    private fun buildRecentItemsPreview(items: List<OrderItemRowDto>): String {
        val labels = items.map { it.displayName() }
        return when {
            labels.isEmpty() -> NO_ITEMS_PREVIEW
            labels.size == 1 -> labels.first()
            else -> "${labels.first()} + ${labels.size - 1} more"
        }
    }

    private fun formatStatusLabel(status: String): String {
        return status.replaceFirstChar { character ->
            if (character.isLowerCase()) character.titlecase(Locale.getDefault()) else character.toString()
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

    private suspend fun fetchIngredients(): List<IngredientRowDto> {
        return supabaseClient
            .from(INGREDIENTS_TABLE)
            .select {
                order(column = "ingredient_name", order = Order.ASCENDING)
            }
            .decodeList<IngredientRowDto>()
    }

    private suspend fun fetchRecipes(): List<RecipeRowDto> {
        return supabaseClient
            .from(VARIANT_INGREDIENTS_TABLE)
            .select {
                order(column = "product_variant_id", order = Order.ASCENDING)
            }
            .decodeList<RecipeRowDto>()
    }

    private suspend fun fetchProductCatalog(): List<ProductVariantStockDto> {
        return supabaseClient
            .from(PRODUCT_VARIANT_STOCK_VIEW)
            .select {
                order(column = "product_name", order = Order.ASCENDING)
                order(column = "variant_name", order = Order.ASCENDING)
            }
            .decodeList<ProductVariantStockDto>()
    }

    private suspend fun <T> fetchOptionalData(
        label: String,
        block: suspend () -> List<T>
    ): List<T> {
        return try {
            block()
        } catch (exception: Exception) {
            Log.w(TAG, "Dashboard optional source failed: $label", exception)
            emptyList()
        }
    }

    private fun OrderRowDto.toOrderRecord(zoneId: ZoneId): OrderRecord? {
        val zonedDateTime = try {
            OffsetDateTime.parse(createdAt).atZoneSameInstant(zoneId)
        } catch (_: DateTimeParseException) {
            null
        }

        val resolvedCustomer = orderCustomerName
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: DEFAULT_CUSTOMER_NAME

        return zonedDateTime?.let { parsedTime ->
            val discountAmount = (orderDiscountAmount ?: 0.0).coerceAtLeast(0.0)
            val discountLabel = orderDiscountLabel
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.takeIf { discountAmount > 0.0 }
            OrderRecord(
                id = orderId,
                orderNumber = orderNumber,
                customerName = resolvedCustomer,
                createdAt = parsedTime,
                localDate = parsedTime.toLocalDate(),
                subtotal = orderSubtotal?.takeIf { it > 0.0 },
                discountLabel = discountLabel,
                discountAmount = discountAmount,
                total = orderTotal,
                status = orderStatus.trim().lowercase(Locale.US),
                isCompleted = orderStatus.trim().equals(STATUS_COMPLETED, ignoreCase = true),
                isActive = orderStatus.trim().equals(STATUS_PENDING, ignoreCase = true) ||
                    orderStatus.trim().equals(STATUS_PREPARING, ignoreCase = true)
            )
        }
    }

    private fun OrderItemRowDto.displayName(): String {
        return when {
            variantName.equals(STANDARD_VARIANT, ignoreCase = true) -> productName
            variantName.equals(COMBO_VARIANT, ignoreCase = true) -> productName
            else -> "$productName ($variantName)"
        }
    }

    private fun estimateProfit(
        items: List<OrderItemRowDto>,
        estimatedVariantCostById: Map<String, Double>
    ): Double {
        return items.sumOf { item ->
            val recipeCost = item.productVariantId
                ?.let(estimatedVariantCostById::get)
                ?: 0.0
            item.lineTotal - (recipeCost * item.quantity)
        }
    }

    private fun buildComparisonMetric(
        title: String,
        current: Double,
        previous: Double,
        value: String
    ): DashboardMetric {
        val (deltaLabel, isPositive) = compareAgainstYesterday(current, previous)
        return DashboardMetric(
            title = title,
            value = value,
            delta = deltaLabel,
            positive = isPositive
        )
    }

    private fun compareAgainstYesterday(current: Double, previous: Double): Pair<String, Boolean> {
        return when {
            current <= 0.0 && previous <= 0.0 -> "No change vs yesterday" to true
            previous <= 0.0 -> "New activity vs yesterday" to true
            else -> {
                val delta = ((current - previous) / previous) * 100.0
                String.format(Locale.US, "%+.1f%% vs yesterday", delta) to (delta >= 0.0)
            }
        }
    }

    private fun buildHourlyChart(todayOrders: List<OrderRecord>): List<DashboardChartPoint> {
        val buckets = listOf(8, 10, 12, 14, 16, 18, 20)
        return buckets.mapIndexed { index, hour ->
            val endExclusive = buckets.getOrNull(index + 1) ?: 24
            val totalSales = todayOrders
                .filter { record -> record.createdAt.hour in hour until endExclusive }
                .sumOf(OrderRecord::total)

            DashboardChartPoint(
                label = formatChartHour(hour),
                sales = totalSales.toFloat()
            )
        }
    }

    private fun buildDailyChart(
        completedOrders: List<OrderRecord>,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<DashboardChartPoint> {
        return generateSequence(startDate) { current ->
            current.plusDays(1).takeIf { !it.isAfter(endDate) }
        }.map { date ->
            DashboardChartPoint(
                label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                sales = completedOrders
                    .filter { it.localDate == date }
                    .sumOf(OrderRecord::total)
                    .toFloat()
            )
        }.toList()
    }

    private fun buildWeeklyChart(
        completedOrders: List<OrderRecord>,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<DashboardChartPoint> {
        val lastWeekStart = endDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return generateSequence(startDate) { current ->
            current.plusWeeks(1).takeIf { !it.isAfter(lastWeekStart) }
        }.map { weekStart ->
            val weekEnd = weekStart.plusDays(6)
            DashboardChartPoint(
                label = weekStart.format(WEEK_LABEL_FORMATTER),
                sales = completedOrders
                    .filter { it.localDate in weekStart..weekEnd }
                    .sumOf(OrderRecord::total)
                    .toFloat()
            )
        }.toList()
    }

    private fun buildMonthlyChart(
        completedOrders: List<OrderRecord>,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<DashboardChartPoint> {
        val startMonth = YearMonth.from(startDate)
        val endMonth = YearMonth.from(endDate)
        return generateSequence(startMonth) { current ->
            current.plusMonths(1).takeIf { !it.isAfter(endMonth) }
        }.map { month ->
            DashboardChartPoint(
                label = month.format(MONTH_LABEL_FORMATTER),
                sales = completedOrders
                    .filter { YearMonth.from(it.localDate) == month }
                    .sumOf(OrderRecord::total)
                    .toFloat()
            )
        }.toList()
    }

    private fun buildYearlyChart(
        completedOrders: List<OrderRecord>,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<DashboardChartPoint> {
        return (startDate.year..endDate.year).map { year ->
            DashboardChartPoint(
                label = year.toString(),
                sales = completedOrders
                    .filter { it.localDate.year == year }
                    .sumOf(OrderRecord::total)
                    .toFloat()
            )
        }
    }

    private fun formatCurrency(amount: Double): String {
        return String.format(Locale.US, "PHP %,.2f", amount.coerceAtLeast(0.0))
    }

    private fun formatStock(amount: Double): String {
        return if (amount % 1.0 == 0.0) {
            amount.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", amount)
        }
    }

    private fun formatChartHour(hour: Int): String {
        return LocalTime.of(hour, 0)
            .format(CHART_HOUR_FORMATTER)
            .uppercase(Locale.getDefault())
    }

    private fun stockRatio(ingredient: IngredientRowDto): Double {
        return if (ingredient.minimumStock <= 0.0) {
            Double.MAX_VALUE
        } else {
            ingredient.currentStock / ingredient.minimumStock
        }
    }

    private data class RawDashboardData(
        val orders: List<OrderRecord>,
        val orderItems: List<OrderItemRowDto>,
        val ingredients: List<IngredientRowDto>,
        val recipes: List<RecipeRowDto>,
        val productCatalog: List<ProductVariantStockDto>
    )

    private data class OrderRecord(
        val id: String,
        val orderNumber: String,
        val customerName: String,
        val createdAt: java.time.ZonedDateTime,
        val localDate: LocalDate,
        val subtotal: Double?,
        val discountLabel: String?,
        val discountAmount: Double,
        val total: Double,
        val status: String,
        val isCompleted: Boolean,
        val isActive: Boolean
    )

    @Serializable
    private data class OrderRowDto(
        @SerialName("order_id")
        val orderId: String,
        @SerialName("order_number")
        val orderNumber: String,
        @SerialName("order_customer_name")
        val orderCustomerName: String? = null,
        @SerialName("created_at")
        val createdAt: String,
        @SerialName("order_total")
        val orderTotal: Double,
        @SerialName("order_subtotal")
        val orderSubtotal: Double? = null,
        @SerialName("order_discount_label")
        val orderDiscountLabel: String? = null,
        @SerialName("order_discount_amount")
        val orderDiscountAmount: Double? = null,
        @SerialName("order_status")
        val orderStatus: String
    )

    @Serializable
    private data class OrderItemRowDto(
        @SerialName("order_id")
        val orderId: String,
        @SerialName("product_variant_id")
        val productVariantId: String? = null,
        @SerialName("order_item_product_name")
        val productName: String,
        @SerialName("order_item_variant_name")
        val variantName: String,
        @SerialName("order_item_quantity")
        val quantity: Int,
        @SerialName("order_item_line_total")
        val lineTotal: Double
    )

    @Serializable
    private data class IngredientRowDto(
        @SerialName("ingredient_id")
        val ingredientId: String,
        @SerialName("ingredient_name")
        val ingredientName: String,
        @SerialName("ingredient_unit")
        val ingredientUnit: String,
        @SerialName("ingredient_current_stock")
        val currentStock: Double,
        @SerialName("ingredient_minimum_stock")
        val minimumStock: Double,
        @SerialName("ingredient_cost_per_unit")
        val costPerUnit: Double
    )

    @Serializable
    private data class RecipeRowDto(
        @SerialName("product_variant_id")
        val productVariantId: String,
        @SerialName("ingredient_id")
        val ingredientId: String,
        @SerialName("required_quantity")
        val requiredQuantity: Double
    )

    private companion object {
        const val TAG = "DashboardRepository"
        const val ORDERS_TABLE = "orders"
        const val ORDER_ITEMS_TABLE = "order_items"
        const val INGREDIENTS_TABLE = "ingredients"
        const val VARIANT_INGREDIENTS_TABLE = "variant_ingredients"
        const val PRODUCT_VARIANT_STOCK_VIEW = "product_variant_stock_view"

        const val STATUS_COMPLETED = "completed"
        const val STATUS_PENDING = "pending"
        const val STATUS_PREPARING = "preparing"

        const val STANDARD_VARIANT = "standard"
        const val COMBO_VARIANT = "combo"
        const val UNCATEGORIZED = "Uncategorized"

        const val MAX_ALERT_COUNT = 3
        const val MAX_TOP_ITEMS = 3
        const val MAX_RECENT_ORDERS = 10
        const val CRITICAL_THRESHOLD_RATIO = 0.5
        const val DEFAULT_CUSTOMER_NAME = "Walk-in"
        const val NO_ITEMS_PREVIEW = "No items"

        val CHART_HOUR_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("ha", Locale.getDefault())
        val WEEK_LABEL_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
        val MONTH_LABEL_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
    }
}
