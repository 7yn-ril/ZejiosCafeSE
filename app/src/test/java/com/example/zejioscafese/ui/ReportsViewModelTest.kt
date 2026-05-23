package com.example.zejioscafese.ui

import com.example.zejioscafese.helpers.InstantExecutorExtension
import com.example.zejioscafese.reports.data.model.DatasetOrder
import com.example.zejioscafese.reports.data.model.DatasetOrderItem
import com.example.zejioscafese.reports.data.model.ProductGroupFilter
import com.example.zejioscafese.reports.data.model.ReportsDataset
import com.example.zejioscafese.reports.data.model.TimelineGranularity
import com.example.zejioscafese.reports.data.repository.ReportsRepository
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
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
class ReportsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var reportsRepo: ReportsRepository
    private lateinit var viewModel: ReportsViewModel

    // A dataset shaped so the default bucket selection (today) yields the
    // KPI values the tests assert on: 50 completed orders totalling 5,000,
    // each carrying one "Latte" line at PHP 100. Average order value
    // works out to 100.
    private val today: LocalDate = LocalDate.now()
    private val zone: ZoneId = ZoneId.systemDefault()
    private val sampleDataset: ReportsDataset = run {
        val orders = (1..50).map { index ->
            val orderId = "order-$index"
            DatasetOrder(
                orderId = orderId,
                orderNumber = "#POS-${1000 + index}",
                createdAt = ZonedDateTime.of(today.atTime(10, 0), zone),
                localDate = today,
                total = 100.0,
                subtotal = 100.0,
                statusDisplay = "Completed",
                discountLabel = null,
                discountPercent = null,
                discountAmount = 0.0,
                paymentMethod = "cash",
                orderType = "dine_in"
            )
        }
        val items = orders.map { order ->
            DatasetOrderItem(
                orderId = order.orderId,
                productId = "product-latte",
                productName = "Latte",
                variantName = "Standard",
                quantity = 1,
                unitPrice = 100.0,
                lineTotal = 100.0
            )
        }
        ReportsDataset(
            startDate = today.minusDays(6),
            endDate = today,
            timelineGranularity = TimelineGranularity.DAILY,
            zoneId = zone,
            orders = orders,
            items = items,
            categoryByProductId = mapOf("product-latte" to "Coffee")
        )
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        reportsRepo = mockk()
        coEvery { reportsRepo.fetchReportsDataset(any(), any()) } returns sampleDataset
        viewModel = ReportsViewModel(reportsRepo)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    // ── initial state ─────────────────────────────────────────────────────────

    @Test
    fun selectedRange_default_isDaily() {
        assertEquals(ReportsViewModel.DateRange.DAILY, viewModel.selectedRange.value)
    }

    @Test
    fun selectedFilter_default_isAll() {
        assertEquals(ProductGroupFilter.ALL, viewModel.productGroupFilter.value)
    }

    @Test
    fun init_loadsReports_totalRevenuePopulated() = runTest {
        advanceUntilIdle()
        assertEquals(5000.0, viewModel.totalRevenue.value)
    }

    @Test
    fun init_loadsReports_totalOrdersPopulated() = runTest {
        advanceUntilIdle()
        assertEquals(50, viewModel.totalOrders.value)
    }

    @Test
    fun init_loadsReports_avgOrderValuePopulated() = runTest {
        advanceUntilIdle()
        assertEquals(100.0, viewModel.avgOrderValue.value)
    }

    @Test
    fun init_loadsReports_bestProductPopulated() = runTest {
        advanceUntilIdle()
        assertEquals("Latte", viewModel.bestProduct.value)
    }

    @Test
    fun init_loadsReports_selectsTodaysBucket() = runTest {
        advanceUntilIdle()
        // Default bucket = today's label; KPIs above also verify the
        // aggregation routes through that bucket.
        assertNotNull(viewModel.selectedBucketLabel.value)
    }

    @Test
    fun init_noReportError_afterSuccessfulLoad() = runTest {
        advanceUntilIdle()
        assertNull(viewModel.reportError.value)
    }

    @Test
    fun init_callsRepositoryOnce() = runTest {
        advanceUntilIdle()
        coVerify(exactly = 1) { reportsRepo.fetchReportsDataset(any(), any()) }
    }

    // ── setDateRange ──────────────────────────────────────────────────────────

    @Test
    fun setDateRange_differentRange_updatesSelectedRange() = runTest {
        advanceUntilIdle()
        viewModel.setDateRange(ReportsViewModel.DateRange.WEEKLY)
        assertEquals(ReportsViewModel.DateRange.WEEKLY, viewModel.selectedRange.value)
    }

    @Test
    fun setDateRange_hourly_updatesSelectedRange() = runTest {
        advanceUntilIdle()
        viewModel.setDateRange(ReportsViewModel.DateRange.HOURLY)
        assertEquals(ReportsViewModel.DateRange.HOURLY, viewModel.selectedRange.value)
    }

    @Test
    fun setDateRange_monthly_updatesSelectedRange() = runTest {
        advanceUntilIdle()
        viewModel.setDateRange(ReportsViewModel.DateRange.MONTHLY)
        assertEquals(ReportsViewModel.DateRange.MONTHLY, viewModel.selectedRange.value)
    }

    @Test
    fun setDateRange_yearly_updatesSelectedRange() = runTest {
        advanceUntilIdle()
        viewModel.setDateRange(ReportsViewModel.DateRange.YEARLY)
        assertEquals(ReportsViewModel.DateRange.YEARLY, viewModel.selectedRange.value)
    }

    @Test
    fun setDateRange_differentRange_triggersRepositoryFetch() = runTest {
        advanceUntilIdle()
        viewModel.setDateRange(ReportsViewModel.DateRange.MONTHLY)
        advanceUntilIdle()
        coVerify(atLeast = 2) { reportsRepo.fetchReportsDataset(any(), any()) }
    }

    @Test
    fun setDateRange_sameRange_stillTriggersRefresh() = runTest {
        advanceUntilIdle()
        viewModel.setDateRange(ReportsViewModel.DateRange.DAILY)  // same as default
        advanceUntilIdle()
        coVerify(atLeast = 2) { reportsRepo.fetchReportsDataset(any(), any()) }
    }

    // ── filter + bucket selection ─────────────────────────────────────────────

    @Test
    fun setProductGroupFilter_drinksKeepsCoffeeData() = runTest {
        advanceUntilIdle()
        viewModel.setProductGroupFilter(ProductGroupFilter.DRINKS)
        // Coffee category matches DRINKS, so revenue stays at 5,000.
        assertEquals(5000.0, viewModel.totalRevenue.value)
        assertEquals(ProductGroupFilter.DRINKS, viewModel.productGroupFilter.value)
    }

    @Test
    fun setProductGroupFilter_foodHidesDrinksOnlyDataset() = runTest {
        advanceUntilIdle()
        viewModel.setProductGroupFilter(ProductGroupFilter.FOOD)
        // The sample dataset is Coffee-only, so a Food filter wipes out
        // every metric — revenue and orders both fall to zero.
        assertEquals(0.0, viewModel.totalRevenue.value)
        assertEquals(0, viewModel.totalOrders.value)
    }

    @Test
    fun setSelectedBucketLabel_unknownLabelZeroesKpis() = runTest {
        advanceUntilIdle()
        viewModel.setSelectedBucketLabel("nope")
        assertEquals(0.0, viewModel.totalRevenue.value)
        assertEquals(0, viewModel.totalOrders.value)
    }

    // ── error handling ────────────────────────────────────────────────────────

    @Test
    fun refreshReports_repositoryThrows_setsReportError() = runTest {
        coEvery { reportsRepo.fetchReportsDataset(any(), any()) } throws RuntimeException("timeout")
        viewModel.setDateRange(ReportsViewModel.DateRange.HOURLY)
        advanceUntilIdle()
        assertNotNull(viewModel.reportError.value)
    }

    @Test
    fun refreshReports_repositoryThrows_doesNotClearPreviousRevenue() = runTest {
        advanceUntilIdle()  // first load succeeds → totalRevenue = 5000
        coEvery { reportsRepo.fetchReportsDataset(any(), any()) } throws RuntimeException("error")
        viewModel.setDateRange(ReportsViewModel.DateRange.WEEKLY)
        advanceUntilIdle()
        assertEquals(5000.0, viewModel.totalRevenue.value)
    }

    @Test
    fun onReportErrorConsumed_clearsError() = runTest {
        coEvery { reportsRepo.fetchReportsDataset(any(), any()) } throws RuntimeException("error")
        viewModel.setDateRange(ReportsViewModel.DateRange.HOURLY)
        advanceUntilIdle()
        assertNotNull(viewModel.reportError.value)
        viewModel.onReportErrorConsumed()
        assertNull(viewModel.reportError.value)
    }

    // ── refreshReportsIfStale ─────────────────────────────────────────────────

    @Test
    fun refreshReportsIfStale_veryLongMaxAge_doesNotRefetch() = runTest {
        advanceUntilIdle()  // initial load marks lastSuccessfulRefreshAt
        viewModel.refreshReportsIfStale(maxAgeMs = Long.MAX_VALUE)
        advanceUntilIdle()
        // Only the initial call; not stale with MAX_VALUE age window
        coVerify(exactly = 1) { reportsRepo.fetchReportsDataset(any(), any()) }
    }

    @Test
    fun refreshReportsIfStale_immediatelyAfterInit_zeroMaxAge_refetches() = runTest {
        advanceUntilIdle()
        viewModel.refreshReportsIfStale(maxAgeMs = 0L)
        advanceUntilIdle()
        coVerify(atLeast = 2) { reportsRepo.fetchReportsDataset(any(), any()) }
    }
}
