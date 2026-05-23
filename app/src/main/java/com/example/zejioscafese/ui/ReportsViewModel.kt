package com.example.zejioscafese.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.StringRes
import com.example.zejioscafese.R
import com.example.zejioscafese.core.network.NetworkErrorFormatter
import com.example.zejioscafese.pos.data.model.CategorySalesRecord
import com.example.zejioscafese.pos.data.model.ProductSalesRecord
import com.example.zejioscafese.reports.data.ReportsAggregator
import com.example.zejioscafese.reports.data.model.ProductGroupFilter
import com.example.zejioscafese.reports.data.model.ReportTransaction
import com.example.zejioscafese.reports.data.model.ReportsDataset
import com.example.zejioscafese.reports.data.model.SalesTimelinePoint
import com.example.zejioscafese.reports.data.model.TypeBreakdown
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
        ),
        YEARLY(
            labelRes = R.string.reports_range_yearly,
            subtitleRes = R.string.reports_granularity_yearly_subtitle,
            timelineGranularity = ReportsRepository.TimelineGranularity.YEARLY
        )
    }

    private val _selectedRange = MutableLiveData(DateRange.DAILY)
    val selectedRange: LiveData<DateRange> = _selectedRange

    private val _productGroupFilter = MutableLiveData(ProductGroupFilter.ALL)
    val productGroupFilter: LiveData<ProductGroupFilter> = _productGroupFilter

    private val _selectedBucketLabel = MutableLiveData<String?>(null)
    val selectedBucketLabel: LiveData<String?> = _selectedBucketLabel

    private val _salesByDateRange = MutableLiveData<List<SalesTimelinePoint>>(emptyList())
    val salesByDateRange: LiveData<List<SalesTimelinePoint>> = _salesByDateRange

    private val _chartReferenceMax = MutableLiveData(0.0)
    val chartReferenceMax: LiveData<Double> = _chartReferenceMax

    private val _salesByCategory = MutableLiveData<List<CategorySalesRecord>>(emptyList())
    val salesByCategory: LiveData<List<CategorySalesRecord>> = _salesByCategory

    private val _salesByProduct = MutableLiveData<List<ProductSalesRecord>>(emptyList())
    val salesByProduct: LiveData<List<ProductSalesRecord>> = _salesByProduct

    private val _salesByOrderType = MutableLiveData<List<TypeBreakdown>>(emptyList())
    val salesByOrderType: LiveData<List<TypeBreakdown>> = _salesByOrderType

    private val _salesByPaymentMethod = MutableLiveData<List<TypeBreakdown>>(emptyList())
    val salesByPaymentMethod: LiveData<List<TypeBreakdown>> = _salesByPaymentMethod

    private val _totalRevenue = MutableLiveData(0.0)
    val totalRevenue: LiveData<Double> = _totalRevenue

    private val _totalOrders = MutableLiveData(0)
    val totalOrders: LiveData<Int> = _totalOrders

    private val _avgOrderValue = MutableLiveData(0.0)
    val avgOrderValue: LiveData<Double> = _avgOrderValue

    private val _bestProduct = MutableLiveData(NO_PRODUCT)
    val bestProduct: LiveData<String> = _bestProduct

    private val _transactions = MutableLiveData<List<ReportTransaction>>(emptyList())
    val transactions: LiveData<List<ReportTransaction>> = _transactions

    private val _reportError = MutableLiveData<String?>(null)
    val reportError: LiveData<String?> = _reportError

    private var refreshJob: Job? = null
    private var lastSuccessfulRefreshAt: Long = 0L

    // Most recently fetched raw dataset. Kept around so bucket/filter
    // changes can re-aggregate without re-hitting Supabase.
    private var cachedDataset: ReportsDataset? = null

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
                        // Calendar-year window: Jan 1 through Dec 31 of
                        // the current year. Future months render as
                        // empty bars and fill in as the year progresses.
                        startDate = today.withDayOfYear(1),
                        endDate = today.withMonth(12).withDayOfMonth(31),
                        timelineGranularity = selected.timelineGranularity
                    )

                    DateRange.YEARLY -> ReportsRepository.ReportDateWindow(
                        startDate = today.withDayOfYear(1).minusYears(4),
                        endDate = today,
                        timelineGranularity = selected.timelineGranularity
                    )
                }

                val dataset = reportsRepository.fetchReportsDataset(dateWindow)
                cachedDataset = dataset

                // Pick a default bucket for this range: today (or the
                // current period's bucket). The user can drill into other
                // buckets by tapping bars in the chart.
                val provisionalLabels = ReportsAggregator.aggregate(
                    dataset = dataset,
                    selectedBucketLabel = null,
                    productGroupFilter = _productGroupFilter.value ?: ProductGroupFilter.ALL
                ).bucketLabels
                _selectedBucketLabel.value = ReportsAggregator.defaultBucketLabel(
                    bucketLabels = provisionalLabels,
                    granularity = dataset.timelineGranularity
                )

                publishCurrentAggregation()
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
        // Wipe the cached bucket selection — the next refresh will pick a
        // fresh default for the new range.
        _selectedBucketLabel.value = null
        refreshReports(force = true)
    }

    fun setSelectedBucketLabel(label: String?) {
        if (_selectedBucketLabel.value == label) return
        _selectedBucketLabel.value = label
        publishCurrentAggregation()
    }

    fun setProductGroupFilter(filter: ProductGroupFilter) {
        if (_productGroupFilter.value == filter) return
        _productGroupFilter.value = filter

        // Re-pick the default bucket for the new filter so a stale label
        // (e.g. for an hour that no longer has any orders under this
        // filter) doesn't leave the screen empty.
        val dataset = cachedDataset
        if (dataset != null) {
            val newLabels = ReportsAggregator.aggregate(
                dataset = dataset,
                selectedBucketLabel = null,
                productGroupFilter = filter
            ).bucketLabels
            val current = _selectedBucketLabel.value
            if (current == null || current !in newLabels) {
                _selectedBucketLabel.value = ReportsAggregator.defaultBucketLabel(
                    bucketLabels = newLabels,
                    granularity = dataset.timelineGranularity
                )
            }
        }
        publishCurrentAggregation()
    }

    fun onReportErrorConsumed() {
        _reportError.value = null
    }

    private fun publishCurrentAggregation() {
        val dataset = cachedDataset ?: return
        val result = ReportsAggregator.aggregate(
            dataset = dataset,
            selectedBucketLabel = _selectedBucketLabel.value,
            productGroupFilter = _productGroupFilter.value ?: ProductGroupFilter.ALL
        )
        _salesByDateRange.value = result.salesByDateRange
        _chartReferenceMax.value = result.referenceMaxSales
        _salesByCategory.value = result.salesByCategory
        _salesByProduct.value = result.salesByProduct
        _salesByOrderType.value = result.salesByOrderType
        _salesByPaymentMethod.value = result.salesByPaymentMethod
        _totalRevenue.value = result.totalRevenue
        _totalOrders.value = result.totalOrders
        _avgOrderValue.value = result.averageOrderValue
        _bestProduct.value = result.bestProduct
        _transactions.value = result.transactions
    }

    private fun shouldRefresh(maxAgeMs: Long = REPORT_REFRESH_INTERVAL_MS): Boolean {
        return lastSuccessfulRefreshAt == 0L ||
            System.currentTimeMillis() - lastSuccessfulRefreshAt >= maxAgeMs
    }

    private companion object {
        const val NO_PRODUCT = "N/A"
        const val DEFAULT_ERROR_MESSAGE = "Failed to load reports right now."
        const val REPORT_REFRESH_INTERVAL_MS = 60_000L
    }
}
