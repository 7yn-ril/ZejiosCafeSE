package com.example.zejioscafese.pos.presentation

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zejioscafese.core.network.NetworkErrorFormatter
import com.example.zejioscafese.orders.data.repository.CheckoutOrderLine
import com.example.zejioscafese.orders.data.repository.CheckoutOrderPayload
import com.example.zejioscafese.orders.data.repository.OrderRepository
import com.example.zejioscafese.orders.model.CafeOrder
import com.example.zejioscafese.pos.data.model.Discount
import com.example.zejioscafese.pos.data.model.OrderItem
import com.example.zejioscafese.pos.data.model.Product
import com.example.zejioscafese.pos.data.model.ProductGroup
import com.example.zejioscafese.pos.data.model.ProductRecipeRequirement
import com.example.zejioscafese.pos.data.repository.CategoryRepository
import com.example.zejioscafese.pos.data.repository.DiscountRepository
import com.example.zejioscafese.pos.data.repository.ProductRepository
import java.time.LocalDate
import java.util.Locale
import kotlin.math.floor
import kotlin.math.round
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PosViewModel(
    private val productRepository: ProductRepository = ProductRepository(),
    private val categoryRepository: CategoryRepository = CategoryRepository(),
    private val orderRepository: OrderRepository = OrderRepository(),
    private val discountRepository: DiscountRepository = DiscountRepository()
) : ViewModel() {

    data class PaginationState(
        val currentPage: Int,
        val totalPages: Int,
        val totalItems: Int
    ) {
        val canGoPrevious: Boolean get() = currentPage > 1
        val canGoNext: Boolean get() = currentPage < totalPages
    }

    data class StockLimitNotice(
        val productName: String,
        val message: String
    )

    enum class PaymentMethod {
        CASH,
        GCASH,
        MAYA,
        PAYMONGO
    }

    enum class OrderType {
        DINE_IN,
        TAKE_AWAY,
        DELIVERY
    }

    enum class SortOption {
        NAME_ASC,
        NAME_DESC,
        PRICE_ASC,
        PRICE_DESC
    }

    private var allProducts: List<Product> = emptyList()
    private var filteredProducts: List<Product> = emptyList()
    private var filteredGroups: List<ProductGroup> = emptyList()
    private var currentProductPageIndex: Int = 0
    private var menuLoadJob: Job? = null
    private var menuRetryJob: Job? = null
    private var consecutiveMenuLoadFailures: Int = 0

    private val _categories = MutableLiveData(listOf(CategoryRepository.ALL_CATEGORY))
    val categories: LiveData<List<String>> = _categories

    private val _selectedCategory = MutableLiveData(CategoryRepository.ALL_CATEGORY)
    val selectedCategory: LiveData<String> = _selectedCategory

    private val _searchQuery = MutableLiveData("")

    private val _selectedSortOption = MutableLiveData(SortOption.NAME_ASC)
    val selectedSortOption: LiveData<SortOption> = _selectedSortOption

    private val _products = MutableLiveData<List<Product>>(emptyList())
    val products: LiveData<List<Product>> = _products

    private val _productGroups = MutableLiveData<List<ProductGroup>>(emptyList())
    val productGroups: LiveData<List<ProductGroup>> = _productGroups

    private val _productPaginationState = MutableLiveData(
        PaginationState(
            currentPage = 1,
            totalPages = 1,
            totalItems = 0
        )
    )
    val productPaginationState: LiveData<PaginationState> = _productPaginationState

    private val _orderItems = MutableLiveData<List<OrderItem>>(emptyList())
    val orderItems: LiveData<List<OrderItem>> = _orderItems

    private val _orderQuantities = MutableLiveData<Map<String, Int>>(emptyMap())
    val orderQuantities: LiveData<Map<String, Int>> = _orderQuantities

    private val _subtotal = MutableLiveData(0.0)
    val subtotal: LiveData<Double> = _subtotal

    private val _tax = MutableLiveData(0.0)
    val tax: LiveData<Double> = _tax

    private val _total = MutableLiveData(0.0)
    val total: LiveData<Double> = _total

    private val _selectedPaymentMethod = MutableLiveData(PaymentMethod.CASH)
    val selectedPaymentMethod: LiveData<PaymentMethod> = _selectedPaymentMethod

    private val _selectedOrderType = MutableLiveData(OrderType.DINE_IN)
    val selectedOrderType: LiveData<OrderType> = _selectedOrderType

    private val _isMenuLoading = MutableLiveData(false)
    val isMenuLoading: LiveData<Boolean> = _isMenuLoading

    private val _menuLoadError = MutableLiveData<String?>(null)
    val menuLoadError: LiveData<String?> = _menuLoadError

    private val _stockLimitNotice = MutableLiveData<StockLimitNotice?>(null)
    val stockLimitNotice: LiveData<StockLimitNotice?> = _stockLimitNotice

    private val _isCheckoutInProgress = MutableLiveData(false)
    val isCheckoutInProgress: LiveData<Boolean> = _isCheckoutInProgress

    private val _checkoutError = MutableLiveData<String?>(null)
    val checkoutError: LiveData<String?> = _checkoutError

    private var orderCounter = 1024

    private val _orderNumber = MutableLiveData("#POS-1024")
    val orderNumber: LiveData<String> = _orderNumber

    private val _checkoutEvent = MutableLiveData<CafeOrder?>(null)
    val checkoutEvent: LiveData<CafeOrder?> = _checkoutEvent

    // CHANGE: Discounts — replaces the hard-coded 10/20/50 radio in the
    // checkout dialog. availableDiscounts is everything active for today
    // (built-ins + custom rows whose date range covers LocalDate.now()).
    // selectedDiscount drives the recomputed total and is sent through
    // to the saved order so reports can attribute revenue accurately.
    private val _availableDiscounts = MutableLiveData<List<Discount>>(emptyList())
    val availableDiscounts: LiveData<List<Discount>> = _availableDiscounts

    private val _selectedDiscount = MutableLiveData<Discount?>(null)
    val selectedDiscount: LiveData<Discount?> = _selectedDiscount

    private val _discountAmount = MutableLiveData(0.0)
    val discountAmount: LiveData<Double> = _discountAmount

    private val orderQuantitiesStore = linkedMapOf<String, Int>()

    init {
        loadMenuData()
        refreshSuggestedOrderNumber()
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
        refreshProductList()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        refreshProductList()
    }

    fun setSortOption(option: SortOption) {
        _selectedSortOption.value = option
        refreshProductList()
    }

    fun refreshMenu() {
        consecutiveMenuLoadFailures = 0
        menuRetryJob?.cancel()
        loadMenuData()
    }

    fun goToPreviousProductPage() {
        updateProductPage(currentProductPageIndex - 1)
    }

    fun goToNextProductPage() {
        updateProductPage(currentProductPageIndex + 1)
    }

    fun increaseProduct(product: Product) {
        updateQuantity(product, 1)
    }

    fun decreaseProduct(product: Product) {
        updateQuantity(product, -1)
    }

    fun increaseOrderItem(item: OrderItem) {
        updateQuantity(item.product, 1)
    }

    fun decreaseOrderItem(item: OrderItem) {
        updateQuantity(item.product, -1)
    }

    fun removeProduct(productId: String) {
        orderQuantitiesStore.remove(productId)
        syncOrderState()
    }

    fun clearOrder() {
        orderQuantitiesStore.clear()
        syncOrderState()
    }

    fun setPaymentMethod(method: PaymentMethod) {
        _selectedPaymentMethod.value = method
    }

    fun setOrderType(type: OrderType) {
        _selectedOrderType.value = type
    }

    fun setDiscount(discount: Discount?) {
        _selectedDiscount.value = discount
        recomputeTotals()
    }

    fun refreshAvailableDiscounts() {
        viewModelScope.launch { loadAvailableDiscounts() }
    }

    fun onDiscountCreated(discount: Discount) {
        // Newly-created custom discount should appear in the dropdown
        // immediately and be the active selection for the in-flight cart.
        val updated = (_availableDiscounts.value.orEmpty() + discount)
            .distinctBy(Discount::id)
        _availableDiscounts.value = updated
        setDiscount(discount)
    }

    fun checkout(
        customerName: String? = null,
        discountLabel: String? = null,
        discountAmount: Double = 0.0,
        finalTotal: Double? = null,
        paymentReference: String? = null,
        paymentProvider: String? = null,
        paymentStatus: String? = null
    ) {
        val itemsToCheckout = _orderItems.value.orEmpty()
        if (itemsToCheckout.isEmpty() || _isCheckoutInProgress.value == true) {
            return
        }

        val currentOrderNumber = _orderNumber.value ?: formatOrderNumber(orderCounter)
        val subtotal = _subtotal.value ?: 0.0
        val tax = _tax.value ?: 0.0
        // CHANGE: Discounts — the selectedDiscount LiveData is the source of
        // truth; the caller-passed discountLabel/amount/finalTotal arguments
        // are kept for backwards compatibility with the existing dialog but
        // the id/percent now ride along to the saved order too.
        val activeDiscount = _selectedDiscount.value
        val cartTotal = _total.value ?: (subtotal - (_discountAmount.value ?: 0.0))
        val resolvedDiscountAmount = (
            discountAmount.takeIf { it > 0.0 }
                ?: _discountAmount.value
                ?: 0.0
        ).coerceIn(0.0, subtotal)
        val resolvedDiscountLabel = discountLabel?.trim()?.takeIf(String::isNotBlank)
            ?: activeDiscount?.displayLabel()
        val savedTotal = finalTotal?.coerceIn(0.0, subtotal)
            ?: (subtotal - resolvedDiscountAmount).coerceAtLeast(0.0)

        viewModelScope.launch {
            _isCheckoutInProgress.value = true
            _checkoutError.value = null

            try {
                // CHANGE: Orders — pass the cart's selected order type
                // through to the RPC. TAKE_AWAY (cart enum) maps to
                // 'takeout' (DB token) so the SQL deduction runs.
                val savedOrder = orderRepository.saveCheckoutOrder(
                    payload = CheckoutOrderPayload(
                        customerName = customerName?.trim()?.takeIf(String::isNotBlank),
                        subtotal = subtotal,
                        tax = tax,
                        total = savedTotal.roundToTwoDecimals(),
                        discountLabel = resolvedDiscountLabel,
                        discountAmount = resolvedDiscountAmount.roundToTwoDecimals(),
                        discountId = activeDiscount?.id,
                        discountPercent = activeDiscount?.percent,
                        paymentMethod = (_selectedPaymentMethod.value ?: PaymentMethod.CASH).name.lowercase(Locale.US),
                        paymentProvider = paymentProvider?.trim()?.takeIf(String::isNotBlank),
                        paymentReference = paymentReference?.trim()?.takeIf(String::isNotBlank),
                        paymentStatus = paymentStatus?.trim()?.takeIf(String::isNotBlank),
                        status = "preparing",
                        items = buildCheckoutLines(itemsToCheckout),
                        orderType = when (_selectedOrderType.value ?: OrderType.DINE_IN) {
                            OrderType.DINE_IN -> "dine_in"
                            OrderType.TAKE_AWAY -> "takeout"
                            OrderType.DELIVERY -> "delivery"
                        }
                    ),
                    suggestedOrderNumber = currentOrderNumber
                )

                clearOrder()
                _selectedDiscount.value = null
                _discountAmount.value = 0.0
                setNextOrderNumberAfter(savedOrder.id)
                _checkoutEvent.value = savedOrder
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to save checkout order to Supabase", exception)
                _checkoutError.value = NetworkErrorFormatter.toUserMessage(
                    exception = exception,
                    fallbackMessage = "Failed to save order."
                )
            } finally {
                _isCheckoutInProgress.value = false
            }
        }
    }

    fun completeExternalCheckout(savedOrderNumber: String) {
        clearOrder()
        _selectedDiscount.value = null
        _discountAmount.value = 0.0
        setNextOrderNumberAfter(savedOrderNumber)
    }

    fun onCheckoutEventConsumed() {
        _checkoutEvent.value = null
    }

    fun onCheckoutErrorConsumed() {
        _checkoutError.value = null
    }

    fun onMenuLoadErrorConsumed() {
        _menuLoadError.value = null
    }

    fun onStockLimitNoticeConsumed() {
        _stockLimitNotice.value = null
    }

    private fun loadMenuData() {
        if (menuLoadJob?.isActive == true) {
            return
        }

        menuLoadJob = viewModelScope.launch {
            val previousProducts = allProducts
            val previousCategories = _categories.value.orEmpty()

            _isMenuLoading.value = true
            _menuLoadError.value = null

            try {
                coroutineScope {
                    val productsDeferred = async { productRepository.fetchProducts() }
                    val categoriesDeferred = async { categoryRepository.fetchCategories() }
                    val discountsDeferred = async {
                        runCatching { discountRepository.fetchActiveDiscounts() }.getOrDefault(emptyList())
                    }

                    val fetchedProducts = normalizeMenuSizeAliases(productsDeferred.await())
                    val fetchedCategories = categoriesDeferred.await()
                    val fetchedDiscounts = discountsDeferred.await()

                    allProducts = fetchedProducts
                    _categories.value = buildVisibleCategories(
                        fetchedCategories = fetchedCategories,
                        products = fetchedProducts
                    )
                    _availableDiscounts.value = fetchedDiscounts

                    if ((_selectedCategory.value ?: CategoryRepository.ALL_CATEGORY) !in _categories.value.orEmpty()) {
                        _selectedCategory.value = CategoryRepository.ALL_CATEGORY
                    }

                    refreshProductList(resetPage = false)
                    syncOrderState()
                    consecutiveMenuLoadFailures = 0
                    menuRetryJob?.cancel()
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to load POS menu from Supabase", exception)
                allProducts = previousProducts
                _categories.value = previousCategories.ifEmpty { listOf(CategoryRepository.ALL_CATEGORY) }
                refreshProductList(resetPage = false)
                syncOrderState()
                consecutiveMenuLoadFailures += 1
                val shouldRetryEmptyStartup =
                    previousProducts.isEmpty() &&
                        consecutiveMenuLoadFailures < MAX_EMPTY_MENU_LOAD_ATTEMPTS
                if (shouldRetryEmptyStartup) {
                    scheduleMenuRetry()
                } else {
                    _menuLoadError.value = NetworkErrorFormatter.toUserMessage(
                        exception = exception,
                        fallbackMessage = "Failed to load menu data."
                    )
                }
            } finally {
                _isMenuLoading.value = false
                menuLoadJob = null
            }
        }
    }

    private fun scheduleMenuRetry() {
        if (menuRetryJob?.isActive == true) {
            return
        }
        menuRetryJob = viewModelScope.launch {
            delay(EMPTY_MENU_RETRY_DELAY_MS)
            if (allProducts.isEmpty()) {
                loadMenuData()
            }
        }
    }

    private fun refreshSuggestedOrderNumber() {
        viewModelScope.launch {
            try {
                val nextOrderNumber = orderRepository.fetchNextOrderNumber()
                orderCounter = extractOrderCounter(nextOrderNumber) ?: orderCounter
                _orderNumber.value = nextOrderNumber
            } catch (exception: Exception) {
                Log.w(TAG, "Falling back to local order number seed", exception)
            }
        }
    }

    private suspend fun loadAvailableDiscounts() {
        try {
            val fetched = discountRepository.fetchActiveDiscounts()
            _availableDiscounts.value = fetched
            // Drop a selection that no longer applies today (date range expired
            // while the user had the dialog open).
            val selected = _selectedDiscount.value
            if (selected != null && fetched.none { it.id == selected.id }) {
                _selectedDiscount.value = null
                recomputeTotals()
            }
        } catch (exception: Exception) {
            Log.w(TAG, "Failed to refresh discount list", exception)
        }
    }

    private fun buildVisibleCategories(
        fetchedCategories: List<String>,
        products: List<Product>
    ): List<String> {
        val productCategories = products
            .asSequence()
            .map(Product::category)
            .filter(String::isNotBlank)
            .distinct()
            .toList()

        val orderedCategories = fetchedCategories
            .asSequence()
            .filter { it != CategoryRepository.ALL_CATEGORY }
            .filter(productCategories::contains)
            .distinct()
            .toList()

        val missingCategories = productCategories
            .filterNot(orderedCategories::contains)
            .sorted()

        return listOf(CategoryRepository.ALL_CATEGORY) + orderedCategories + missingCategories
    }

    private fun refreshProductList(resetPage: Boolean = true) {
        val selected = _selectedCategory.value.orEmpty()
        val query = _searchQuery.value.orEmpty().trim().lowercase(Locale.getDefault())

        val filtered = allProducts.filter { product ->
            val categoryMatch = selected.isBlank() ||
                selected == CategoryRepository.ALL_CATEGORY ||
                product.category == selected
            val queryMatch = query.isBlank() ||
                product.name.lowercase(Locale.getDefault()).contains(query) ||
                product.category.lowercase(Locale.getDefault()).contains(query)
            categoryMatch && queryMatch
        }

        filteredProducts = filtered

        filteredGroups = groupProducts(filtered).let { groups ->
            when (_selectedSortOption.value ?: SortOption.NAME_ASC) {
                SortOption.NAME_ASC -> groups.sortedBy { it.displayName.lowercase(Locale.getDefault()) }
                SortOption.NAME_DESC -> groups.sortedByDescending { it.displayName.lowercase(Locale.getDefault()) }
                SortOption.PRICE_ASC -> groups.sortedBy { it.minPrice }
                SortOption.PRICE_DESC -> groups.sortedByDescending { it.maxPrice }
            }
        }

        if (resetPage) {
            currentProductPageIndex = 0
        }

        publishProductPage()
    }

    private fun groupProducts(products: List<Product>): List<ProductGroup> {
        val groupedMap = LinkedHashMap<String, MutableList<Product>>()
        products.forEach { product ->
            val key = product.sourceProductId ?: product.id
            groupedMap.getOrPut(key) { mutableListOf() }.add(product)
        }
        return groupedMap.map { (groupId, variants) ->
            val sortedVariants = variants.sortedBy { it.price }
            val first = sortedVariants.first()
            ProductGroup(
                groupId = groupId,
                displayName = first.sourceProductName ?: first.name,
                category = first.category,
                variants = sortedVariants,
                imageUrl = first.imageUrl,
                imageResId = first.imageResId
            )
        }
    }

    private fun normalizeMenuSizeAliases(products: List<Product>): List<Product> {
        if (products.isEmpty()) {
            return emptyList()
        }

        data class Candidate(
            val product: Product,
            val originalVariantName: String,
            val canonicalVariantName: String?
        )

        val candidates = products.map { product ->
            val originalVariantName = product.sourceVariantName.orEmpty()
            val canonicalVariantName = originalVariantName.toCanonicalBeverageSizeName()
            val normalizedProduct = canonicalVariantName?.let { canonicalName ->
                val productName = product.sourceProductName ?: product.name
                product.copy(
                    name = formatProductDisplayName(productName, canonicalName),
                    sourceVariantName = canonicalName
                )
            } ?: product
            Candidate(
                product = normalizedProduct,
                originalVariantName = originalVariantName,
                canonicalVariantName = canonicalVariantName
            )
        }

        return candidates
            .groupBy { candidate ->
                val productKey = candidate.product.sourceProductId ?: candidate.product.id
                candidate.canonicalVariantName
                    ?.let { "$productKey::${it.lowercase(Locale.US)}" }
                    ?: candidate.product.id
            }
            .values
            .map { duplicateCandidates ->
                duplicateCandidates
                    .sortedWith(
                        compareBy<Candidate> { candidate ->
                            val original = candidate.originalVariantName.normalizedVariantKey()
                            val canonical = candidate.canonicalVariantName?.normalizedVariantKey()
                            if (canonical != null && original == canonical) 0 else 1
                        }.thenByDescending { it.product.price }
                    )
                    .first()
                    .product
            }
    }

    private fun String.toCanonicalBeverageSizeName(): String? {
        return when (normalizedVariantKey()) {
            "mezzo", "16oz", "16ounce", "16ounces" -> "16oz"
            "grande", "dosa", "22oz", "22ounce", "22ounces" -> "22oz"
            else -> null
        }
    }

    private fun String.normalizedVariantKey(): String {
        return lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "")
    }

    private fun formatProductDisplayName(productName: String, variantName: String): String {
        return when {
            variantName.equals("standard", ignoreCase = true) -> productName
            variantName.equals("combo", ignoreCase = true) -> productName
            else -> "$productName ($variantName)"
        }
    }

    private fun updateProductPage(targetPageIndex: Int) {
        val totalPages = filteredGroups.pageCount(POS_PAGE_SIZE)
        if (totalPages <= 1) return

        val boundedIndex = targetPageIndex.coerceIn(0, totalPages - 1)
        if (boundedIndex == currentProductPageIndex) return

        currentProductPageIndex = boundedIndex
        publishProductPage()
    }

    private fun publishProductPage() {
        val totalPages = filteredGroups.pageCount(POS_PAGE_SIZE)
        currentProductPageIndex = currentProductPageIndex.coerceIn(0, totalPages - 1)

        val fromIndex = currentProductPageIndex * POS_PAGE_SIZE
        val pagedGroups = filteredGroups.drop(fromIndex).take(POS_PAGE_SIZE)

        _productGroups.value = pagedGroups
        _products.value = pagedGroups.flatMap { it.variants }
        _productPaginationState.value = PaginationState(
            currentPage = currentProductPageIndex + 1,
            totalPages = totalPages,
            totalItems = filteredGroups.size
        )
    }

    private fun updateQuantity(product: Product, delta: Int) {
        val cachedProduct = allProducts.firstOrNull { it.id == product.id }
        val currentProduct = when {
            delta > 0 && product.stockLeft <= 0 -> product
            cachedProduct != null -> cachedProduct
            else -> product
        }
        val stockLimit = currentProduct.stockLeft.coerceAtLeast(0)

        if (delta > 0) {
            val stockLimitNotice = findStockLimitNotice(currentProduct, delta)
            if (stockLimitNotice != null) {
                _stockLimitNotice.value = stockLimitNotice
                return
            }
        }

        val updatedQuantity = (orderQuantitiesStore[currentProduct.id] ?: 0) + delta
        if (updatedQuantity <= 0) {
            orderQuantitiesStore.remove(currentProduct.id)
        } else {
            orderQuantitiesStore[currentProduct.id] = updatedQuantity.coerceAtMost(stockLimit)
        }
        syncOrderState()
    }

    private fun findStockLimitNotice(product: Product, delta: Int): StockLimitNotice? {
        val requestedQuantity = (orderQuantitiesStore[product.id] ?: 0) + delta
        if (requestedQuantity <= 0) return null

        if (product.recipeIngredients.isEmpty()) {
            return if (requestedQuantity > product.stockLeft.coerceAtLeast(0)) {
                buildVariantStockNotice(product)
            } else {
                null
            }
        }

        val candidateQuantities = LinkedHashMap(orderQuantitiesStore)
        candidateQuantities[product.id] = requestedQuantity

        val targetRequirements = product.recipeIngredients
            .groupBy(ProductRecipeRequirement::ingredientId)
            .mapValues { (_, requirements) -> requirements.sumOf(ProductRecipeRequirement::requiredQuantity) }

        val usageByIngredient = linkedMapOf<String, IngredientUsage>()
        candidateQuantities.forEach { (productId, quantity) ->
            val cartProduct = if (productId == product.id) {
                product
            } else {
                allProducts.firstOrNull { it.id == productId }
            } ?: return@forEach

            cartProduct.recipeIngredients.forEach { requirement ->
                val usage = usageByIngredient.getOrPut(requirement.ingredientId) {
                    IngredientUsage(
                        ingredientName = requirement.ingredientName,
                        currentStock = requirement.currentStock.coerceAtLeast(0.0),
                        targetRequiredQuantity = targetRequirements[requirement.ingredientId] ?: 0.0
                    )
                }
                usage.requiredQuantity += requirement.requiredQuantity * quantity
            }
        }

        val blockingUsage = usageByIngredient.values
            .filter { it.requiredQuantity > it.currentStock + STOCK_EPSILON }
            .sortedWith(
                compareBy<IngredientUsage> { if (it.currentStock <= 0.0) 0 else 1 }
                    .thenBy { it.ingredientName.lowercase(Locale.US) }
            )
            .firstOrNull()

        return blockingUsage?.let { buildIngredientStockNotice(product, it) }
    }

    private fun buildVariantStockNotice(product: Product): StockLimitNotice {
        val availableQuantity = product.stockLeft.coerceAtLeast(0)
        val message = if (availableQuantity == 1) {
            "Only 1 ${product.name} left."
        } else {
            "Only $availableQuantity ${product.name} left."
        }
        return StockLimitNotice(productName = product.name, message = message)
    }

    private fun buildIngredientStockNotice(
        product: Product,
        usage: IngredientUsage
    ): StockLimitNotice {
        if (usage.currentStock <= 0.0) {
            return StockLimitNotice(
                productName = product.name,
                message = "${usage.ingredientName} is out of stock."
            )
        }

        val targetRequired = usage.targetRequiredQuantity.takeIf { it > 0.0 } ?: 1.0
        val itemCapacity = floor(usage.currentStock / targetRequired).toInt().coerceAtLeast(0)
        val lead = if (itemCapacity == 1) {
            "Only 1 ${usage.ingredientName} left."
        } else {
            "Only $itemCapacity ${usage.ingredientName} portions left."
        }
        return StockLimitNotice(
            productName = product.name,
            message = "$lead It is already reserved by the current order."
        )
    }

    private fun syncOrderState() {
        val items = orderQuantitiesStore.mapNotNull { (productId, quantity) ->
            allProducts.firstOrNull { it.id == productId }?.let { product ->
                val boundedQuantity = quantity.coerceIn(0, product.stockLeft.coerceAtLeast(0))
                if (boundedQuantity <= 0) {
                    null
                } else {
                    OrderItem(product = product, quantity = boundedQuantity)
                }
            }
        }

        orderQuantitiesStore.clear()
        items.forEach { item ->
            orderQuantitiesStore[item.product.id] = item.quantity
        }

        _orderQuantities.value = LinkedHashMap(orderQuantitiesStore)
        _orderItems.value = items

        recomputeTotals()
    }

    private fun recomputeTotals() {
        val subtotalAmount = (_orderItems.value.orEmpty()).sumOf { it.lineTotal }
        val taxAmount = 0.0
        val rawDiscount = _selectedDiscount.value?.amountFor(subtotalAmount) ?: 0.0
        val boundedDiscount = rawDiscount.coerceIn(0.0, subtotalAmount)

        _subtotal.value = subtotalAmount.roundToTwoDecimals()
        _tax.value = taxAmount.roundToTwoDecimals()
        _discountAmount.value = boundedDiscount.roundToTwoDecimals()
        _total.value = (subtotalAmount - boundedDiscount).roundToTwoDecimals()
    }

    private fun buildCheckoutLines(items: List<OrderItem>): List<CheckoutOrderLine> {
        return items.map { item ->
            val product = item.product
            CheckoutOrderLine(
                productVariantId = product.id,
                sourceProductId = product.sourceProductId
                    ?: throw IllegalStateException("Missing product ID for ${product.name}. Refresh the menu and try again."),
                sourceProductName = product.sourceProductName ?: product.name,
                sourceVariantName = product.sourceVariantName ?: "Standard",
                unitPrice = product.price,
                quantity = item.quantity
            )
        }
    }

    private fun setNextOrderNumberAfter(savedOrderNumber: String) {
        val savedCounter = extractOrderCounter(savedOrderNumber) ?: orderCounter
        orderCounter = savedCounter + 1
        _orderNumber.value = formatOrderNumber(orderCounter)
    }

    private fun extractOrderCounter(orderNumber: String): Int? {
        return ORDER_NUMBER_REGEX.find(orderNumber)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    private fun formatOrderNumber(counter: Int): String {
        return ORDER_NUMBER_TEMPLATE.format(counter)
    }

    private fun Double.roundToTwoDecimals(): Double {
        return round(this * 100) / 100
    }

    private fun <T> List<T>.pageCount(pageSize: Int): Int {
        if (isEmpty()) return 1
        return ((size + pageSize - 1) / pageSize).coerceAtLeast(1)
    }

    private data class IngredientUsage(
        val ingredientName: String,
        val currentStock: Double,
        val targetRequiredQuantity: Double,
        var requiredQuantity: Double = 0.0
    )

    private companion object {
        const val TAG = "PosViewModel"
        val ORDER_NUMBER_REGEX = Regex("^#POS-(\\d+)$")
        const val ORDER_NUMBER_TEMPLATE = "#POS-%04d"

        const val POS_PAGE_SIZE = 12
        const val MAX_EMPTY_MENU_LOAD_ATTEMPTS = 3
        const val EMPTY_MENU_RETRY_DELAY_MS = 2_500L
        const val STOCK_EPSILON = 0.0001
    }
}
