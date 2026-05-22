package com.example.zejioscafese.ui

import com.example.zejioscafese.helpers.InstantExecutorExtension
import com.example.zejioscafese.inventory.data.model.ProductCategoryOption
import com.example.zejioscafese.inventory.data.model.ProducibleProduct
import com.example.zejioscafese.inventory.data.repository.InventoryRepository
import com.example.zejioscafese.pos.data.model.Ingredient
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(InstantExecutorExtension::class)
class InventoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var inventoryRepo: InventoryRepository
    private lateinit var viewModel: InventoryViewModel

    private val milk = Ingredient(
        id = "ING-001", name = "Milk", category = "Dairy", unit = "mL",
        currentStock = 5000.0, minimumStock = 1000.0, costPerUnit = 0.05,
        mlPerServing = 250.0, lastRestocked = "2026-05-01"
    )
    private val sugar = Ingredient(
        id = "ING-002", name = "Sugar", category = "Pantry", unit = "mL",
        currentStock = 2000.0, minimumStock = 500.0, costPerUnit = 0.02,
        mlPerServing = null, lastRestocked = "2026-05-01"
    )
    private val coffee = Ingredient(
        id = "ING-003", name = "Coffee Beans", category = "Pantry", unit = "mL",
        currentStock = 300.0, minimumStock = 500.0, costPerUnit = 0.30,
        mlPerServing = null, lastRestocked = "2026-05-01"
    ) // low stock: 300 <= 500

    private val latteProduct = ProducibleProduct(
        id = "VAR-001", productId = "PRD-001", categoryId = "CAT-001",
        category = "Coffee", productName = "Latte", variantName = "Standard",
        price = 110.0, availableQuantity = 5
    )
    private val icedLatteProduct = ProducibleProduct(
        id = "VAR-002", productId = "PRD-001", categoryId = "CAT-001",
        category = "Coffee", productName = "Latte", variantName = "Iced",
        price = 120.0, availableQuantity = 0  // out-of-stock
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        inventoryRepo = mockk()
        stubRepoDefaults()
        viewModel = InventoryViewModel(inventoryRepo)
    }

    private fun stubRepoDefaults() {
        coEvery { inventoryRepo.fetchIngredients() } returns listOf(milk, sugar, coffee)
        coEvery { inventoryRepo.fetchProducibleProducts() } returns listOf(latteProduct, icedLatteProduct)
        coEvery { inventoryRepo.fetchProductCategories() } returns listOf(
            ProductCategoryOption("CAT-001", "Coffee")
        )
        coEvery { inventoryRepo.fetchProductRecipeLinks() } returns emptyList()
        coEvery { inventoryRepo.fetchOrderVariantCounts() } returns emptyMap()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    // ── initial load ──────────────────────────────────────────────────────────

    @Test
    fun init_loadsIngredients_ingredientListAvailableAfterIdle() = runTest {
        advanceUntilIdle()
        assertEquals(3, viewModel.ingredientList.value?.size)
    }

    @Test
    fun init_loadsProducibleProducts_productListAvailableAfterIdle() = runTest {
        advanceUntilIdle()
        // producibleProductList is only published when in PRODUCTION screen mode
        viewModel.setScreenMode(InventoryViewModel.ScreenMode.PRODUCTION)
        assertEquals(2, viewModel.producibleProductList.value?.size)
    }

    @Test
    fun init_derivesLowStockIngredients_coffeeBeansIsLowStock() = runTest {
        advanceUntilIdle()
        val lowStock = viewModel.lowStockIngredients.value.orEmpty()
        assertTrue(lowStock.any { it.id == "ING-003" })
    }

    @Test
    fun init_derivesOutOfStockProducts_icedLatteIsOutOfStock() = runTest {
        advanceUntilIdle()
        val outOfStock = viewModel.outOfStockProducts.value.orEmpty()
        assertEquals(1, outOfStock.size)
        assertEquals("VAR-002", outOfStock.first().id)
    }

    @Test
    fun init_calculatesTotalInventoryValue_equalsSumOfStockTimesCost() = runTest {
        advanceUntilIdle()
        // milk: 5000 * 0.05 = 250, sugar: 2000 * 0.02 = 40, coffee: 300 * 0.30 = 90 → 380
        assertEquals(380.0, viewModel.totalInventoryValue.value)
    }

    @Test
    fun init_totalIngredientCount_matchesRemoteData() = runTest {
        advanceUntilIdle()
        assertEquals(3, viewModel.totalIngredientCount.value)
    }

    @Test
    fun init_totalProducibleProductCount_matchesRemoteData() = runTest {
        advanceUntilIdle()
        assertEquals(2, viewModel.totalProducibleProductCount.value)
    }

    @Test
    fun init_noInventoryError_afterSuccessfulLoad() = runTest {
        advanceUntilIdle()
        assertNull(viewModel.inventoryError.value)
    }

    // ── setSearchQuery ────────────────────────────────────────────────────────

    @Test
    fun setSearchQuery_partialMatch_filtersIngredients() = runTest {
        advanceUntilIdle()
        viewModel.setSearchQuery("milk")
        val items = viewModel.ingredientList.value.orEmpty()
        assertEquals(1, items.size)
        assertEquals("Milk", items.first().name)
    }

    @Test
    fun setSearchQuery_noMatch_returnsEmptyList() = runTest {
        advanceUntilIdle()
        viewModel.setSearchQuery("xyznonexistent")
        assertTrue(viewModel.ingredientList.value.orEmpty().isEmpty())
    }

    @Test
    fun setSearchQuery_empty_resetsToAllIngredients() = runTest {
        advanceUntilIdle()
        viewModel.setSearchQuery("milk")
        viewModel.setSearchQuery("")
        assertEquals(3, viewModel.ingredientList.value?.size)
    }

    @Test
    fun setSearchQuery_caseInsensitive_matchesRegardlessOfCase() = runTest {
        advanceUntilIdle()
        viewModel.setSearchQuery("MILK")
        assertEquals(1, viewModel.ingredientList.value?.size)
    }

    // ── setCategory ───────────────────────────────────────────────────────────

    @Test
    fun setCategory_dairy_showsOnlyDairyIngredients() = runTest {
        advanceUntilIdle()
        viewModel.setCategory("Dairy")
        val items = viewModel.ingredientList.value.orEmpty()
        assertTrue(items.all { it.category == "Dairy" })
        assertEquals(1, items.size)
    }

    @Test
    fun setCategory_all_showsAllIngredients() = runTest {
        advanceUntilIdle()
        viewModel.setCategory("Dairy")
        viewModel.setCategory("All")
        assertEquals(3, viewModel.ingredientList.value?.size)
    }

    @Test
    fun setCategory_unknownCategory_showsEmptyList() = runTest {
        advanceUntilIdle()
        viewModel.setCategory("NonExistentCategory")
        assertTrue(viewModel.ingredientList.value.orEmpty().isEmpty())
    }

    // ── setSortMode ───────────────────────────────────────────────────────────

    @Test
    fun setSortMode_name_ingredientsInAlphabeticalOrder() = runTest {
        advanceUntilIdle()
        viewModel.setSortMode(InventoryViewModel.SortMode.NAME)
        val names = viewModel.ingredientList.value.orEmpty().map { it.name.lowercase() }
        assertEquals(names.sorted(), names)
    }

    @Test
    fun setSortMode_value_highestValueFirst() = runTest {
        advanceUntilIdle()
        viewModel.setSortMode(InventoryViewModel.SortMode.VALUE)
        val values = viewModel.ingredientList.value.orEmpty().map { it.currentStock * it.costPerUnit }
        assertEquals(values.sortedDescending(), values)
    }

    // ── setScreenMode ─────────────────────────────────────────────────────────

    @Test
    fun setScreenMode_production_switchesScreenMode() = runTest {
        advanceUntilIdle()
        viewModel.setScreenMode(InventoryViewModel.ScreenMode.PRODUCTION)
        assertEquals(InventoryViewModel.ScreenMode.PRODUCTION, viewModel.screenMode.value)
    }

    @Test
    fun setScreenMode_production_paginationReflectsProductionData() = runTest {
        advanceUntilIdle()
        viewModel.setScreenMode(InventoryViewModel.ScreenMode.PRODUCTION)
        assertEquals(2, viewModel.producibleProductList.value?.size)
    }

    @Test
    fun setScreenMode_sameMode_doesNotChangeScreenMode() = runTest {
        advanceUntilIdle()
        viewModel.setScreenMode(InventoryViewModel.ScreenMode.INGREDIENTS)
        assertEquals(InventoryViewModel.ScreenMode.INGREDIENTS, viewModel.screenMode.value)
    }

    @Test
    fun setScreenMode_production_resetsSelectedCategoryToAll() = runTest {
        advanceUntilIdle()
        viewModel.setCategory("Dairy")
        viewModel.setScreenMode(InventoryViewModel.ScreenMode.PRODUCTION)
        // After switching modes the category filter resets; switch back and verify
        viewModel.setScreenMode(InventoryViewModel.ScreenMode.INGREDIENTS)
        assertEquals(3, viewModel.ingredientList.value?.size)
    }

    // ── pagination ────────────────────────────────────────────────────────────

    @Test
    fun paginationState_initial_canGoPreviousIsFalse() = runTest {
        advanceUntilIdle()
        assertFalse(viewModel.paginationState.value!!.canGoPrevious)
    }

    @Test
    fun paginationState_fewItems_canGoNextIsFalse() = runTest {
        advanceUntilIdle()
        // 3 items < 8 per page
        assertFalse(viewModel.paginationState.value!!.canGoNext)
    }

    @Test
    fun paginationState_initial_currentPageIsOne() = runTest {
        advanceUntilIdle()
        assertEquals(1, viewModel.paginationState.value!!.currentPage)
    }

    // ── generateId ────────────────────────────────────────────────────────────

    @Test
    fun generateId_withExistingIngredients_returnsNextSequentialId() = runTest {
        advanceUntilIdle()
        // highest existing id is ING-003 → next is ING-004
        assertEquals("ING-004", viewModel.generateId())
    }

    // ── getCategories ─────────────────────────────────────────────────────────

    @Test
    fun getCategories_ingredientMode_containsAllAndDistinctCategories() = runTest {
        advanceUntilIdle()
        val cats = viewModel.getCategories()
        assertTrue("All" in cats)
        assertTrue("Dairy" in cats)
        assertTrue("Pantry" in cats)
        assertEquals(cats.distinct(), cats)  // no duplicates
    }

    @Test
    fun getCategories_ingredientMode_doesNotContainBlankCategory() = runTest {
        advanceUntilIdle()
        val cats = viewModel.getCategories()
        assertTrue(cats.none { it.isBlank() })
    }

    // ── getIngredientCategoriesForEditor ──────────────────────────────────────

    @Test
    fun getIngredientCategoriesForEditor_containsDefaultAndDataCategories() = runTest {
        advanceUntilIdle()
        val cats = viewModel.getIngredientCategoriesForEditor()
        assertTrue("Dairy" in cats)
        assertTrue("Pantry" in cats)
        assertTrue("Beverages" in cats)  // from DEFAULT_INGREDIENT_CATEGORIES
    }

    // ── restockIngredient ─────────────────────────────────────────────────────

    @Test
    fun restockIngredient_negativeQuantity_setsError() = runTest {
        advanceUntilIdle()
        viewModel.restockIngredient("ING-001", -10.0)
        assertNotNull(viewModel.inventoryError.value)
    }

    @Test
    fun restockIngredient_zeroQuantity_setsError() = runTest {
        advanceUntilIdle()
        viewModel.restockIngredient("ING-001", 0.0)
        assertNotNull(viewModel.inventoryError.value)
    }

    @Test
    fun restockIngredient_unknownId_doesNotSetError() = runTest {
        advanceUntilIdle()
        viewModel.restockIngredient("ING-999", 100.0)
        assertNull(viewModel.inventoryError.value)
    }

    @Test
    fun restockIngredient_validQuantity_callsRepository() = runTest {
        advanceUntilIdle()
        coEvery { inventoryRepo.restockIngredient(any(), any()) } returns Unit
        viewModel.restockIngredient("ING-001", 1000.0)
        advanceUntilIdle()
        coVerify { inventoryRepo.restockIngredient("ING-001", any()) }
    }

    // ── updateIngredient ──────────────────────────────────────────────────────

    @Test
    fun updateIngredient_unknownId_leavesListUnchanged() = runTest {
        advanceUntilIdle()
        val unknown = milk.copy(id = "ING-999", name = "Ghost")
        viewModel.updateIngredient(unknown)
        assertEquals(3, viewModel.ingredientList.value?.size)
        assertFalse(viewModel.ingredientList.value.orEmpty().any { it.id == "ING-999" })
    }

    @Test
    fun updateIngredient_validId_callsRepository() = runTest {
        advanceUntilIdle()
        coEvery { inventoryRepo.updateIngredient(any()) } returns Unit
        val updated = milk.copy(name = "Full Cream Milk")
        viewModel.updateIngredient(updated)
        advanceUntilIdle()
        coVerify { inventoryRepo.updateIngredient(any()) }
    }

    // ── softDeleteProduct ─────────────────────────────────────────────────────

    @Test
    fun softDeleteProduct_removesProductOptimistically() = runTest {
        advanceUntilIdle()
        viewModel.setScreenMode(InventoryViewModel.ScreenMode.PRODUCTION)
        coEvery { inventoryRepo.softDeleteProduct(any()) } returns Unit
        viewModel.softDeleteProduct(latteProduct)
        assertEquals(1, viewModel.producibleProductList.value?.size)
    }

    // ── onInventoryErrorConsumed ──────────────────────────────────────────────

    @Test
    fun onInventoryErrorConsumed_clearsError() = runTest {
        advanceUntilIdle()
        viewModel.restockIngredient("ING-001", -1.0)
        assertNotNull(viewModel.inventoryError.value)
        viewModel.onInventoryErrorConsumed()
        assertNull(viewModel.inventoryError.value)
    }

    // ── error propagation ─────────────────────────────────────────────────────

    @Test
    fun init_repositoryThrows_setsInventoryError() = runTest {
        val failingRepo = mockk<InventoryRepository>()
        coEvery { failingRepo.fetchIngredients() } throws RuntimeException("Network error")
        coEvery { failingRepo.fetchProducibleProducts() } returns emptyList()
        coEvery { failingRepo.fetchProductCategories() } returns emptyList()
        coEvery { failingRepo.fetchProductRecipeLinks() } returns emptyList()
        val failingViewModel = InventoryViewModel(failingRepo)
        advanceUntilIdle()
        assertNotNull(failingViewModel.inventoryError.value)
    }
}
