package com.example.zejioscafese.ui

import com.example.zejioscafese.inventory.data.model.ProductCategoryOption
import com.example.zejioscafese.inventory.data.model.ProductEditorDraft
import com.example.zejioscafese.inventory.data.model.ProductRecipeIngredient
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

    data class PaginationState(
        val currentPage: Int,
        val totalPages: Int,
        val totalItems: Int
    ) {
        val canGoPrevious: Boolean get() = currentPage > 1
        val canGoNext: Boolean get() = currentPage < totalPages
    }

    private val allIngredients = mutableListOf<Ingredient>()
    private val allProducibleProducts = mutableListOf<ProducibleProduct>()
    private val productCategories = mutableListOf<ProductCategoryOption>()
    private val productRecipeMap = mutableMapOf<String, List<ProductRecipeIngredient>>()
    private var filteredIngredients = emptyList<Ingredient>()
    private var filteredProducibleProducts = emptyList<ProducibleProduct>()
    private var ingredientPageIndex = 0
    private var productionPageIndex = 0

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

    private val _averageProduciblePrice = MutableLiveData(0.0)
    val averageProduciblePrice: LiveData<Double> = _averageProduciblePrice

    private val _totalProducibleProductCount = MutableLiveData(0)
    val totalProducibleProductCount: LiveData<Int> = _totalProducibleProductCount

    private val _inventoryError = MutableLiveData<String?>(null)
    val inventoryError: LiveData<String?> = _inventoryError

    private val _screenMode = MutableLiveData(ScreenMode.INGREDIENTS)
    val screenMode: LiveData<ScreenMode> = _screenMode

    private val _paginationState = MutableLiveData(
        PaginationState(
            currentPage = 1,
            totalPages = 1,
            totalItems = 0
        )
    )
    val paginationState: LiveData<PaginationState> = _paginationState

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
                applyFilters(resetActivePage = false)
            }
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery = query.trim().lowercase(Locale.getDefault())
        applyFilters(resetActivePage = true)
    }

    fun setCategory(category: String) {
        selectedCategory = category
        applyFilters(resetActivePage = true)
    }

    fun setSortMode(mode: SortMode) {
        sortMode = mode
        applyFilters(resetActivePage = true)
    }

    fun setScreenMode(mode: ScreenMode) {
        if (_screenMode.value == mode) return
        _screenMode.value = mode
        selectedCategory = ALL_CATEGORY
        when (mode) {
            ScreenMode.INGREDIENTS -> ingredientPageIndex = 0
            ScreenMode.PRODUCTION -> productionPageIndex = 0
        }
        applyFilters(resetActivePage = false)
    }

    fun goToPreviousPage() {
        when (_screenMode.value ?: ScreenMode.INGREDIENTS) {
            ScreenMode.INGREDIENTS -> publishIngredientPage(ingredientPageIndex - 1)
            ScreenMode.PRODUCTION -> publishProductionPage(productionPageIndex - 1)
        }
    }

    fun goToNextPage() {
        when (_screenMode.value ?: ScreenMode.INGREDIENTS) {
            ScreenMode.INGREDIENTS -> publishIngredientPage(ingredientPageIndex + 1)
            ScreenMode.PRODUCTION -> publishProductionPage(productionPageIndex + 1)
        }
    }

    fun addIngredient(ingredient: Ingredient) {
        allIngredients.add(ingredient)
        applyFilters(resetActivePage = false)

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
        applyFilters(resetActivePage = false)

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
        applyFilters(resetActivePage = false)

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

    fun addProduct(draft: ProductEditorDraft) {
        viewModelScope.launch {
            try {
                inventoryRepository.addProduct(draft)
                syncInventoryFromRemote()
                _inventoryError.value = null
            } catch (exception: Exception) {
                _inventoryError.value = exception.message ?: "Failed to add product."
                syncInventorySafely()
            }
        }
    }

    fun updateProduct(draft: ProductEditorDraft) {
        viewModelScope.launch {
            try {
                inventoryRepository.updateProduct(draft)
                syncInventoryFromRemote()
                _inventoryError.value = null
            } catch (exception: Exception) {
                _inventoryError.value = exception.message ?: "Failed to update product."
                syncInventorySafely()
            }
        }
    }

    fun softDeleteProduct(product: ProducibleProduct) {
        allProducibleProducts.removeAll { it.id == product.id }
        productRecipeMap.remove(product.id)
        applyFilters(resetActivePage = false)

        viewModelScope.launch {
            try {
                inventoryRepository.softDeleteProduct(product)
                syncInventoryFromRemote()
                _inventoryError.value = null
            } catch (exception: Exception) {
                _inventoryError.value = exception.message ?: "Failed to remove product."
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

    fun getIngredientOptionsForEditor(): List<Ingredient> {
        return allIngredients.toList()
    }

    fun getProductCategoriesForEditor(): List<ProductCategoryOption> {
        return productCategories.toList()
    }

    fun getRecipeForProduct(productVariantId: String): List<ProductRecipeIngredient> {
        return productRecipeMap[productVariantId].orEmpty()
    }

    fun onInventoryErrorConsumed() {
        _inventoryError.value = null
    }

    private suspend fun syncInventoryFromRemote() {
        val snapshot = coroutineScope {
            val ingredientsDeferred = async { inventoryRepository.fetchIngredients() }
            val producibleDeferred = async { inventoryRepository.fetchProducibleProducts() }
            val categoriesDeferred = async { inventoryRepository.fetchProductCategories() }
            val recipeLinksDeferred = async {
                runCatching { inventoryRepository.fetchProductRecipeLinks() }.getOrDefault(emptyList())
            }

            val ingredients = ingredientsDeferred.await().distinctBy(Ingredient::id)
            val ingredientDirectory = ingredients.associateBy(Ingredient::id)
            val recipeLinks = recipeLinksDeferred.await()

            InventorySnapshot(
                ingredients = ingredients,
                producibleProducts = producibleDeferred.await().distinctBy(ProducibleProduct::id),
                productCategories = categoriesDeferred.await().distinctBy(ProductCategoryOption::id),
                productRecipes = recipeLinks
                    .groupBy { it.productVariantId }
                    .mapValues { (_, links) ->
                        links.mapNotNull { link ->
                            ingredientDirectory[link.ingredientId]?.let { ingredient ->
                                ProductRecipeIngredient(
                                    ingredientId = ingredient.id,
                                    ingredientName = ingredient.name,
                                    ingredientUnit = ingredient.unit,
                                    requiredQuantity = link.requiredQuantity
                                )
                            }
                        }.sortedBy { it.ingredientName.lowercase(Locale.getDefault()) }
                    }
            )
        }

        allIngredients.clear()
        allIngredients.addAll(snapshot.ingredients)

        allProducibleProducts.clear()
        allProducibleProducts.addAll(snapshot.producibleProducts)

        productCategories.clear()
        productCategories.addAll(snapshot.productCategories)

        productRecipeMap.clear()
        productRecipeMap.putAll(snapshot.productRecipes)

        if (selectedCategory != ALL_CATEGORY && selectedCategory !in getCategories()) {
            selectedCategory = ALL_CATEGORY
        }

        applyFilters(resetActivePage = false)
    }

    private suspend fun syncInventorySafely() {
        runCatching { syncInventoryFromRemote() }
        applyFilters(resetActivePage = false)
    }

    private fun applyFilters(resetActivePage: Boolean = true) {
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

                filteredIngredients = filtered
                if (resetActivePage) {
                    ingredientPageIndex = 0
                }
                publishIngredientPage(ingredientPageIndex)
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

                filteredProducibleProducts = filtered
                if (resetActivePage) {
                    productionPageIndex = 0
                }
                publishProductionPage(productionPageIndex)
            }
        }

        refreshDerived()
    }

    private fun publishIngredientPage(targetPageIndex: Int) {
        val pagination = buildPaginationState(
            totalItems = filteredIngredients.size,
            pageSize = INVENTORY_PAGE_SIZE,
            requestedPageIndex = targetPageIndex
        )
        ingredientPageIndex = pagination.currentPage - 1
        val fromIndex = ingredientPageIndex * INVENTORY_PAGE_SIZE

        _ingredientList.value = filteredIngredients.drop(fromIndex).take(INVENTORY_PAGE_SIZE)
        if (_screenMode.value == ScreenMode.INGREDIENTS) {
            _paginationState.value = pagination
        }
    }

    private fun publishProductionPage(targetPageIndex: Int) {
        val pagination = buildPaginationState(
            totalItems = filteredProducibleProducts.size,
            pageSize = INVENTORY_PAGE_SIZE,
            requestedPageIndex = targetPageIndex
        )
        productionPageIndex = pagination.currentPage - 1
        val fromIndex = productionPageIndex * INVENTORY_PAGE_SIZE

        _producibleProductList.value = filteredProducibleProducts.drop(fromIndex).take(INVENTORY_PAGE_SIZE)
        if (_screenMode.value == ScreenMode.PRODUCTION) {
            _paginationState.value = pagination
        }
    }

    private fun buildPaginationState(
        totalItems: Int,
        pageSize: Int,
        requestedPageIndex: Int
    ): PaginationState {
        val totalPages = if (totalItems == 0) 1 else ((totalItems + pageSize - 1) / pageSize)
        val boundedIndex = requestedPageIndex.coerceIn(0, totalPages - 1)
        return PaginationState(
            currentPage = boundedIndex + 1,
            totalPages = totalPages,
            totalItems = totalItems
        )
    }

    private fun refreshDerived() {
        _lowStockIngredients.value = allIngredients.filter { it.currentStock <= it.minimumStock }
        _totalInventoryValue.value = allIngredients.sumOf { it.currentStock * it.costPerUnit }
        _totalIngredientCount.value = allIngredients.size
        _outOfStockProducts.value = allProducibleProducts.filter { it.availableQuantity <= 0 }
        _averageProduciblePrice.value = allProducibleProducts
            .map(ProducibleProduct::price)
            .average()
            .takeUnless(Double::isNaN)
            ?: 0.0
        _totalProducibleProductCount.value = allProducibleProducts.size
    }

    private fun currentDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private companion object {
        const val ALL_CATEGORY = "All"
        const val INGREDIENT_ID_PREFIX = "ING-"
        const val INVENTORY_PAGE_SIZE = 8
    }

    private data class InventorySnapshot(
        val ingredients: List<Ingredient>,
        val producibleProducts: List<ProducibleProduct>,
        val productCategories: List<ProductCategoryOption>,
        val productRecipes: Map<String, List<ProductRecipeIngredient>>
    )
}
