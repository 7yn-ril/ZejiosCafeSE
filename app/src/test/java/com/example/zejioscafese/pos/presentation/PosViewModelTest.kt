package com.example.zejioscafese.pos.presentation

import com.example.zejioscafese.helpers.InstantExecutorExtension
import com.example.zejioscafese.orders.data.repository.CheckoutOrderPayload
import com.example.zejioscafese.orders.data.repository.OrderRepository
import com.example.zejioscafese.orders.model.CafeOrder
import com.example.zejioscafese.orders.model.CafeOrderStatus
import com.example.zejioscafese.pos.data.model.Discount
import com.example.zejioscafese.pos.data.model.Product
import com.example.zejioscafese.pos.data.model.ProductRecipeRequirement
import com.example.zejioscafese.pos.data.repository.CategoryRepository
import com.example.zejioscafese.pos.data.repository.DiscountRepository
import com.example.zejioscafese.pos.data.repository.ProductRepository
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
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
class PosViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var productRepo: ProductRepository
    private lateinit var categoryRepo: CategoryRepository
    private lateinit var orderRepo: OrderRepository
    private lateinit var discountRepo: DiscountRepository
    private lateinit var viewModel: PosViewModel

    private val pwdDiscount = Discount(
        id = Discount.PWD_ID, name = "PWD", percent = 20.0, isBuiltIn = true
    )
    private val seniorDiscount = Discount(
        id = Discount.SENIOR_ID, name = "Senior Citizen", percent = 20.0, isBuiltIn = true
    )
    private val anniversaryDiscount = Discount(
        id = "DSC-001", name = "Anniversary Week", percent = 15.0, isBuiltIn = false
    )

    private val coffeeA = Product(
        id = "VAR-001", name = "Americano", category = "Coffee",
        price = 90.0, stockLeft = 10,
        sourceProductId = "PRD-001", sourceProductName = "Americano", sourceVariantName = "Standard"
    )
    private val coffeeB = Product(
        id = "VAR-002", name = "Latte (Iced)", category = "Coffee",
        price = 110.0, stockLeft = 5,
        sourceProductId = "PRD-002", sourceProductName = "Latte", sourceVariantName = "Iced"
    )
    private val food = Product(
        id = "VAR-003", name = "Burger", category = "Food",
        price = 150.0, stockLeft = 3,
        sourceProductId = "PRD-003", sourceProductName = "Burger", sourceVariantName = "Standard"
    )
    private val allProducts = listOf(coffeeA, coffeeB, food)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        productRepo = mockk()
        categoryRepo = mockk()
        orderRepo = mockk()
        discountRepo = mockk()
        coEvery { productRepo.fetchProducts() } returns allProducts
        coEvery { categoryRepo.fetchCategories() } returns listOf("All", "Coffee", "Food")
        coEvery { orderRepo.fetchNextOrderNumber() } returns "#POS-1025"
        coEvery { discountRepo.fetchActiveDiscounts(any()) } returns listOf(
            pwdDiscount, seniorDiscount, anniversaryDiscount
        )
        viewModel = PosViewModel(productRepo, categoryRepo, orderRepo, discountRepo)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    // ── initial load ──────────────────────────────────────────────────────────

    @Test
    fun init_loadsProducts_productsAvailableAfterIdle() = runTest {
        advanceUntilIdle()
        assertEquals(3, viewModel.products.value?.size)
    }

    @Test
    fun init_loadsCategories_containsAllAndFetched() = runTest {
        advanceUntilIdle()
        val cats = viewModel.categories.value.orEmpty()
        assertTrue("All" in cats)
        assertTrue("Coffee" in cats)
        assertTrue("Food" in cats)
    }

    @Test
    fun init_suggestsOrderNumber_fromRepository() = runTest {
        advanceUntilIdle()
        assertEquals("#POS-1025", viewModel.orderNumber.value)
    }

    // ── selectCategory ────────────────────────────────────────────────────────

    @Test
    fun selectCategory_coffee_showsOnlyCoffeeProducts() = runTest {
        advanceUntilIdle()
        viewModel.selectCategory("Coffee")
        val products = viewModel.products.value.orEmpty()
        assertTrue(products.all { it.category == "Coffee" })
        assertEquals(2, products.size)
    }

    @Test
    fun selectCategory_all_showsAllProducts() = runTest {
        advanceUntilIdle()
        viewModel.selectCategory("Coffee")
        viewModel.selectCategory("All")
        assertEquals(3, viewModel.products.value?.size)
    }

    @Test
    fun selectCategory_unknown_showsEmptyList() = runTest {
        advanceUntilIdle()
        viewModel.selectCategory("NonExistentCategory")
        assertTrue(viewModel.products.value.orEmpty().isEmpty())
    }

    // ── search ────────────────────────────────────────────────────────────────

    @Test
    fun updateSearchQuery_partialMatch_filtersCorrectly() = runTest {
        advanceUntilIdle()
        viewModel.updateSearchQuery("latte")
        val names = viewModel.products.value.orEmpty().map { it.name }
        assertTrue(names.all { it.contains("Latte", ignoreCase = true) })
    }

    @Test
    fun updateSearchQuery_noMatch_returnsEmptyList() = runTest {
        advanceUntilIdle()
        viewModel.updateSearchQuery("xyznonexistent")
        assertTrue(viewModel.products.value.orEmpty().isEmpty())
    }

    @Test
    fun updateSearchQuery_empty_resetsToAllProducts() = runTest {
        advanceUntilIdle()
        viewModel.updateSearchQuery("latte")
        viewModel.updateSearchQuery("")
        assertEquals(3, viewModel.products.value?.size)
    }

    @Test
    fun updateSearchQuery_matchesCategory_returnsProductsInCategory() = runTest {
        advanceUntilIdle()
        viewModel.updateSearchQuery("coffee")
        val products = viewModel.products.value.orEmpty()
        assertTrue(products.all { it.category == "Coffee" })
    }

    // ── sort ──────────────────────────────────────────────────────────────────

    @Test
    fun setSortOption_nameAsc_productsInAlphabeticalOrder() = runTest {
        advanceUntilIdle()
        viewModel.setSortOption(PosViewModel.SortOption.NAME_ASC)
        val names = viewModel.products.value.orEmpty().map { it.name.lowercase() }
        assertEquals(names.sorted(), names)
    }

    @Test
    fun setSortOption_nameDesc_productsInReverseAlphabeticalOrder() = runTest {
        advanceUntilIdle()
        viewModel.setSortOption(PosViewModel.SortOption.NAME_DESC)
        val names = viewModel.products.value.orEmpty().map { it.name.lowercase() }
        assertEquals(names.sortedDescending(), names)
    }

    @Test
    fun setSortOption_priceAsc_productsCheapestFirst() = runTest {
        advanceUntilIdle()
        viewModel.setSortOption(PosViewModel.SortOption.PRICE_ASC)
        val prices = viewModel.products.value.orEmpty().map { it.price }
        assertEquals(prices.sorted(), prices)
    }

    @Test
    fun setSortOption_priceDesc_productsMostExpensiveFirst() = runTest {
        advanceUntilIdle()
        viewModel.setSortOption(PosViewModel.SortOption.PRICE_DESC)
        val prices = viewModel.products.value.orEmpty().map { it.price }
        assertEquals(prices.sortedDescending(), prices)
    }

    // ── increaseProduct / decreaseProduct ─────────────────────────────────────

    @Test
    fun increaseProduct_once_addsOneItemToOrder() = runTest {
        advanceUntilIdle()
        viewModel.increaseProduct(coffeeA)
        val items = viewModel.orderItems.value.orEmpty()
        assertEquals(1, items.size)
        assertEquals(1, items.first().quantity)
    }

    @Test
    fun increaseProduct_twice_quantityIsTwo() = runTest {
        advanceUntilIdle()
        viewModel.increaseProduct(coffeeA)
        viewModel.increaseProduct(coffeeA)
        assertEquals(2, viewModel.orderItems.value?.first()?.quantity)
    }

    @Test
    fun decreaseProduct_toZero_removesFromOrder() = runTest {
        advanceUntilIdle()
        viewModel.increaseProduct(coffeeA)
        viewModel.decreaseProduct(coffeeA)
        assertTrue(viewModel.orderItems.value.orEmpty().isEmpty())
    }

    @Test
    fun increaseProduct_outOfStockProduct_doesNotAddToOrder() = runTest {
        advanceUntilIdle()
        val outOfStock = coffeeA.copy(stockLeft = 0)
        viewModel.increaseProduct(outOfStock)
        assertTrue(viewModel.orderItems.value.orEmpty().isEmpty())
    }

    @Test
    fun increaseProduct_exceedsStock_capsAtStockLeft() = runTest {
        advanceUntilIdle()
        repeat(20) { viewModel.increaseProduct(food) } // stockLeft = 3
        assertEquals(3, viewModel.orderItems.value?.firstOrNull()?.quantity)
    }

    @Test
    fun increaseOrderItem_addsOneToExistingItem() = runTest {
        advanceUntilIdle()
        viewModel.increaseProduct(coffeeA)
        val item = viewModel.orderItems.value!!.first()
        viewModel.increaseOrderItem(item)
        assertEquals(2, viewModel.orderItems.value?.first()?.quantity)
    }

    @Test
    fun decreaseOrderItem_reducesQuantityByOne() = runTest {
        advanceUntilIdle()
        viewModel.increaseProduct(coffeeA)
        viewModel.increaseProduct(coffeeA)
        val item = viewModel.orderItems.value!!.first()
        viewModel.decreaseOrderItem(item)
        assertEquals(1, viewModel.orderItems.value?.first()?.quantity)
    }

    // ── removeProduct ─────────────────────────────────────────────────────────

    @Test
    fun removeProduct_removesOnlyTargetedProduct() = runTest {
        advanceUntilIdle()
        viewModel.increaseProduct(coffeeA)
        viewModel.increaseProduct(coffeeB)
        viewModel.removeProduct(coffeeA.id)
        val items = viewModel.orderItems.value.orEmpty()
        assertEquals(1, items.size)
        assertEquals(coffeeB.id, items.first().product.id)
    }

    @Test
    fun removeProduct_nonExistentId_orderUnchanged() = runTest {
        advanceUntilIdle()
        viewModel.increaseProduct(coffeeA)
        viewModel.removeProduct("VAR-NONEXISTENT")
        assertEquals(1, viewModel.orderItems.value?.size)
    }

    // ── clearOrder ────────────────────────────────────────────────────────────

    @Test
    fun clearOrder_removesAllItems() = runTest {
        advanceUntilIdle()
        allProducts.forEach { viewModel.increaseProduct(it) }
        viewModel.clearOrder()
        assertTrue(viewModel.orderItems.value.orEmpty().isEmpty())
    }

    @Test
    fun clearOrder_resetsSubtotalToZero() = runTest {
        advanceUntilIdle()
        viewModel.increaseProduct(coffeeA)
        viewModel.clearOrder()
        assertEquals(0.0, viewModel.subtotal.value)
        assertEquals(0.0, viewModel.total.value)
    }

    // ── subtotal calculation ──────────────────────────────────────────────────

    @Test
    fun subtotal_afterAddingTwoItems_equalsSumOfLineTotals() = runTest {
        advanceUntilIdle()
        viewModel.increaseProduct(coffeeA) // 90.0
        viewModel.increaseProduct(coffeeB) // 110.0
        assertEquals(200.0, viewModel.subtotal.value)
    }

    @Test
    fun subtotal_afterAddingTwoOfSameProduct_equalsDoublePrice() = runTest {
        advanceUntilIdle()
        viewModel.increaseProduct(coffeeA) // 90.0
        viewModel.increaseProduct(coffeeA) // 90.0
        assertEquals(180.0, viewModel.subtotal.value)
    }

    // ── payment method / order type ───────────────────────────────────────────

    @Test
    fun setPaymentMethod_updatesSelectedPaymentMethod() = runTest {
        advanceUntilIdle()
        viewModel.setPaymentMethod(PosViewModel.PaymentMethod.GCASH)
        assertEquals(PosViewModel.PaymentMethod.GCASH, viewModel.selectedPaymentMethod.value)
    }

    @Test
    fun setOrderType_updatesSelectedOrderType() = runTest {
        advanceUntilIdle()
        viewModel.setOrderType(PosViewModel.OrderType.TAKE_AWAY)
        assertEquals(PosViewModel.OrderType.TAKE_AWAY, viewModel.selectedOrderType.value)
    }

    // ── checkout ──────────────────────────────────────────────────────────────

    @Test
    fun checkout_emptyCart_doesNotCallRepository() = runTest {
        advanceUntilIdle()
        viewModel.checkout()
        advanceUntilIdle()
        coVerify(exactly = 0) { orderRepo.saveCheckoutOrder(any(), any()) }
    }

    @Test
    fun checkout_withItems_emitsCheckoutEvent() = runTest {
        advanceUntilIdle()
        val savedOrder = CafeOrder(
            id = "#POS-1025", customerName = "Walk-in Customer",
            itemsSummary = "Americano", itemCount = 1,
            timeLabel = "10:00 AM", status = CafeOrderStatus.PREPARING,
            total = 90.0, initials = "WC"
        )
        coEvery { orderRepo.saveCheckoutOrder(any(), any()) } returns savedOrder

        viewModel.increaseProduct(coffeeA)
        viewModel.checkout()
        advanceUntilIdle()

        assertNotNull(viewModel.checkoutEvent.value)
        assertEquals("#POS-1025", viewModel.checkoutEvent.value?.id)
    }

    @Test
    fun checkout_success_clearsOrderItems() = runTest {
        advanceUntilIdle()
        val savedOrder = CafeOrder(
            id = "#POS-1025", customerName = "Walk-in Customer",
            itemsSummary = "Americano", itemCount = 1,
            timeLabel = "10:00 AM", status = CafeOrderStatus.PREPARING,
            total = 90.0, initials = "WC"
        )
        coEvery { orderRepo.saveCheckoutOrder(any(), any()) } returns savedOrder

        viewModel.increaseProduct(coffeeA)
        viewModel.checkout()
        advanceUntilIdle()

        assertTrue(viewModel.orderItems.value.orEmpty().isEmpty())
    }

    @Test
    fun checkout_repositoryThrows_setsCheckoutError() = runTest {
        advanceUntilIdle()
        coEvery { orderRepo.saveCheckoutOrder(any(), any()) } throws RuntimeException("Network timeout")

        viewModel.increaseProduct(coffeeA)
        viewModel.checkout()
        advanceUntilIdle()

        assertNotNull(viewModel.checkoutError.value)
    }

    @Test
    fun checkout_repositoryThrows_doesNotClearCart() = runTest {
        advanceUntilIdle()
        coEvery { orderRepo.saveCheckoutOrder(any(), any()) } throws RuntimeException("Network timeout")

        viewModel.increaseProduct(coffeeA)
        viewModel.checkout()
        advanceUntilIdle()

        assertEquals(1, viewModel.orderItems.value?.size)
    }

    @Test
    fun increaseProduct_sharedRecipeIngredient_blocksSecondProduct() = runTest {
        val beefPatty = ProductRecipeRequirement(
            ingredientId = "ING-042",
            ingredientName = "Beef Patty",
            ingredientUnit = "pcs",
            requiredQuantity = 1.0,
            currentStock = 1.0
        )
        val alohaBurger = Product(
            id = "VAR-071",
            name = "Aloha Burger",
            category = "Burgers",
            price = 160.0,
            stockLeft = 1,
            sourceProductId = "PRD-071",
            sourceProductName = "Aloha Burger",
            sourceVariantName = "Standard",
            recipeIngredients = listOf(beefPatty)
        )
        val sekaiBurger = Product(
            id = "VAR-072",
            name = "A - Sekai Burger",
            category = "Burgers",
            price = 100.0,
            stockLeft = 1,
            sourceProductId = "PRD-072",
            sourceProductName = "A - Sekai Burger",
            sourceVariantName = "Standard",
            recipeIngredients = listOf(beefPatty)
        )
        coEvery { productRepo.fetchProducts() } returns listOf(alohaBurger, sekaiBurger)
        coEvery { categoryRepo.fetchCategories() } returns listOf("All", "Burgers")
        viewModel = PosViewModel(productRepo, categoryRepo, orderRepo, discountRepo)
        advanceUntilIdle()

        viewModel.increaseProduct(alohaBurger)
        viewModel.increaseProduct(sekaiBurger)

        val items = viewModel.orderItems.value.orEmpty()
        assertEquals(1, items.size)
        assertEquals("Aloha Burger", items.single().product.name)
        assertEquals(1, items.single().quantity)
        assertTrue(viewModel.stockLimitNotice.value?.message.orEmpty().contains("Beef Patty"))
    }

    @Test
    fun increaseProduct_sameRecipeItemPastStock_emitsLimitNotice() = runTest {
        val beefPatty = ProductRecipeRequirement(
            ingredientId = "ING-042",
            ingredientName = "Beef Patty",
            ingredientUnit = "pcs",
            requiredQuantity = 1.0,
            currentStock = 1.0
        )
        val alohaBurger = Product(
            id = "VAR-071",
            name = "Aloha Burger",
            category = "Burgers",
            price = 160.0,
            stockLeft = 1,
            sourceProductId = "PRD-071",
            sourceProductName = "Aloha Burger",
            sourceVariantName = "Standard",
            recipeIngredients = listOf(beefPatty)
        )
        coEvery { productRepo.fetchProducts() } returns listOf(alohaBurger)
        coEvery { categoryRepo.fetchCategories() } returns listOf("All", "Burgers")
        viewModel = PosViewModel(productRepo, categoryRepo, orderRepo, discountRepo)
        advanceUntilIdle()

        viewModel.increaseProduct(alohaBurger)
        viewModel.increaseProduct(alohaBurger)

        val item = viewModel.orderItems.value.orEmpty().single()
        assertEquals(1, item.quantity)
        assertEquals(
            "Only 1 Beef Patty left. It is already reserved by the current order.",
            viewModel.stockLimitNotice.value?.message
        )
    }

    // ── event consumption ─────────────────────────────────────────────────────

    @Test
    fun onCheckoutEventConsumed_clearsCheckoutEvent() = runTest {
        advanceUntilIdle()
        val savedOrder = CafeOrder(
            id = "#POS-1025", customerName = "Walk-in Customer",
            itemsSummary = "Americano", itemCount = 1,
            timeLabel = "10:00 AM", status = CafeOrderStatus.PREPARING,
            total = 90.0, initials = "WC"
        )
        coEvery { orderRepo.saveCheckoutOrder(any(), any()) } returns savedOrder
        viewModel.increaseProduct(coffeeA)
        viewModel.checkout()
        advanceUntilIdle()

        viewModel.onCheckoutEventConsumed()
        assertNull(viewModel.checkoutEvent.value)
    }

    @Test
    fun onCheckoutErrorConsumed_clearsCheckoutError() = runTest {
        advanceUntilIdle()
        coEvery { orderRepo.saveCheckoutOrder(any(), any()) } throws RuntimeException("error")
        viewModel.increaseProduct(coffeeA)
        viewModel.checkout()
        advanceUntilIdle()

        viewModel.onCheckoutErrorConsumed()
        assertNull(viewModel.checkoutError.value)
    }

    // ── pagination state ──────────────────────────────────────────────────────

    @Test
    fun paginationState_canGoPrevious_falseOnFirstPage() = runTest {
        advanceUntilIdle()
        val state = viewModel.productPaginationState.value!!
        assertFalse(state.canGoPrevious)
    }

    @Test
    fun paginationState_canGoNext_falseWhenAllFitOnOnePage() = runTest {
        advanceUntilIdle()
        val state = viewModel.productPaginationState.value!!
        assertFalse(state.canGoNext) // 3 products < 12 per page
    }

    // ── discounts ─────────────────────────────────────────────────────────────

    @Test
    fun init_loadsAvailableDiscounts_listExposedToUi() = runTest {
        advanceUntilIdle()
        assertEquals(3, viewModel.availableDiscounts.value?.size)
    }

    @Test
    fun setDiscount_pwd_reducesTotalBy20Percent() = runTest {
        advanceUntilIdle()
        viewModel.increaseProduct(coffeeA) // 90.0
        viewModel.setDiscount(pwdDiscount)
        // 90 - (90 * 0.20) = 72
        assertEquals(72.0, viewModel.total.value)
        assertEquals(18.0, viewModel.discountAmount.value)
    }

    @Test
    fun setDiscount_senior_reducesTotalBy20Percent() = runTest {
        advanceUntilIdle()
        viewModel.increaseProduct(coffeeA) // 90.0
        viewModel.increaseProduct(coffeeB) // 110.0
        viewModel.setDiscount(seniorDiscount)
        // (90 + 110) * 0.80 = 160
        assertEquals(160.0, viewModel.total.value)
    }

    @Test
    fun setDiscount_custom15Percent_appliesCorrectAmount() = runTest {
        advanceUntilIdle()
        viewModel.increaseProduct(coffeeB) // 110.0
        viewModel.setDiscount(anniversaryDiscount)
        // 110 * 0.85 = 93.5
        assertEquals(93.5, viewModel.total.value)
        assertEquals(16.5, viewModel.discountAmount.value)
    }

    @Test
    fun setDiscount_null_clearsDiscountAndRestoresTotal() = runTest {
        advanceUntilIdle()
        viewModel.increaseProduct(coffeeA) // 90.0
        viewModel.setDiscount(pwdDiscount)
        viewModel.setDiscount(null)
        assertEquals(90.0, viewModel.total.value)
        assertEquals(0.0, viewModel.discountAmount.value)
        assertNull(viewModel.selectedDiscount.value)
    }

    @Test
    fun setDiscount_changingItemsRecomputesAgainstNewSubtotal() = runTest {
        advanceUntilIdle()
        viewModel.increaseProduct(coffeeA) // 90.0
        viewModel.setDiscount(pwdDiscount) // 90 * 0.20 = 18
        viewModel.increaseProduct(coffeeB) // subtotal becomes 200
        // syncOrderState calls recomputeTotals → 200 * 0.20 = 40 discount
        assertEquals(40.0, viewModel.discountAmount.value)
        assertEquals(160.0, viewModel.total.value)
    }

    @Test
    fun checkout_withDiscount_forwardsDiscountIdAndPercentToRepository() = runTest {
        advanceUntilIdle()
        val savedOrder = CafeOrder(
            id = "#POS-1025", customerName = "Walk-in Customer",
            itemsSummary = "Americano", itemCount = 1,
            timeLabel = "10:00 AM", status = CafeOrderStatus.PREPARING,
            total = 72.0, initials = "WC"
        )
        val payloadSlot = slot<CheckoutOrderPayload>()
        coEvery { orderRepo.saveCheckoutOrder(capture(payloadSlot), any()) } returns savedOrder

        viewModel.increaseProduct(coffeeA) // 90.0
        viewModel.setDiscount(pwdDiscount)
        viewModel.checkout()
        advanceUntilIdle()

        val payload = payloadSlot.captured
        assertEquals(Discount.PWD_ID, payload.discountId)
        assertEquals(20.0, payload.discountPercent)
        assertEquals(18.0, payload.discountAmount)
        assertEquals(72.0, payload.total)
    }

    @Test
    fun checkout_withoutDiscount_payloadHasNullDiscountFields() = runTest {
        advanceUntilIdle()
        val savedOrder = CafeOrder(
            id = "#POS-1025", customerName = "Walk-in Customer",
            itemsSummary = "Americano", itemCount = 1,
            timeLabel = "10:00 AM", status = CafeOrderStatus.PREPARING,
            total = 90.0, initials = "WC"
        )
        val payloadSlot = slot<CheckoutOrderPayload>()
        coEvery { orderRepo.saveCheckoutOrder(capture(payloadSlot), any()) } returns savedOrder

        viewModel.increaseProduct(coffeeA)
        viewModel.checkout()
        advanceUntilIdle()

        val payload = payloadSlot.captured
        assertNull(payload.discountId)
        assertNull(payload.discountPercent)
        assertEquals(0.0, payload.discountAmount)
    }

    @Test
    fun checkout_success_clearsSelectedDiscount() = runTest {
        advanceUntilIdle()
        val savedOrder = CafeOrder(
            id = "#POS-1025", customerName = "Walk-in Customer",
            itemsSummary = "Americano", itemCount = 1,
            timeLabel = "10:00 AM", status = CafeOrderStatus.PREPARING,
            total = 72.0, initials = "WC"
        )
        coEvery { orderRepo.saveCheckoutOrder(any(), any()) } returns savedOrder

        viewModel.increaseProduct(coffeeA)
        viewModel.setDiscount(pwdDiscount)
        viewModel.checkout()
        advanceUntilIdle()

        assertNull(viewModel.selectedDiscount.value)
        assertEquals(0.0, viewModel.discountAmount.value)
    }

    @Test
    fun onDiscountCreated_appendsAndAutoSelects() = runTest {
        advanceUntilIdle()
        val brandNew = Discount(
            id = "DSC-002", name = "May Day", percent = 25.0, isBuiltIn = false
        )
        viewModel.onDiscountCreated(brandNew)
        assertTrue(viewModel.availableDiscounts.value.orEmpty().any { it.id == "DSC-002" })
        assertEquals(brandNew, viewModel.selectedDiscount.value)
    }

    @Test
    fun onDiscountCreated_duplicateId_doesNotDuplicate() = runTest {
        advanceUntilIdle()
        // pwdDiscount is already in availableDiscounts; "creating" it again
        // should still leave a single entry for PWD_ID.
        viewModel.onDiscountCreated(pwdDiscount)
        val pwdRows = viewModel.availableDiscounts.value.orEmpty().filter { it.id == Discount.PWD_ID }
        assertEquals(1, pwdRows.size)
    }
}
