package com.example.zejioscafese.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zejioscafese.inventory.data.model.ProducibleProduct
import com.example.zejioscafese.inventory.data.repository.InventoryRepository
import com.example.zejioscafese.pos.data.model.Ingredient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class InventoryViewModel(
    private val inventoryRepository: InventoryRepository = InventoryRepository()
) : ViewModel() {

    private val allIngredients = mutableListOf<Ingredient>()
    private val allProducibleProducts = mutableListOf<ProducibleProduct>()

    private val _ingredientList = MutableLiveData<List<Ingredient>>(emptyList())
    val ingredientList: LiveData<List<Ingredient>> = _ingredientList

    private val _producibleProductList = MutableLiveData<List<ProducibleProduct>>(emptyList())
    val producibleProductList: LiveData<List<ProducibleProduct>> = _producibleProductList

    private val _lowStockIngredients = MutableLiveData<List<Ingredient>>(emptyList())
    val lowStockIngredients: LiveData<List<Ingredient>> = _lowStockIngredients

    private val _totalInventoryValue = MutableLiveData(0.0)
    val totalInventoryValue: LiveData<Double> = _totalInventoryValue

    private val _totalIngredientCount = MutableLiveData(0)
    val totalIngredientCount: LiveData<Int> = _totalIngredientCount

    private val _outOfStockProducts = MutableLiveData<List<ProducibleProduct>>(emptyList())
    val outOfStockProducts: LiveData<List<ProducibleProduct>> = _outOfStockProducts

    private val _estimatedProductionValue = MutableLiveData(0.0)
    val estimatedProductionValue: LiveData<Double> = _estimatedProductionValue

    private val _totalProducibleProductCount = MutableLiveData(0)
    val totalProducibleProductCount: LiveData<Int> = _totalProducibleProductCount

    private val _inventoryError = MutableLiveData<String?>(null)
    val inventoryError: LiveData<String?> = _inventoryError

    private val _screenMode = MutableLiveData(ScreenMode.INGREDIENTS)
    val screenMode: LiveData<ScreenMode> = _screenMode

    private var searchQuery = ""
    private var selectedCategory = ALL_CATEGORY
    private var sortMode = SortMode.NAME

    enum class SortMode { NAME, STOCK_LEVEL, VALUE }
    enum class ScreenMode { INGREDIENTS, PRODUCTION }

    init {
        refreshInventory()
    }

    fun refreshInventory() {
        viewModelScope.launch {
            try {
                syncInventoryFromRemote()
                _inventoryError.value = null
            } catch (exception: Exception) {
                _inventoryError.value = exception.message ?: "Failed to load inventory."
                applyFilters()
            }
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery = query.trim().lowercase(Locale.getDefault())
        applyFilters()
    }

    fun setCategory(category: String) {
        selectedCategory = category
        applyFilters()
    }

    fun setSortMode(mode: SortMode) {
        sortMode = mode
        applyFilters()
    }

    fun setScreenMode(mode: ScreenMode) {
        if (_screenMode.value == mode) return
        _screenMode.value = mode
        selectedCategory = ALL_CATEGORY
        applyFilters()
    }

    fun addIngredient(ingredient: Ingredient) {
        allIngredients.add(ingredient)
        applyFilters()

        viewModelScope.launch {
            try {
                inventoryRepository.addIngredient(ingredient)
                syncInventoryFromRemote()
            } catch (exception: Exception) {
                _inventoryError.value = exception.message ?: "Failed to add ingredient."
                syncInventorySafely()
            }
        }
    }

    fun updateIngredient(updated: Ingredient) {
        val index = allIngredients.indexOfFirst { it.id == updated.id }
        if (index == -1) return

        allIngredients[index] = updated
        applyFilters()

        viewModelScope.launch {
            try {
                inventoryRepository.updateIngredient(updated)
                syncInventoryFromRemote()
            } catch (exception: Exception) {
                _inventoryError.value = exception.message ?: "Failed to update ingredient."
                syncInventorySafely()
            }
        }
    }

    fun restockIngredient(id: String, quantity: Double) {
        val index = allIngredients.indexOfFirst { it.id == id }
        if (index == -1) return

        val current = allIngredients[index]
        val updated = current.copy(
            currentStock = current.currentStock + quantity,
            lastRestocked = currentDate()
        )
        allIngredients[index] = updated
        applyFilters()

        viewModelScope.launch {
            try {
                inventoryRepository.restockIngredient(
                    ingredientId = id,
                    updatedStock = updated.currentStock
                )
                syncInventoryFromRemote()
            } catch (exception: Exception) {
                _inventoryError.value = exception.message ?: "Failed to restock ingredient."
                syncInventorySafely()
            }
        }
    }

    fun generateId(): String {
        val maxNum = allIngredients
            .mapNotNull { it.id.removePrefix(INGREDIENT_ID_PREFIX).toIntOrNull() }
            .maxOrNull() ?: 0
        return "$INGREDIENT_ID_PREFIX${(maxNum + 1).toString().padStart(3, '0')}"
    }

    fun getCategories(): List<String> {
        val categories = when (_screenMode.value ?: ScreenMode.INGREDIENTS) {
            ScreenMode.INGREDIENTS -> allIngredients.map(Ingredient::category)
            ScreenMode.PRODUCTION -> allProducibleProducts.map(ProducibleProduct::category)
        }

        return listOf(ALL_CATEGORY) + categories
            .filter(String::isNotBlank)
            .distinct()
            .sorted()
    }

    fun onInventoryErrorConsumed() {
        _inventoryError.value = null
    }

    private suspend fun syncInventoryFromRemote() {
        val snapshot = coroutineScope {
            val ingredientsDeferred = async { inventoryRepository.fetchIngredients() }
            val producibleDeferred = async { inventoryRepository.fetchProducibleProducts() }

            InventorySnapshot(
                ingredients = ingredientsDeferred.await().distinctBy(Ingredient::id),
                producibleProducts = producibleDeferred.await().distinctBy(ProducibleProduct::id)
            )
        }

        allIngredients.clear()
        allIngredients.addAll(snapshot.ingredients)

        allProducibleProducts.clear()
        allProducibleProducts.addAll(snapshot.producibleProducts)

        if (selectedCategory != ALL_CATEGORY && selectedCategory !in getCategories()) {
            selectedCategory = ALL_CATEGORY
        }

        applyFilters()
    }

    private suspend fun syncInventorySafely() {
        runCatching { syncInventoryFromRemote() }
        applyFilters()
    }

    private fun applyFilters() {
        when (_screenMode.value ?: ScreenMode.INGREDIENTS) {
            ScreenMode.INGREDIENTS -> {
                var filtered = allIngredients.toList()

                if (selectedCategory != ALL_CATEGORY) {
                    filtered = filtered.filter { it.category == selectedCategory }
                }

                if (searchQuery.isNotBlank()) {
                    filtered = filtered.filter {
                        it.name.lowercase(Locale.getDefault()).contains(searchQuery)
                    }
                }

                filtered = when (sortMode) {
                    SortMode.NAME -> filtered.sortedBy { it.name.lowercase(Locale.getDefault()) }
                    SortMode.STOCK_LEVEL -> filtered.sortedBy { ingredient ->
                        if (ingredient.minimumStock <= 0.0) Double.MAX_VALUE
                        else ingredient.currentStock / ingredient.minimumStock
                    }
                    SortMode.VALUE -> filtered.sortedByDescending { it.currentStock * it.costPerUnit }
                }

                _ingredientList.value = filtered
            }

            ScreenMode.PRODUCTION -> {
                var filtered = allProducibleProducts.toList()

                if (selectedCategory != ALL_CATEGORY) {
                    filtered = filtered.filter { it.category == selectedCategory }
                }

                if (searchQuery.isNotBlank()) {
                    filtered = filtered.filter {
                        it.name.lowercase(Locale.getDefault()).contains(searchQuery)
                    }
                }

                filtered = when (sortMode) {
                    SortMode.NAME -> filtered.sortedBy { it.name.lowercase(Locale.getDefault()) }
                    SortMode.STOCK_LEVEL -> filtered.sortedByDescending { it.availableQuantity }
                    SortMode.VALUE -> filtered.sortedByDescending { it.estimatedValue }
                }

                _producibleProductList.value = filtered
            }
        }

        refreshDerived()
    }

    private fun refreshDerived() {
        _lowStockIngredients.value = allIngredients.filter { it.currentStock <= it.minimumStock }
        _totalInventoryValue.value = allIngredients.sumOf { it.currentStock * it.costPerUnit }
        _totalIngredientCount.value = allIngredients.size
        _outOfStockProducts.value = allProducibleProducts.filter { it.availableQuantity <= 0 }
        _estimatedProductionValue.value = allProducibleProducts.sumOf { it.estimatedValue }
        _totalProducibleProductCount.value = allProducibleProducts.size
    }

    private fun currentDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private companion object {
        const val ALL_CATEGORY = "All"
        const val INGREDIENT_ID_PREFIX = "ING-"
    }

    private data class InventorySnapshot(
        val ingredients: List<Ingredient>,
        val producibleProducts: List<ProducibleProduct>
    )
}
