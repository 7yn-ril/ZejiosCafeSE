package com.example.zejioscafese.pos.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.zejioscafese.pos.data.model.OrderItem
import com.example.zejioscafese.pos.data.model.Product
import java.util.Locale
import kotlin.math.round

class PosViewModel : ViewModel() {

    enum class PaymentMethod {
        CASH,
        GCASH,
        CARD
    }

    private val allProducts: List<Product> = listOf(
        Product("d1", "Iced Caramel Latte", "Drinks", 165.0),
        Product("d2", "Cold Brew", "Drinks", 145.0),
        Product("d3", "Matcha Latte", "Drinks", 175.0),
        Product("d4", "Hot Americano", "Drinks", 120.0),
        Product("d5", "Vanilla Frappe", "Drinks", 185.0),
        Product("m1", "Chicken Pesto Panini", "Meals", 235.0),
        Product("m2", "Beef Tapa Bowl", "Meals", 265.0),
        Product("m3", "Creamy Mushroom Pasta", "Meals", 250.0),
        Product("m4", "Chicken Adobo Rice Bowl", "Meals", 195.0),
        Product("de1", "New York Cheesecake", "Desserts", 180.0),
        Product("de2", "Chocolate Brownie", "Desserts", 120.0),
        Product("de3", "Mango Cheesecake Slice", "Desserts", 160.0),
        Product("s1", "Truffle Fries", "Snacks", 135.0),
        Product("s2", "Nacho Bites", "Snacks", 150.0),
        Product("s3", "French Fries Basket", "Snacks", 110.0),
        Product("sp1", "Seasonal Signature Latte", "Specials", 195.0),
        Product("sp2", "Weekend Combo Set", "Specials", 320.0)
    )

    private val _categories = MutableLiveData(
        listOf("Drinks", "Meals", "Desserts", "Snacks", "Specials")
    )
    val categories: LiveData<List<String>> = _categories

    private val _selectedCategory = MutableLiveData("Drinks")
    val selectedCategory: LiveData<String> = _selectedCategory

    private val _searchQuery = MutableLiveData("")

    private val _products = MutableLiveData<List<Product>>(emptyList())
    val products: LiveData<List<Product>> = _products

    private val _orderItems = MutableLiveData<List<OrderItem>>(emptyList())
    val orderItems: LiveData<List<OrderItem>> = _orderItems

    private val _subtotal = MutableLiveData(0.0)
    val subtotal: LiveData<Double> = _subtotal

    private val _tax = MutableLiveData(0.0)
    val tax: LiveData<Double> = _tax

    private val _total = MutableLiveData(0.0)
    val total: LiveData<Double> = _total

    private val _selectedPaymentMethod = MutableLiveData(PaymentMethod.CASH)
    val selectedPaymentMethod: LiveData<PaymentMethod> = _selectedPaymentMethod

    private var orderCounter = 1024

    private val _orderNumber = MutableLiveData("#POS-1024")
    val orderNumber: LiveData<String> = _orderNumber

    /** Emits a one-shot event: formatted order number string after successful checkout */
    private val _checkoutEvent = MutableLiveData<String?>()
    val checkoutEvent: LiveData<String?> = _checkoutEvent

    private val orderQuantities = linkedMapOf<String, Int>()

    init {
        refreshProductList()
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
        refreshProductList()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        refreshProductList()
    }

    fun addProduct(product: Product) {
        orderQuantities[product.id] = (orderQuantities[product.id] ?: 0) + 1
        syncOrderItems()
    }

    fun removeProduct(productId: String) {
        orderQuantities.remove(productId)
        syncOrderItems()
    }

    fun clearOrder() {
        orderQuantities.clear()
        syncOrderItems()
    }

    fun setPaymentMethod(method: PaymentMethod) {
        _selectedPaymentMethod.value = method
    }

    /**
     * Performs checkout: clears the current order, increments the order counter,
     * and posts a one-shot event with the completed order number.
     */
    fun checkout() {
        val completedOrderNumber = _orderNumber.value ?: "#POS-$orderCounter"
        clearOrder()
        orderCounter++
        _orderNumber.value = "#POS-$orderCounter"
        _checkoutEvent.value = completedOrderNumber
    }

    /** Call after the checkout event has been consumed to avoid re-delivery */
    fun onCheckoutEventConsumed() {
        _checkoutEvent.value = null
    }

    private fun refreshProductList() {
        val selected = _selectedCategory.value.orEmpty()
        val query = _searchQuery.value.orEmpty().trim().lowercase(Locale.getDefault())

        val filtered = allProducts.filter { product ->
            val categoryMatch = selected.isBlank() || product.category == selected
            val queryMatch = query.isBlank() || product.name.lowercase(Locale.getDefault()).contains(query)
            categoryMatch && queryMatch
        }

        _products.value = filtered
    }

    private fun syncOrderItems() {
        val items = orderQuantities.mapNotNull { (productId, quantity) ->
            allProducts.firstOrNull { it.id == productId }?.let { product ->
                OrderItem(product = product, quantity = quantity)
            }
        }
        _orderItems.value = items

        val subtotalAmount = items.sumOf { it.lineTotal }
        val taxAmount = subtotalAmount * 0.12
        _subtotal.value = subtotalAmount.roundToTwoDecimals()
        _tax.value = taxAmount.roundToTwoDecimals()
        _total.value = (subtotalAmount + taxAmount).roundToTwoDecimals()
    }

    private fun Double.roundToTwoDecimals(): Double {
        return round(this * 100) / 100
    }
}
