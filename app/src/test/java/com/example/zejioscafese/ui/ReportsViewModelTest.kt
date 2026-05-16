package com.example.zejioscafese.ui

import com.example.zejioscafese.helpers.InstantExecutorExtension
import com.example.zejioscafese.reports.data.model.ReportsSnapshot
import com.example.zejioscafese.reports.data.repository.ReportsRepository
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
class ReportsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var reportsRepo: ReportsRepository
    private lateinit var viewModel: ReportsViewModel

    private val sampleSnapshot = ReportsSnapshot(
        totalRevenue = 5000.0,
        totalOrders = 50,
        averageOrderValue = 100.0,
        bestProduct = "Latte",
        salesByDateRange = emptyList(),
        salesByCategory = emptyList(),
        salesByProduct = emptyList(),
        transactions = emptyList()
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        reportsRepo = mockk()
        coEvery { reportsRepo.fetchReportsSnapshot(any(), any()) } returns sampleSnapshot
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
    fun init_noReportError_afterSuccessfulLoad() = runTest {
        advanceUntilIdle()
        assertNull(viewModel.reportError.value)
    }

    @Test
    fun init_callsRepositoryOnce() = runTest {
        advanceUntilIdle()
        coVerify(exactly = 1) { reportsRepo.fetchReportsSnapshot(any(), any()) }
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
    fun setDateRange_differentRange_triggersRepositoryFetch() = runTest {
        advanceUntilIdle()
        viewModel.setDateRange(ReportsViewModel.DateRange.MONTHLY)
        advanceUntilIdle()
        coVerify(atLeast = 2) { reportsRepo.fetchReportsSnapshot(any(), any()) }
    }

    @Test
    fun setDateRange_sameRange_stillTriggersRefresh() = runTest {
        advanceUntilIdle()
        viewModel.setDateRange(ReportsViewModel.DateRange.DAILY)  // same as default
        advanceUntilIdle()
        coVerify(atLeast = 2) { reportsRepo.fetchReportsSnapshot(any(), any()) }
    }

    // ── error handling ────────────────────────────────────────────────────────

    @Test
    fun refreshReports_repositoryThrows_setsReportError() = runTest {
        coEvery { reportsRepo.fetchReportsSnapshot(any(), any()) } throws RuntimeException("timeout")
        viewModel.setDateRange(ReportsViewModel.DateRange.HOURLY)
        advanceUntilIdle()
        assertNotNull(viewModel.reportError.value)
    }

    @Test
    fun refreshReports_repositoryThrows_doesNotClearPreviousRevenue() = runTest {
        advanceUntilIdle()  // first load succeeds → totalRevenue = 5000
        coEvery { reportsRepo.fetchReportsSnapshot(any(), any()) } throws RuntimeException("error")
        viewModel.setDateRange(ReportsViewModel.DateRange.WEEKLY)
        advanceUntilIdle()
        assertEquals(5000.0, viewModel.totalRevenue.value)
    }

    @Test
    fun onReportErrorConsumed_clearsError() = runTest {
        coEvery { reportsRepo.fetchReportsSnapshot(any(), any()) } throws RuntimeException("error")
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
        coVerify(exactly = 1) { reportsRepo.fetchReportsSnapshot(any(), any()) }
    }

    @Test
    fun refreshReportsIfStale_immediatelyAfterInit_zeroMaxAge_refetches() = runTest {
        advanceUntilIdle()
        viewModel.refreshReportsIfStale(maxAgeMs = 0L)
        advanceUntilIdle()
        coVerify(atLeast = 2) { reportsRepo.fetchReportsSnapshot(any(), any()) }
    }
}
