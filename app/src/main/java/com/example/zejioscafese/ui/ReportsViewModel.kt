package com.example.zejioscafese.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.zejioscafese.pos.data.model.CategorySalesRecord
import com.example.zejioscafese.pos.data.model.DailySalesRecord
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ReportsViewModel : ViewModel() {

    enum class DateRange { TODAY, THIS_WEEK, THIS_MONTH }

    // ── Seed data for 30 days ────────────────────────────────────────────

    private val allDailySales: List<DailySalesRecord>
    private val allTransactions: List<TransactionRecord>

    data class TransactionRecord(
        val orderId: String,
        val items: String,
        val total: Double,
        val status: String,
        val date: String
    )

    init {
        val cal = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dailyList = mutableListOf<DailySalesRecord>()
        val txList = mutableListOf<TransactionRecord>()

        // Revenue patterns to keep data realistic
        val baseRevenues = listOf(
            8450.0, 9200.0, 11500.0, 10800.0, 13200.0, 14500.0, 12100.0,
            7800.0, 9600.0, 10200.0, 11800.0, 12400.0, 15100.0, 13800.0,
            8100.0, 9900.0, 10500.0, 11200.0, 12800.0, 14200.0, 12600.0,
            7500.0, 9300.0, 10100.0, 11600.0, 13000.0, 14800.0, 13400.0,
            8900.0, 12450.0
        )

        for (i in 29 downTo 0) {
            cal.time = java.util.Date()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val date = dateFormat.format(cal.time)
            val revenue = baseRevenues[29 - i]
            val orders = (revenue / 250.0).toInt() + (5..12).random()
            val avg = revenue / orders

            dailyList.add(DailySalesRecord(date, revenue, orders, avg))
        }

        // Sample transactions
        val statuses = listOf("Completed", "Completed", "Completed", "Pending", "Completed")
        val itemSets = listOf(
            "Iced Caramel Latte, Cheesecake",
            "Cold Brew, Truffle Fries",
            "Matcha Latte x2",
            "Beef Tapa Bowl, Iced Latte",
            "Chicken Panini, Brownie",
            "Seasonal Latte, Nacho Bites",
            "Weekend Combo Set",
            "Mushroom Pasta, Cold Brew",
            "Cheesecake x2, Matcha Latte",
            "Truffle Fries, Iced Latte x2"
        )

        for (i in 0 until 15) {
            cal.time = java.util.Date()
            cal.add(Calendar.DAY_OF_YEAR, -(i / 3))
            val date = dateFormat.format(cal.time)
            txList.add(
                TransactionRecord(
                    orderId = "#POS-${1460 - i}",
                    items = itemSets[i % itemSets.size],
                    total = listOf(345.0, 280.0, 350.0, 430.0, 355.0, 345.0, 320.0, 395.0, 535.0, 465.0)[i % 10],
                    status = statuses[i % statuses.size],
                    date = date
                )
            )
        }

        allDailySales = dailyList
        allTransactions = txList
    }

    // ── Category sales data ──────────────────────────────────────────────

    private val categorySalesData = listOf(
        CategorySalesRecord("Drinks", 156_800.0, 892, 42.5),
        CategorySalesRecord("Meals", 98_400.0, 384, 26.7),
        CategorySalesRecord("Desserts", 54_200.0, 312, 14.7),
        CategorySalesRecord("Snacks", 38_600.0, 268, 10.5),
        CategorySalesRecord("Specials", 20_800.0, 64, 5.6)
    )

    // ── LiveData ─────────────────────────────────────────────────────────

    private val _selectedRange = MutableLiveData(DateRange.THIS_MONTH)
    val selectedRange: LiveData<DateRange> = _selectedRange

    private val _salesByDateRange = MutableLiveData<List<DailySalesRecord>>()
    val salesByDateRange: LiveData<List<DailySalesRecord>> = _salesByDateRange

    private val _salesByCategory = MutableLiveData(categorySalesData)
    val salesByCategory: LiveData<List<CategorySalesRecord>> = _salesByCategory

    private val _totalRevenue = MutableLiveData<Double>()
    val totalRevenue: LiveData<Double> = _totalRevenue

    private val _totalOrders = MutableLiveData<Int>()
    val totalOrders: LiveData<Int> = _totalOrders

    private val _avgOrderValue = MutableLiveData<Double>()
    val avgOrderValue: LiveData<Double> = _avgOrderValue

    private val _bestCategory = MutableLiveData<String>()
    val bestCategory: LiveData<String> = _bestCategory

    private val _transactions = MutableLiveData<List<TransactionRecord>>()
    val transactions: LiveData<List<TransactionRecord>> = _transactions

    init {
        setDateRange(DateRange.THIS_MONTH)
    }

    fun setDateRange(range: DateRange) {
        _selectedRange.value = range

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        val today = dateFormat.format(cal.time)

        val filteredSales = when (range) {
            DateRange.TODAY -> allDailySales.filter { it.date == today }
            DateRange.THIS_WEEK -> {
                cal.add(Calendar.DAY_OF_YEAR, -7)
                val weekAgo = dateFormat.format(cal.time)
                allDailySales.filter { it.date >= weekAgo }
            }
            DateRange.THIS_MONTH -> allDailySales
        }

        _salesByDateRange.value = filteredSales

        val revenue = filteredSales.sumOf { it.totalSales }
        val orders = filteredSales.sumOf { it.totalOrders }
        _totalRevenue.value = revenue
        _totalOrders.value = orders
        _avgOrderValue.value = if (orders > 0) revenue / orders else 0.0

        _bestCategory.value = categorySalesData.maxByOrNull { it.totalRevenue }?.categoryName ?: "N/A"

        // Filter transactions
        val filteredTx = when (range) {
            DateRange.TODAY -> allTransactions.filter { it.date == today }
            DateRange.THIS_WEEK -> {
                val c2 = Calendar.getInstance()
                c2.add(Calendar.DAY_OF_YEAR, -7)
                val weekAgo = dateFormat.format(c2.time)
                allTransactions.filter { it.date >= weekAgo }
            }
            DateRange.THIS_MONTH -> allTransactions
        }
        _transactions.value = filteredTx
    }
}
