package com.example.zejioscafese.dashboard.presentation

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zejioscafese.dashboard.data.repository.DashboardRepository
import com.example.zejioscafese.dashboard.model.DashboardSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val dashboardRepository: DashboardRepository = DashboardRepository()
) : ViewModel() {

    private val _dashboardSnapshot = MutableLiveData(DashboardSnapshot.empty())
    val dashboardSnapshot: LiveData<DashboardSnapshot> = _dashboardSnapshot

    private val _isDashboardLoading = MutableLiveData(false)
    val isDashboardLoading: LiveData<Boolean> = _isDashboardLoading

    private val _dashboardError = MutableLiveData<String?>(null)
    val dashboardError: LiveData<String?> = _dashboardError

    private var autoRefreshJob: Job? = null
    private var refreshJob: Job? = null

    fun refreshDashboard(showLoading: Boolean = false) {
        if (refreshJob?.isActive == true) {
            return
        }

        refreshJob = viewModelScope.launch {
            if (showLoading) {
                _isDashboardLoading.value = true
            }

            try {
                _dashboardSnapshot.value = dashboardRepository.fetchDashboardSnapshot()
                _dashboardError.value = null
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to load dashboard data from Supabase", exception)
                _dashboardError.value = exception.message ?: "Failed to load dashboard data."
            } finally {
                _isDashboardLoading.value = false
            }
        }
    }

    fun startAutoRefresh() {
        refreshDashboard(showLoading = _dashboardSnapshot.value == null)

        if (autoRefreshJob?.isActive == true) {
            return
        }

        autoRefreshJob = viewModelScope.launch {
            while (true) {
                delay(AUTO_REFRESH_INTERVAL_MS)
                refreshDashboard()
            }
        }
    }

    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    fun onDashboardErrorConsumed() {
        _dashboardError.value = null
    }

    override fun onCleared() {
        stopAutoRefresh()
        super.onCleared()
    }

    private companion object {
        const val TAG = "DashboardViewModel"
        const val AUTO_REFRESH_INTERVAL_MS = 30_000L
    }
}
