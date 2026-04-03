package com.example.zejioscafese.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.zejioscafese.pos.data.model.Ingredient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InventoryViewModel : ViewModel() {

    private val allIngredients = mutableListOf(
        Ingredient("i1", "Whole Milk", "Dairy", "L", 25.0, 10.0, 85.00, "2025-03-28"),
        Ingredient("i2", "Coffee Beans (Arabica)", "Dry Goods", "kg", 8.5, 5.0, 650.00, "2025-03-25"),
        Ingredient("i3", "White Sugar", "Dry Goods", "kg", 15.0, 8.0, 55.00, "2025-03-30"),
        Ingredient("i4", "All-Purpose Flour", "Dry Goods", "kg", 4.0, 5.0, 45.00, "2025-03-15"),
        Ingredient("i5", "Fresh Eggs", "Dairy", "pcs", 48.0, 24.0, 8.50, "2025-04-01"),
        Ingredient("i6", "Butter (Unsalted)", "Dairy", "kg", 3.0, 2.0, 420.00, "2025-03-27"),
        Ingredient("i7", "Vanilla Syrup", "Dry Goods", "L", 2.5, 2.0, 320.00, "2025-03-20"),
        Ingredient("i8", "Paper Cups (16oz)", "Supplies", "pcs", 150.0, 100.0, 5.50, "2025-03-29"),
        Ingredient("i9", "Chocolate Powder", "Dry Goods", "kg", 1.5, 2.0, 280.00, "2025-03-18"),
        Ingredient("i10", "Matcha Powder", "Dry Goods", "g", 200.0, 150.0, 2.80, "2025-03-22"),
        Ingredient("i11", "Whipping Cream", "Dairy", "L", 5.0, 3.0, 190.00, "2025-03-30"),
        Ingredient("i12", "Chicken Breast", "Produce", "kg", 6.0, 4.0, 260.00, "2025-04-01")
    )

    private val _ingredientList = MutableLiveData<List<Ingredient>>(allIngredients.toList())
    val ingredientList: LiveData<List<Ingredient>> = _ingredientList

    private val _lowStockIngredients = MutableLiveData<List<Ingredient>>()
    val lowStockIngredients: LiveData<List<Ingredient>> = _lowStockIngredients

    private val _totalInventoryValue = MutableLiveData<Double>()
    val totalInventoryValue: LiveData<Double> = _totalInventoryValue

    private var searchQuery = ""
    private var selectedCategory = "All"
    private var sortMode = SortMode.NAME

    enum class SortMode { NAME, STOCK_LEVEL, VALUE }

    init {
        refreshDerived()
    }

    fun setSearchQuery(query: String) {
        searchQuery = query.trim().lowercase(Locale.getDefault())
        applyFilters()
    }

    fun setCategory(category: String) {
        selectedCategory = category
        applyFilters()
    }

    fun setSortMode(mode: SortMode) {
        sortMode = mode
        applyFilters()
    }

    fun addIngredient(ingredient: Ingredient) {
        allIngredients.add(ingredient)
        applyFilters()
    }

    fun updateIngredient(updated: Ingredient) {
        val index = allIngredients.indexOfFirst { it.id == updated.id }
        if (index != -1) {
            allIngredients[index] = updated
            applyFilters()
        }
    }

    fun restockIngredient(id: String, quantity: Double) {
        val index = allIngredients.indexOfFirst { it.id == id }
        if (index != -1) {
            val current = allIngredients[index]
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            allIngredients[index] = current.copy(
                currentStock = current.currentStock + quantity,
                lastRestocked = dateFormat.format(Date())
            )
            applyFilters()
        }
    }

    fun generateId(): String {
        val maxNum = allIngredients
            .mapNotNull { it.id.removePrefix("i").toIntOrNull() }
            .maxOrNull() ?: 0
        return "i${maxNum + 1}"
    }

    fun getCategories(): List<String> {
        return listOf("All") + allIngredients.map { it.category }.distinct().sorted()
    }

    private fun applyFilters() {
        var filtered = allIngredients.toList()

        if (selectedCategory != "All") {
            filtered = filtered.filter { it.category == selectedCategory }
        }

        if (searchQuery.isNotBlank()) {
            filtered = filtered.filter {
                it.name.lowercase(Locale.getDefault()).contains(searchQuery)
            }
        }

        filtered = when (sortMode) {
            SortMode.NAME -> filtered.sortedBy { it.name }
            SortMode.STOCK_LEVEL -> filtered.sortedBy { it.currentStock / it.minimumStock }
            SortMode.VALUE -> filtered.sortedByDescending { it.currentStock * it.costPerUnit }
        }

        _ingredientList.value = filtered
        refreshDerived()
    }

    private fun refreshDerived() {
        _lowStockIngredients.value = allIngredients.filter { it.currentStock <= it.minimumStock }
        _totalInventoryValue.value = allIngredients.sumOf { it.currentStock * it.costPerUnit }
    }
}
