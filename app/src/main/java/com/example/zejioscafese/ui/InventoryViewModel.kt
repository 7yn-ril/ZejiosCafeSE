package com.example.zejioscafese.ui

import com.example.zejioscafese.inventory.data.model.ProductCategoryOption
import com.example.zejioscafese.inventory.data.model.ProductEditorDraft
import com.example.zejioscafese.inventory.data.model.ProductRecipeIngredient
import com.example.zejioscafese.inventory.data.model.RecipePricing
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zejioscafese.core.network.NetworkErrorFormatter
import com.example.zejioscafese.inventory.data.model.ProducibleProduct
import com.example.zejioscafese.inventory.data.remote.dto.ProductRecipeLinkDto
import com.example.zejioscafese.inventory.data.repository.InventoryRepository
import com.example.zejioscafese.pos.data.model.Ingredient
import com.example.zejioscafese.pos.data.model.IngredientStockStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
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
    private var sortDirection = SortDirection.ASCENDING
    private var refreshJob: Job? = null
    private var lastSuccessfulRefreshAt: Long = 0L
    private var categoryOrderRanks: Map<String, Int> = emptyMap()
    private var pendingRecipeScaling: List<RecipeScalingUpdate> = emptyList()

    private val _sortDirection = MutableLiveData(sortDirection)
    val sortDirectionLive: LiveData<SortDirection> = _sortDirection

    enum class SortMode { NAME, STOCK_LEVEL, VALUE }
    enum class SortDirection { ASCENDING, DESCENDING }
    enum class ScreenMode { INGREDIENTS, PRODUCTION }

    init {
        refreshInventory(force = true)
    }

    fun refreshInventory(force: Boolean = false) {
        if (refreshJob?.isActive == true) {
            return
        }
        if (!force && !shouldRefresh()) {
            return
        }

        refreshJob = viewModelScope.launch {
            try {
                syncInventoryFromRemote()
                _inventoryError.value = null
            } catch (exception: Exception) {
                _inventoryError.value = NetworkErrorFormatter.toUserMessage(
                    exception = exception,
                    fallbackMessage = "Failed to load inventory."
                )
                applyFilters(resetActivePage = false)
            } finally {
                refreshJob = null
            }
        }
    }

    fun refreshInventoryIfStale(maxAgeMs: Long = INVENTORY_REFRESH_INTERVAL_MS) {
        if (shouldRefresh(maxAgeMs)) {
            refreshInventory(force = true)
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
        if (mode == SortMode.VALUE) {
            sortDirection = SortDirection.DESCENDING
            _sortDirection.value = sortDirection
        }
        applyFilters(resetActivePage = true)
    }

    fun setSortDirection(direction: SortDirection) {
        if (sortDirection == direction) return
        sortDirection = direction
        _sortDirection.value = direction
        applyFilters(resetActivePage = true)
    }

    fun toggleSortDirection() {
        setSortDirection(
            if (sortDirection == SortDirection.ASCENDING) SortDirection.DESCENDING
            else SortDirection.ASCENDING
        )
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
                _inventoryError.value = NetworkErrorFormatter.toUserMessage(
                    exception = exception,
                    fallbackMessage = "Failed to add ingredient."
                )
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
                _inventoryError.value = NetworkErrorFormatter.toUserMessage(
                    exception = exception,
                    fallbackMessage = "Failed to update ingredient."
                )
                syncInventorySafely()
            }
        }
    }

    fun restockIngredient(id: String, quantity: Double) {
        if (quantity <= 0.0) {
            _inventoryError.value = RESTOCK_QUANTITY_ERROR
            return
        }

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
                _inventoryError.value = NetworkErrorFormatter.toUserMessage(
                    exception = exception,
                    fallbackMessage = "Failed to restock ingredient."
                )
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
                _inventoryError.value = NetworkErrorFormatter.toUserMessage(
                    exception = exception,
                    fallbackMessage = "Failed to add product."
                )
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
                _inventoryError.value = NetworkErrorFormatter.toUserMessage(
                    exception = exception,
                    fallbackMessage = "Failed to update product."
                )
                syncInventorySafely()
            }
        }
    }

    fun softDeleteProduct(product: ProducibleProduct) {
        val index = allProducibleProducts.indexOfFirst { it.id == product.id }
        if (index >= 0) {
            allProducibleProducts[index] = product.copy(isActive = false)
        }
        applyFilters(resetActivePage = false)

        viewModelScope.launch {
            try {
                inventoryRepository.softDeleteProduct(product)
                syncInventoryFromRemote()
                _inventoryError.value = null
            } catch (exception: Exception) {
                _inventoryError.value = NetworkErrorFormatter.toUserMessage(
                    exception = exception,
                    fallbackMessage = "Failed to remove product."
                )
                syncInventorySafely()
            }
        }
    }

    fun restoreProduct(product: ProducibleProduct) {
        val index = allProducibleProducts.indexOfFirst { it.id == product.id }
        if (index >= 0) {
            allProducibleProducts[index] = product.copy(isActive = true)
        }
        applyFilters(resetActivePage = false)

        viewModelScope.launch {
            try {
                inventoryRepository.restoreProduct(product)
                syncInventoryFromRemote()
                _inventoryError.value = null
            } catch (exception: Exception) {
                _inventoryError.value = NetworkErrorFormatter.toUserMessage(
                    exception = exception,
                    fallbackMessage = "Failed to restore product."
                )
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
            ScreenMode.PRODUCTION -> allProducibleProducts
                .filter(ProducibleProduct::isActive)
                .map(ProducibleProduct::category)
        }

        val sortedCategories = categories
            .filter(String::isNotBlank)
            .distinct()
            .sortedWith(
                compareByDescending<String> { categoryOrderRanks[it] ?: 0 }
                    .thenBy { it }
            )
        val hasDeletedProducts = (_screenMode.value ?: ScreenMode.INGREDIENTS) == ScreenMode.PRODUCTION &&
            allProducibleProducts.any { !it.isActive }

        // Place Deleted immediately after All when any inactive products
        // exist. Putting it at the end (the old behavior) buried it under
        // the long category list on tablets, so staff never found their
        // hidden products to restore them.
        return if (hasDeletedProducts) {
            listOf(ALL_CATEGORY, DELETED_CATEGORY) + sortedCategories
        } else {
            listOf(ALL_CATEGORY) + sortedCategories
        }
    }

    /** Number of soft-deleted products visible in the Products tab. */
    fun deletedProductCount(): Int {
        if ((_screenMode.value ?: ScreenMode.INGREDIENTS) != ScreenMode.PRODUCTION) return 0
        return allProducibleProducts.count { !it.isActive }
    }

    fun getIngredientOptionsForEditor(): List<Ingredient> {
        return allIngredients.toList()
    }

    fun getIngredientCategoriesForEditor(): List<String> {
        val existingCategories = allIngredients
            .map(Ingredient::category)
            .filter(String::isNotBlank)

        return (DEFAULT_INGREDIENT_CATEGORIES + existingCategories)
            .distinct()
            .sorted()
    }

    fun getProductCategoriesForEditor(): List<ProductCategoryOption> {
        return productCategories.toList()
    }

    fun hasProductWithName(name: String): Boolean {
        val normalized = name.trim()
        if (normalized.isBlank()) return false
        return allProducibleProducts.any { it.productName.equals(normalized, ignoreCase = true) }
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
            val variantCountsDeferred = async {
                runCatching { inventoryRepository.fetchOrderVariantCounts() }.getOrDefault(emptyMap())
            }

            val ingredients = ingredientsDeferred.await().distinctBy(Ingredient::id)
            val ingredientDirectory = ingredients.associateBy(Ingredient::id)
            val recipeLinks = recipeLinksDeferred.await()
            val rawRecipeLinksByVariant = recipeLinks.groupBy(ProductRecipeLinkDto::productVariantId)
            val rawProducibleProducts = producibleDeferred.await()
                .distinctBy(ProducibleProduct::id)

            val scaledRecipeLinksByVariant = applyBeverageRecipeScaling(
                products = rawProducibleProducts,
                recipeLinksByVariant = rawRecipeLinksByVariant,
                ingredientDirectory = ingredientDirectory
            )
            pendingRecipeScaling = collectRecipeScalingUpdates(
                originals = rawRecipeLinksByVariant,
                scaled = scaledRecipeLinksByVariant
            )

            val producibleProductsWithAvailability = rawProducibleProducts.map { product ->
                val availableQuantity = computeRecipeAvailableQuantity(
                    recipeLinks = scaledRecipeLinksByVariant[product.id].orEmpty(),
                    ingredientDirectory = ingredientDirectory
                )
                if (availableQuantity == null) {
                    product
                } else {
                    product.copy(availableQuantity = availableQuantity)
                }
            }
            val rawProducibleProductsScaled = producibleProductsWithAvailability

            val producibleProducts = rawProducibleProductsScaled
            val variantCounts = variantCountsDeferred.await()

            InventorySnapshot(
                ingredients = ingredients,
                producibleProducts = producibleProducts,
                productCategories = categoriesDeferred.await().distinctBy(ProductCategoryOption::id),
                productRecipes = scaledRecipeLinksByVariant
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
                    },
                variantOrderCounts = variantCounts
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

        val variantIdToCategory = allProducibleProducts
            .filter(ProducibleProduct::isActive)
            .associate { it.id to it.category }
        categoryOrderRanks = snapshot.variantOrderCounts.entries
            .groupBy({ variantIdToCategory[it.key].orEmpty() }, { it.value })
            .mapValues { (_, counts) -> counts.sum() }
            .filterKeys { it.isNotBlank() }

        if (selectedCategory != ALL_CATEGORY && selectedCategory !in getCategories()) {
            selectedCategory = ALL_CATEGORY
        }

        applyFilters(resetActivePage = false)
        lastSuccessfulRefreshAt = System.currentTimeMillis()

        flushPendingRecipeScaling()
    }

    private fun flushPendingRecipeScaling() {
        val updates = pendingRecipeScaling
        if (updates.isEmpty()) return
        pendingRecipeScaling = emptyList()

        viewModelScope.launch {
            updates.forEach { update ->
                runCatching {
                    inventoryRepository.updateVariantIngredientQuantity(
                        productVariantId = update.variantId,
                        ingredientId = update.ingredientId,
                        newRequiredQuantity = update.newRequiredQuantity
                    )
                }
            }
        }
    }

    private fun sizeOzForVariant(variantName: String): Double? {
        val normalized = variantName.trim().lowercase(Locale.US)
        return when {
            "22" in normalized -> 22.0
            "16" in normalized || normalized == "mezzo" -> BEVERAGE_BASE_SIZE_OZ
            else -> null
        }
    }

    private fun applyBeverageRecipeScaling(
        products: List<ProducibleProduct>,
        recipeLinksByVariant: Map<String, List<ProductRecipeLinkDto>>,
        ingredientDirectory: Map<String, Ingredient>
    ): Map<String, List<ProductRecipeLinkDto>> {
        val variantsByProduct = products.groupBy(ProducibleProduct::productId)
        val result = recipeLinksByVariant.toMutableMap()

        products.forEach { product ->
            val sizeOz = sizeOzForVariant(product.variantName) ?: return@forEach
            if (sizeOz == BEVERAGE_BASE_SIZE_OZ) return@forEach

            val baseSibling = variantsByProduct[product.productId]
                ?.firstOrNull { sizeOzForVariant(it.variantName) == BEVERAGE_BASE_SIZE_OZ }
                ?: return@forEach

            val myLinks = recipeLinksByVariant[product.id].orEmpty()
            val baseLinks = recipeLinksByVariant[baseSibling.id].orEmpty()
            if (myLinks.isEmpty() || baseLinks.isEmpty()) return@forEach
            if (!recipesIdentical(myLinks, baseLinks)) return@forEach

            val scaleFactor = sizeOz / BEVERAGE_BASE_SIZE_OZ
            result[product.id] = myLinks.map { link ->
                val isMl = ingredientDirectory[link.ingredientId]
                    ?.unit
                    ?.let { com.example.zejioscafese.pos.data.model.IngredientUnits.isMl(it) }
                    ?: false
                if (isMl) {
                    link.copy(requiredQuantity = RecipePricing.roundCurrency(link.requiredQuantity * scaleFactor))
                } else {
                    link
                }
            }
        }
        return result
    }

    private fun recipesIdentical(
        a: List<ProductRecipeLinkDto>,
        b: List<ProductRecipeLinkDto>
    ): Boolean {
        if (a.size != b.size) return false
        val aMap = a.associate { it.ingredientId to it.requiredQuantity }
        val bMap = b.associate { it.ingredientId to it.requiredQuantity }
        return aMap == bMap
    }

    private fun collectRecipeScalingUpdates(
        originals: Map<String, List<ProductRecipeLinkDto>>,
        scaled: Map<String, List<ProductRecipeLinkDto>>
    ): List<RecipeScalingUpdate> {
        val updates = mutableListOf<RecipeScalingUpdate>()
        scaled.forEach { (variantId, scaledLinks) ->
            val originalById = originals[variantId].orEmpty().associate { it.ingredientId to it.requiredQuantity }
            scaledLinks.forEach { link ->
                val original = originalById[link.ingredientId] ?: return@forEach
                if (abs(original - link.requiredQuantity) >= 0.01) {
                    updates.add(RecipeScalingUpdate(variantId, link.ingredientId, link.requiredQuantity))
                }
            }
        }
        return updates
    }

    private data class RecipeScalingUpdate(
        val variantId: String,
        val ingredientId: String,
        val newRequiredQuantity: Double
    )

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

                val ingredientComparator: Comparator<Ingredient> = when (sortMode) {
                    SortMode.NAME -> compareBy { it.name.lowercase(Locale.getDefault()) }
                    SortMode.STOCK_LEVEL -> compareBy { ingredient ->
                        if (ingredient.minimumStock <= 0.0) Double.MAX_VALUE
                        else ingredient.currentStock / ingredient.minimumStock
                    }
                    SortMode.VALUE -> compareBy { it.currentStock * it.costPerUnit }
                }
                filtered = filtered.sortedWith(
                    if (sortDirection == SortDirection.ASCENDING) ingredientComparator
                    else ingredientComparator.reversed()
                )

                filteredIngredients = filtered
                if (resetActivePage) {
                    ingredientPageIndex = 0
                }
                publishIngredientPage(ingredientPageIndex)
            }

            ScreenMode.PRODUCTION -> {
                var filtered = allProducibleProducts.toList()

                if (selectedCategory == DELETED_CATEGORY) {
                    filtered = filtered.filter { !it.isActive }
                } else {
                    filtered = filtered.filter(ProducibleProduct::isActive)
                }

                if (selectedCategory != ALL_CATEGORY && selectedCategory != DELETED_CATEGORY) {
                    filtered = filtered.filter { it.category == selectedCategory }
                }

                if (searchQuery.isNotBlank()) {
                    filtered = filtered.filter {
                        it.name.lowercase(Locale.getDefault()).contains(searchQuery)
                    }
                }

                val productComparator: Comparator<ProducibleProduct> = when (sortMode) {
                    SortMode.NAME -> compareBy { it.name.lowercase(Locale.getDefault()) }
                    SortMode.STOCK_LEVEL -> compareBy { it.availableQuantity }
                    SortMode.VALUE -> compareBy { it.estimatedValue }
                }
                filtered = filtered.sortedWith(
                    if (sortDirection == SortDirection.ASCENDING) productComparator
                    else productComparator.reversed()
                )

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
        _lowStockIngredients.value = allIngredients.filter {
            it.stockStatus != IngredientStockStatus.IN_STOCK
        }
        _totalInventoryValue.value = allIngredients.sumOf { it.currentStock * it.costPerUnit }
        _totalIngredientCount.value = allIngredients.size
        val activeProducts = allProducibleProducts.filter(ProducibleProduct::isActive)
        _outOfStockProducts.value = activeProducts.filter { it.availableQuantity <= 0 }
        _averageProduciblePrice.value = activeProducts
            .map(ProducibleProduct::price)
            .average()
            .takeUnless(Double::isNaN)
            ?: 0.0
        _totalProducibleProductCount.value = activeProducts.size
    }

    private fun computeRecipeAvailableQuantity(
        recipeLinks: List<ProductRecipeLinkDto>,
        ingredientDirectory: Map<String, Ingredient>
    ): Int? {
        val requiredLinks = recipeLinks.filter { it.requiredQuantity > 0.0 }
        if (requiredLinks.isEmpty()) return null

        return requiredLinks
            .minOfOrNull { link ->
                val currentStock = ingredientDirectory[link.ingredientId]
                    ?.currentStock
                    ?.coerceAtLeast(0.0)
                    ?: 0.0
                floor(currentStock / link.requiredQuantity).toInt()
            }
            ?.coerceAtLeast(0)
    }

    private fun currentDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private fun shouldRefresh(maxAgeMs: Long = INVENTORY_REFRESH_INTERVAL_MS): Boolean {
        return lastSuccessfulRefreshAt == 0L ||
            System.currentTimeMillis() - lastSuccessfulRefreshAt >= maxAgeMs
    }

    companion object {
        const val ALL_CATEGORY = "All"
        const val DELETED_CATEGORY = "Deleted"
        const val INGREDIENT_ID_PREFIX = "ING-"
        const val INVENTORY_PAGE_SIZE = 8
        const val INVENTORY_REFRESH_INTERVAL_MS = 60_000L
        const val RESTOCK_QUANTITY_ERROR = "Restock quantity must be greater than zero."
        const val BEVERAGE_BASE_SIZE_OZ = 16.0
        // Default ingredient categories shown in the Add Ingredient
        // dialog. Mirrors the finer split established by
        // database/split_ingredient_categories.sql so every category
        // here matches a single product domain (drinks vs food, sweet
        // vs savoury, etc.) and has a deterministic default unit via
        // IngredientUnits.defaultUnitForCategory.
        val DEFAULT_INGREDIENT_CATEGORIES = listOf(
            // Drinks
            "Beverages",
            "Coffee Bases",
            "Dairy",
            "Drink Pantry",
            "Drink Produce",
            "Lemonade Bases",
            "Powders & Mixes",
            "Sweet Syrups",
            "Tea Bases",
            // Food
            "Bakery & Bread",
            "Burger Proteins",
            "Food Dairy",
            "Food Pantry",
            "Food Produce",
            "Frozen & Sides",
            "Rice Meal Proteins",
            "Savoury Sauces",
            "Side Proteins",
            "Wing Proteins"
        )
    }

    private data class InventorySnapshot(
        val ingredients: List<Ingredient>,
        val producibleProducts: List<ProducibleProduct>,
        val productCategories: List<ProductCategoryOption>,
        val productRecipes: Map<String, List<ProductRecipeIngredient>>,
        val variantOrderCounts: Map<String, Int> = emptyMap()
    )
}
