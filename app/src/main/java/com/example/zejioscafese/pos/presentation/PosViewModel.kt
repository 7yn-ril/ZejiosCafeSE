package com.example.zejioscafese.pos.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.zejioscafese.R
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

    enum class SortOption {
        NAME_ASC,
        NAME_DESC,
        PRICE_ASC,
        PRICE_DESC
    }

    private val allProducts: List<Product> = listOf(
        Product(
            id = "d1",
            name = "Iced Caramel Latte",
            category = "Drinks",
            price = 165.0,
            stockLeft = 18,
            imageUrl = "https://images.unsplash.com/photo-1517701604599-bb29b565090c?auto=format&fit=crop&w=1200&q=80",
            imageResId = R.drawable.ic_coffee_24
        ),
        Product(
            id = "d2",
            name = "Cold Brew",
            category = "Drinks",
            price = 145.0,
            stockLeft = 24,
            imageUrl = "https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?auto=format&fit=crop&w=1200&q=80",
            imageResId = R.drawable.ic_coffee_24
        ),
        Product(
            id = "d3",
            name = "Matcha Latte",
            category = "Drinks",
            price = 175.0,
            stockLeft = 9,
            imageUrl = "https://images.unsplash.com/photo-1515823064-d6e0c04616a7?auto=format&fit=crop&w=1200&q=80",
            imageResId = R.drawable.ic_coffee_24
        ),
        Product(
            id = "d4",
            name = "Espresso",
            category = "Drinks",
            price = 110.0,
            stockLeft = 32,
            imageUrl = "https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?auto=format&fit=crop&w=1200&q=80",
            imageResId = R.drawable.ic_coffee_24
        ),
        Product(
            id = "d5",
            name = "Cappuccino",
            category = "Drinks",
            price = 150.0,
            stockLeft = 15,
            imageUrl = "https://images.unsplash.com/photo-1509042239860-f550ce710b93?auto=format&fit=crop&w=1200&q=80",
            imageResId = R.drawable.ic_coffee_24
        ),
        Product(
            id = "d6",
            name = "Americano",
            category = "Drinks",
            price = 120.0,
            stockLeft = 27,
            imageUrl = "https://images.unsplash.com/photo-1498804103079-a6351b050096?auto=format&fit=crop&w=1200&q=80",
            imageResId = R.drawable.ic_coffee_24
        ),
        Product(
            id = "d7",
            name = "Mocha Frappe",
            category = "Drinks",
            price = 185.0,
            stockLeft = 7,
            imageUrl = "https://images.unsplash.com/photo-1572490122747-3968b75cc699?auto=format&fit=crop&w=1200&q=80",
            imageResId = R.drawable.ic_coffee_24
        ),
        Product(
            id = "d8",
            name = "Hot Chocolate",
            category = "Drinks",
            price = 155.0,
            stockLeft = 14,
            imageUrl = "https://images.unsplash.com/photo-1542990253-0d0f5be5f44b?auto=format&fit=crop&w=1200&q=80",
            imageResId = R.drawable.ic_coffee_24
        ),
        Product(
            id = "m1",
            name = "Chicken Pesto Panini",
            category = "Meals",
            price = 235.0,
            stockLeft = 11,
            imageUrl = "https://images.unsplash.com/photo-1550317138-10000687a72b?auto=format&fit=crop&w=1200&q=80",
            imageResId = R.drawable.ic_meal_24
        ),
        Product(
            id = "de1",
            name = "Blueberry Cheesecake",
            category = "Desserts",
            price = 180.0,
            stockLeft = 6,
            imageUrl = "https://images.unsplash.com/photo-1533134242443-d4fd215305ad?auto=format&fit=crop&w=1200&q=80",
            imageResId = R.drawable.ic_dessert_24
        ),
        Product(
            id = "s1",
            name = "Butter Croissant",
            category = "Snacks",
            price = 95.0,
            stockLeft = 21,
            imageUrl = "https://images.unsplash.com/photo-1509440159596-0249088772ff?auto=format&fit=crop&w=1200&q=80",
            imageResId = R.drawable.ic_snack_24
        ),
        Product(
            id = "sp1",
            name = "Seasonal Signature Latte",
            category = "Specials",
            price = 195.0,
            stockLeft = 5,
            imageUrl = "https://images.unsplash.com/photo-1461023058943-07fcbe16d735?auto=format&fit=crop&w=1200&q=80",
            imageResId = R.drawable.ic_star_24
        )
    )

    private val _categories = MutableLiveData(
        listOf("All", "Drinks", "Meals", "Desserts", "Snacks", "Specials")
    )
    val categories: LiveData<List<String>> = _categories

    private val _selectedCategory = MutableLiveData("All")
    val selectedCategory: LiveData<String> = _selectedCategory

    private val _searchQuery = MutableLiveData("")

    private val _selectedSortOption = MutableLiveData(SortOption.NAME_ASC)
    val selectedSortOption: LiveData<SortOption> = _selectedSortOption

    private val _products = MutableLiveData<List<Product>>(emptyList())
    val products: LiveData<List<Product>> = _products

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

    private val _orderNumber = MutableLiveData("#POS-1024")
    val orderNumber: LiveData<String> = _orderNumber

    private val orderQuantitiesStore = linkedMapOf<String, Int>()

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

    fun setSortOption(option: SortOption) {
        _selectedSortOption.value = option
        refreshProductList()
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

    fun clearOrder() {
        orderQuantitiesStore.clear()
        syncOrderState()
    }

    fun setPaymentMethod(method: PaymentMethod) {
        _selectedPaymentMethod.value = method
    }

    private fun refreshProductList() {
        val selected = _selectedCategory.value.orEmpty()
        val query = _searchQuery.value.orEmpty().trim().lowercase(Locale.getDefault())

        val filtered = allProducts.filter { product ->
            val categoryMatch = selected.isBlank() || selected == "All" || product.category == selected
            val queryMatch = query.isBlank() ||
                product.name.lowercase(Locale.getDefault()).contains(query) ||
                product.category.lowercase(Locale.getDefault()).contains(query)
            categoryMatch && queryMatch
        }

        _products.value = when (_selectedSortOption.value ?: SortOption.NAME_ASC) {
            SortOption.NAME_ASC -> filtered.sortedBy { it.name.lowercase(Locale.getDefault()) }
            SortOption.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase(Locale.getDefault()) }
            SortOption.PRICE_ASC -> filtered.sortedBy { it.price }
            SortOption.PRICE_DESC -> filtered.sortedByDescending { it.price }
        }
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
        val taxAmount = subtotalAmount * 0.12
        _subtotal.value = subtotalAmount.roundToTwoDecimals()
        _tax.value = taxAmount.roundToTwoDecimals()
        _total.value = (subtotalAmount + taxAmount).roundToTwoDecimals()
    }

    private fun Double.roundToTwoDecimals(): Double {
        return round(this * 100) / 100
    }
}
