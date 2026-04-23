package com.example.zejioscafese.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.StringRes
import com.example.zejioscafese.R
import com.example.zejioscafese.core.network.NetworkErrorFormatter
import com.example.zejioscafese.pos.data.model.CategorySalesRecord
import com.example.zejioscafese.reports.data.model.ReportTransaction
import com.example.zejioscafese.reports.data.model.SalesTimelinePoint
import com.example.zejioscafese.reports.data.repository.ReportsRepository
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ReportsViewModel(
    private val reportsRepository: ReportsRepository = ReportsRepository()
) : ViewModel() {

    enum class DateRange(
        @StringRes val labelRes: Int,
        @StringRes val subtitleRes: Int,
        val timelineGranularity: ReportsRepository.TimelineGranularity
    ) {
        HOURLY(
            labelRes = R.string.reports_range_hourly,
            subtitleRes = R.string.reports_granularity_hourly_subtitle,
            timelineGranularity = ReportsRepository.TimelineGranularity.HOURLY
        ),
        DAILY(
            labelRes = R.string.reports_range_daily,
            subtitleRes = R.string.reports_granularity_daily_subtitle,
            timelineGranularity = ReportsRepository.TimelineGranularity.DAILY
        ),
        WEEKLY(
            labelRes = R.string.reports_range_weekly,
            subtitleRes = R.string.reports_granularity_weekly_subtitle,
            timelineGranularity = ReportsRepository.TimelineGranularity.WEEKLY
        ),
        MONTHLY(
            labelRes = R.string.reports_range_monthly,
            subtitleRes = R.string.reports_granularity_monthly_subtitle,
            timelineGranularity = ReportsRepository.TimelineGranularity.MONTHLY
        )
    }

    private val _selectedRange = MutableLiveData(DateRange.DAILY)
    val selectedRange: LiveData<DateRange> = _selectedRange

    private val _salesByDateRange = MutableLiveData<List<SalesTimelinePoint>>(emptyList())
    val salesByDateRange: LiveData<List<SalesTimelinePoint>> = _salesByDateRange

    private val _salesByCategory = MutableLiveData<List<CategorySalesRecord>>(emptyList())
    val salesByCategory: LiveData<List<CategorySalesRecord>> = _salesByCategory

    private val _totalRevenue = MutableLiveData(0.0)
    val totalRevenue: LiveData<Double> = _totalRevenue

    private val _totalOrders = MutableLiveData(0)
    val totalOrders: LiveData<Int> = _totalOrders

    private val _avgOrderValue = MutableLiveData(0.0)
    val avgOrderValue: LiveData<Double> = _avgOrderValue

    private val _bestCategory = MutableLiveData(NO_CATEGORY)
    val bestCategory: LiveData<String> = _bestCategory

    private val _transactions = MutableLiveData<List<ReportTransaction>>(emptyList())
    val transactions: LiveData<List<ReportTransaction>> = _transactions

    private val _reportError = MutableLiveData<String?>(null)
    val reportError: LiveData<String?> = _reportError
    private var refreshJob: Job? = null
    private var lastSuccessfulRefreshAt: Long = 0L

    init {
        refreshReports(force = true)
    }

    fun refreshReports(force: Boolean = false) {
        if (refreshJob?.isActive == true) {
            return
        }
        if (!force && !shouldRefresh()) {
            return
        }

        val selected = _selectedRange.value ?: DateRange.DAILY
        refreshJob = viewModelScope.launch {
            try {
                val today = LocalDate.now()
                val dateWindow = when (selected) {
                    DateRange.HOURLY -> ReportsRepository.ReportDateWindow(
                        startDate = today,
                        endDate = today,
                        timelineGranularity = selected.timelineGranularity
                    )

                    DateRange.DAILY -> ReportsRepository.ReportDateWindow(
                        startDate = today.minusDays(6),
                        endDate = today,
                        timelineGranularity = selected.timelineGranularity
                    )

                    DateRange.WEEKLY -> ReportsRepository.ReportDateWindow(
                        startDate = today
                            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                            .minusWeeks(7),
                        endDate = today,
                        timelineGranularity = selected.timelineGranularity
                    )

                    DateRange.MONTHLY -> ReportsRepository.ReportDateWindow(
                        startDate = today.withDayOfMonth(1).minusMonths(11),
                        endDate = today,
                        timelineGranularity = selected.timelineGranularity
                    )
                }

                val snapshot = reportsRepository.fetchReportsSnapshot(dateWindow)
                _salesByDateRange.value = snapshot.salesByDateRange
                _salesByCategory.value = snapshot.salesByCategory
                _totalRevenue.value = snapshot.totalRevenue
                _totalOrders.value = snapshot.totalOrders
                _avgOrderValue.value = snapshot.averageOrderValue
                _bestCategory.value = snapshot.bestCategory
                _transactions.value = snapshot.transactions
                _reportError.value = null
                lastSuccessfulRefreshAt = System.currentTimeMillis()
            } catch (exception: Exception) {
                _reportError.value = NetworkErrorFormatter.toUserMessage(
                    exception = exception,
                    fallbackMessage = DEFAULT_ERROR_MESSAGE
                )
            } finally {
                refreshJob = null
            }
        }
    }

    fun refreshReportsIfStale(maxAgeMs: Long = REPORT_REFRESH_INTERVAL_MS) {
        if (shouldRefresh(maxAgeMs)) {
            refreshReports(force = true)
        }
    }

    fun setDateRange(range: DateRange) {
        if (_selectedRange.value == range) {
            refreshReports(force = true)
            return
        }

        _selectedRange.value = range
        refreshReports(force = true)
    }

    fun onReportErrorConsumed() {
        _reportError.value = null
    }

    private fun shouldRefresh(maxAgeMs: Long = REPORT_REFRESH_INTERVAL_MS): Boolean {
        return lastSuccessfulRefreshAt == 0L ||
            System.currentTimeMillis() - lastSuccessfulRefreshAt >= maxAgeMs
    }

    private companion object {
        const val NO_CATEGORY = "N/A"
        const val DEFAULT_ERROR_MESSAGE = "Failed to load reports right now."
        const val REPORT_REFRESH_INTERVAL_MS = 60_000L
    }
}
