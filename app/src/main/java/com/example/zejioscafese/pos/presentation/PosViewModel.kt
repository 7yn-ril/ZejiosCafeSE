package com.example.zejioscafese.pos.presentation

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zejioscafese.orders.data.repository.CheckoutOrderLine
import com.example.zejioscafese.orders.data.repository.CheckoutOrderPayload
import com.example.zejioscafese.orders.data.repository.OrderRepository
import com.example.zejioscafese.orders.model.CafeOrder
import com.example.zejioscafese.pos.data.model.OrderItem
import com.example.zejioscafese.pos.data.model.Product
import com.example.zejioscafese.pos.data.repository.CategoryRepository
import com.example.zejioscafese.pos.data.repository.ProductRepository
import java.util.Locale
import kotlin.math.round
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class PosViewModel(
    private val productRepository: ProductRepository = ProductRepository(),
    private val categoryRepository: CategoryRepository = CategoryRepository(),
    private val orderRepository: OrderRepository = OrderRepository()
) : ViewModel() {

    data class PaginationState(
        val currentPage: Int,
        val totalPages: Int,
        val totalItems: Int
    ) {
        val canGoPrevious: Boolean get() = currentPage > 1
        val canGoNext: Boolean get() = currentPage < totalPages
    }

    enum class PaymentMethod {
        CASH,
        GCASH,
        CARD
    }

    enum class SortOption {
        NAME_ASC,
        NAME_DESC,
        PRICE_ASC,
        PRICE_DESC
    }

    private var allProducts: List<Product> = emptyList()
    private var filteredProducts: List<Product> = emptyList()
    private var currentProductPageIndex: Int = 0

    private val _categories = MutableLiveData(listOf(CategoryRepository.ALL_CATEGORY))
    val categories: LiveData<List<String>> = _categories

    private val _selectedCategory = MutableLiveData(CategoryRepository.ALL_CATEGORY)
    val selectedCategory: LiveData<String> = _selectedCategory

    private val _searchQuery = MutableLiveData("")

    private val _selectedSortOption = MutableLiveData(SortOption.NAME_ASC)
    val selectedSortOption: LiveData<SortOption> = _selectedSortOption

    private val _products = MutableLiveData<List<Product>>(emptyList())
    val products: LiveData<List<Product>> = _products

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

    private val _isMenuLoading = MutableLiveData(false)
    val isMenuLoading: LiveData<Boolean> = _isMenuLoading

    private val _menuLoadError = MutableLiveData<String?>(null)
    val menuLoadError: LiveData<String?> = _menuLoadError

    private val _isCheckoutInProgress = MutableLiveData(false)
    val isCheckoutInProgress: LiveData<Boolean> = _isCheckoutInProgress

    private val _checkoutError = MutableLiveData<String?>(null)
    val checkoutError: LiveData<String?> = _checkoutError

    private var orderCounter = 1024

    private val _orderNumber = MutableLiveData("#POS-1024")
    val orderNumber: LiveData<String> = _orderNumber

    private val _checkoutEvent = MutableLiveData<CafeOrder?>(null)
    val checkoutEvent: LiveData<CafeOrder?> = _checkoutEvent

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

    fun checkout(customerName: String? = null) {
        val itemsToCheckout = _orderItems.value.orEmpty()
        if (itemsToCheckout.isEmpty() || _isCheckoutInProgress.value == true) {
            return
        }

        val currentOrderNumber = _orderNumber.value ?: formatOrderNumber(orderCounter)

        viewModelScope.launch {
            _isCheckoutInProgress.value = true
            _checkoutError.value = null

            try {
                val savedOrder = orderRepository.saveCheckoutOrder(
                    payload = CheckoutOrderPayload(
                        customerName = customerName?.trim()?.takeIf(String::isNotBlank),
                        subtotal = _subtotal.value ?: 0.0,
                        tax = _tax.value ?: 0.0,
                        total = _total.value ?: 0.0,
                        paymentMethod = PaymentMethod.CASH.name.lowercase(Locale.US),
                        status = "completed",
                        items = buildCheckoutLines(itemsToCheckout)
                    ),
                    suggestedOrderNumber = currentOrderNumber
                )

                clearOrder()
                setNextOrderNumberAfter(savedOrder.id)
                _checkoutEvent.value = savedOrder
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to save checkout order to Supabase", exception)
                _checkoutError.value = exception.message ?: "Failed to save order."
            } finally {
                _isCheckoutInProgress.value = false
            }
        }
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

    private fun loadMenuData() {
        viewModelScope.launch {
            val previousProducts = allProducts
            val previousCategories = _categories.value.orEmpty()

            _isMenuLoading.value = true
            _menuLoadError.value = null

            try {
                coroutineScope {
                    val productsDeferred = async { productRepository.fetchProducts() }
                    val categoriesDeferred = async { categoryRepository.fetchCategories() }

                    val fetchedProducts = productsDeferred.await()
                    val fetchedCategories = categoriesDeferred.await()

                    allProducts = fetchedProducts
                    _categories.value = buildVisibleCategories(
                        fetchedCategories = fetchedCategories,
                        products = fetchedProducts
                    )

                    if ((_selectedCategory.value ?: CategoryRepository.ALL_CATEGORY) !in _categories.value.orEmpty()) {
                        _selectedCategory.value = CategoryRepository.ALL_CATEGORY
                    }

                    refreshProductList(resetPage = false)
                    syncOrderState()
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to load POS menu from Supabase", exception)
                allProducts = previousProducts
                _categories.value = previousCategories.ifEmpty { listOf(CategoryRepository.ALL_CATEGORY) }
                refreshProductList(resetPage = false)
                syncOrderState()
                _menuLoadError.value = exception.message ?: "Failed to load menu data."
            } finally {
                _isMenuLoading.value = false
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

        filteredProducts = when (_selectedSortOption.value ?: SortOption.NAME_ASC) {
            SortOption.NAME_ASC -> filtered.sortedBy { it.name.lowercase(Locale.getDefault()) }
            SortOption.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase(Locale.getDefault()) }
            SortOption.PRICE_ASC -> filtered.sortedBy { it.price }
            SortOption.PRICE_DESC -> filtered.sortedByDescending { it.price }
        }

        if (resetPage) {
            currentProductPageIndex = 0
        }

        publishProductPage()
    }

    private fun updateProductPage(targetPageIndex: Int) {
        val totalPages = filteredProducts.pageCount(POS_PAGE_SIZE)
        if (totalPages <= 1) return

        val boundedIndex = targetPageIndex.coerceIn(0, totalPages - 1)
        if (boundedIndex == currentProductPageIndex) return

        currentProductPageIndex = boundedIndex
        publishProductPage()
    }

    private fun publishProductPage() {
        val totalPages = filteredProducts.pageCount(POS_PAGE_SIZE)
        currentProductPageIndex = currentProductPageIndex.coerceIn(0, totalPages - 1)

        val fromIndex = currentProductPageIndex * POS_PAGE_SIZE
        val pagedProducts = filteredProducts.drop(fromIndex).take(POS_PAGE_SIZE)

        _products.value = pagedProducts
        _productPaginationState.value = PaginationState(
            currentPage = currentProductPageIndex + 1,
            totalPages = totalPages,
            totalItems = filteredProducts.size
        )
    }

    private fun updateQuantity(product: Product, delta: Int) {
        val updatedQuantity = (orderQuantitiesStore[product.id] ?: 0) + delta
        if (updatedQuantity <= 0) {
            orderQuantitiesStore.remove(product.id)
        } else {
            orderQuantitiesStore[product.id] = updatedQuantity
        }
        syncOrderState()
    }

    private fun syncOrderState() {
        val items = orderQuantitiesStore.mapNotNull { (productId, quantity) ->
            allProducts.firstOrNull { it.id == productId }?.let { product ->
                OrderItem(product = product, quantity = quantity)
            }
        }

        _orderQuantities.value = LinkedHashMap(orderQuantitiesStore)
        _orderItems.value = items

        val subtotalAmount = items.sumOf { it.lineTotal }
        val taxAmount = 0.0
        _subtotal.value = subtotalAmount.roundToTwoDecimals()
        _tax.value = taxAmount.roundToTwoDecimals()
        _total.value = subtotalAmount.roundToTwoDecimals()
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

    private companion object {
        const val TAG = "PosViewModel"
        val ORDER_NUMBER_REGEX = Regex("^#POS-(\\d+)$")
        const val ORDER_NUMBER_TEMPLATE = "#POS-%04d"
        const val POS_PAGE_SIZE = 12
    }
}
