package com.example.zejioscafese.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.zejioscafese.R
import com.example.zejioscafese.databinding.FragmentInventoryBinding
import com.example.zejioscafese.pos.data.model.Ingredient
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InventoryFragment : Fragment() {

    private var _binding: FragmentInventoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: InventoryViewModel by viewModels()

    private lateinit var ingredientAdapter: IngredientAdapter
    private lateinit var producibleProductAdapter: ProducibleProductAdapter

    private var selectedChipCategory = ALL_CATEGORY

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInventoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSearch()
        setupBottomNavigation()
        setupAddButton()
        setupSortSpinner()
        setupFilterChips()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshInventory()
    }

    private fun setupRecyclerView() {
        ingredientAdapter = IngredientAdapter(
            onEditClick = { showEditDialog(it) },
            onRestockClick = { showRestockDialog(it) }
        )
        producibleProductAdapter = ProducibleProductAdapter()

        binding.rvIngredients.apply {
            adapter = ingredientAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        binding.rvProducibleProducts.apply {
            adapter = producibleProductAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupSearch() {
        binding.etInventorySearch.doAfterTextChanged { text ->
            viewModel.setSearchQuery(text?.toString().orEmpty())
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomInventoryNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navInventoryIngredients -> {
                    viewModel.setScreenMode(InventoryViewModel.ScreenMode.INGREDIENTS)
                    true
                }

                R.id.navInventoryCanProduce -> {
                    viewModel.setScreenMode(InventoryViewModel.ScreenMode.PRODUCTION)
                    true
                }

                else -> false
            }
        }

        binding.bottomInventoryNavigation.selectedItemId = R.id.navInventoryIngredients
    }

    private fun setupFilterChips() {
        val categories = viewModel.getCategories()
        val container = binding.chipContainer
        container.removeAllViews()

        categories.forEach { category ->
            val chip = TextView(requireContext()).apply {
                text = category
                textSize = 13f

                val isSelected = category == selectedChipCategory
                setBackgroundResource(
                    if (isSelected) R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected
                )
                setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        if (isSelected) R.color.chip_selected_text else R.color.pos_text_primary
                    )
                )
                setPadding(dpToPx(14), dpToPx(6), dpToPx(14), dpToPx(6))

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.marginEnd = dpToPx(8)
                layoutParams = params

                setOnClickListener {
                    selectedChipCategory = category
                    viewModel.setCategory(category)
                    setupFilterChips()
                }
            }

            container.addView(chip)
        }
    }

    private fun setupSortSpinner() {
        val sortOptions = arrayOf("By Name", "By Stock Level", "By Value")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, sortOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSort.adapter = adapter

        binding.spinnerSort.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val mode = when (position) {
                        0 -> InventoryViewModel.SortMode.NAME
                        1 -> InventoryViewModel.SortMode.STOCK_LEVEL
                        2 -> InventoryViewModel.SortMode.VALUE
                        else -> InventoryViewModel.SortMode.NAME
                    }
                    viewModel.setSortMode(mode)
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
    }

    private fun setupAddButton() {
        binding.btnAddIngredient.setOnClickListener {
            showAddDialog()
        }
    }

    private fun observeViewModel() {
        viewModel.ingredientList.observe(viewLifecycleOwner) { list ->
            ingredientAdapter.submitList(list.toList())
            if (viewModel.screenMode.value == InventoryViewModel.ScreenMode.INGREDIENTS) {
                setupFilterChips()
            }
            renderCurrentMetrics()
        }

        viewModel.producibleProductList.observe(viewLifecycleOwner) { list ->
            producibleProductAdapter.submitList(list.toList())
            if (viewModel.screenMode.value == InventoryViewModel.ScreenMode.PRODUCTION) {
                setupFilterChips()
            }
            renderCurrentMetrics()
        }

        viewModel.lowStockIngredients.observe(viewLifecycleOwner) {
            renderCurrentMetrics()
        }

        viewModel.totalInventoryValue.observe(viewLifecycleOwner) {
            renderCurrentMetrics()
        }

        viewModel.totalIngredientCount.observe(viewLifecycleOwner) {
            renderCurrentMetrics()
        }

        viewModel.outOfStockProducts.observe(viewLifecycleOwner) {
            renderCurrentMetrics()
        }

        viewModel.estimatedProductionValue.observe(viewLifecycleOwner) {
            renderCurrentMetrics()
        }

        viewModel.totalProducibleProductCount.observe(viewLifecycleOwner) {
            renderCurrentMetrics()
        }

        viewModel.screenMode.observe(viewLifecycleOwner) { mode ->
            selectedChipCategory = ALL_CATEGORY
            renderInventoryMode(mode)
            setupFilterChips()
            renderCurrentMetrics()
        }

        viewModel.inventoryError.observe(viewLifecycleOwner) { errorMessage ->
            if (!errorMessage.isNullOrBlank()) {
                Snackbar.make(binding.root, errorMessage, Snackbar.LENGTH_LONG).show()
                viewModel.onInventoryErrorConsumed()
            }
        }
    }

    private fun renderInventoryMode(mode: InventoryViewModel.ScreenMode) {
        val isIngredientsMode = mode == InventoryViewModel.ScreenMode.INGREDIENTS

        binding.rvIngredients.visibility = if (isIngredientsMode) View.VISIBLE else View.GONE
        binding.rvProducibleProducts.visibility = if (isIngredientsMode) View.GONE else View.VISIBLE
        binding.btnAddIngredient.visibility = if (isIngredientsMode) View.VISIBLE else View.GONE

        binding.tvInventoryHeaderTitle.setText(
            if (isIngredientsMode) R.string.inventory_header_ingredients_title
            else R.string.inventory_header_production_title
        )
        binding.tvInventoryHeaderSubtitle.setText(
            if (isIngredientsMode) R.string.inventory_header_ingredients_subtitle
            else R.string.inventory_header_production_subtitle
        )
        binding.etInventorySearch.hint = getString(
            if (isIngredientsMode) R.string.inventory_search_ingredients_hint
            else R.string.inventory_search_products_hint
        )
        if (!binding.etInventorySearch.text.isNullOrEmpty()) {
            binding.etInventorySearch.setText("")
        }

        binding.tvMetricLabelPrimary.setText(
            if (isIngredientsMode) R.string.inventory_metric_ingredients_title
            else R.string.inventory_metric_production_total_title
        )
        binding.tvMetricSubtitlePrimary.setText(
            if (isIngredientsMode) R.string.inventory_metric_ingredients_subtitle
            else R.string.inventory_metric_production_total_subtitle
        )
        binding.tvMetricLabelSecondary.setText(
            if (isIngredientsMode) R.string.inventory_metric_low_stock_title
            else R.string.inventory_metric_out_of_stock_title
        )
        binding.tvMetricSubtitleSecondary.setText(
            if (isIngredientsMode) R.string.inventory_metric_low_stock_subtitle
            else R.string.inventory_metric_out_of_stock_subtitle
        )
        binding.tvMetricLabelTertiary.setText(
            if (isIngredientsMode) R.string.inventory_metric_value_title
            else R.string.inventory_metric_sales_value_title
        )
        binding.tvMetricSubtitleTertiary.setText(
            if (isIngredientsMode) R.string.inventory_metric_value_subtitle
            else R.string.inventory_metric_sales_value_subtitle
        )

        val expectedNavigationItem = if (isIngredientsMode) {
            R.id.navInventoryIngredients
        } else {
            R.id.navInventoryCanProduce
        }
        if (binding.bottomInventoryNavigation.selectedItemId != expectedNavigationItem) {
            binding.bottomInventoryNavigation.selectedItemId = expectedNavigationItem
        }
    }

    private fun renderCurrentMetrics() {
        when (viewModel.screenMode.value ?: InventoryViewModel.ScreenMode.INGREDIENTS) {
            InventoryViewModel.ScreenMode.INGREDIENTS -> {
                binding.tvTotalIngredients.text =
                    (viewModel.totalIngredientCount.value ?: 0).toString()
                binding.tvLowStockCount.text =
                    (viewModel.lowStockIngredients.value?.size ?: 0).toString()
                binding.tvInventoryValue.text =
                    formatCurrency(viewModel.totalInventoryValue.value ?: 0.0)
            }

            InventoryViewModel.ScreenMode.PRODUCTION -> {
                binding.tvTotalIngredients.text =
                    (viewModel.totalProducibleProductCount.value ?: 0).toString()
                binding.tvLowStockCount.text =
                    (viewModel.outOfStockProducts.value?.size ?: 0).toString()
                binding.tvInventoryValue.text =
                    formatCurrency(viewModel.estimatedProductionValue.value ?: 0.0)
            }
        }
    }

    private fun showRestockDialog(ingredient: Ingredient) {
        val ctx = requireContext()
        val inputField = EditText(ctx).apply {
            hint = "Quantity to add (${ingredient.unit})"
            inputType =
                android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
        }

        AlertDialog.Builder(ctx)
            .setTitle("Restock: ${ingredient.name}")
            .setMessage("Current stock: ${ingredient.currentStock} ${ingredient.unit}")
            .setView(inputField)
            .setPositiveButton("Restock") { _, _ ->
                val quantity = inputField.text.toString().toDoubleOrNull()
                if (quantity != null && quantity > 0) {
                    viewModel.restockIngredient(ingredient.id, quantity)
                    setupFilterChips()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditDialog(ingredient: Ingredient) {
        val ctx = requireContext()
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(12), dpToPx(20), dpToPx(4))
        }

        val etName = createLabeledField(container, "Name", ingredient.name)
        val etCategory = createLabeledField(container, "Category", ingredient.category)
        val etUnit = createLabeledField(container, "Unit", ingredient.unit)
        val etStock = createLabeledField(
            container,
            "Current Stock",
            ingredient.currentStock.toString(),
            android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        )
        val etMinStock = createLabeledField(
            container,
            "Minimum Stock",
            ingredient.minimumStock.toString(),
            android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        )
        val etCost = createLabeledField(
            container,
            "Cost Per Unit (PHP)",
            ingredient.costPerUnit.toString(),
            android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        )

        AlertDialog.Builder(ctx)
            .setTitle("Edit Ingredient")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val updated = ingredient.copy(
                    name = etName.text.toString().ifBlank { ingredient.name },
                    category = etCategory.text.toString().ifBlank { ingredient.category },
                    unit = etUnit.text.toString().ifBlank { ingredient.unit },
                    currentStock = etStock.text.toString().toDoubleOrNull() ?: ingredient.currentStock,
                    minimumStock = etMinStock.text.toString().toDoubleOrNull()
                        ?: ingredient.minimumStock,
                    costPerUnit = etCost.text.toString().toDoubleOrNull() ?: ingredient.costPerUnit
                )
                viewModel.updateIngredient(updated)
                setupFilterChips()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddDialog() {
        val ctx = requireContext()
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(12), dpToPx(20), dpToPx(4))
        }

        val etName = createLabeledField(container, "Name", "")
        val etCategory = createLabeledField(container, "Category (e.g. Dairy, Produce)", "")
        val etUnit = createLabeledField(container, "Unit (e.g. kg, L, pcs)", "")
        val etStock = createLabeledField(
            container,
            "Initial Stock",
            "",
            android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        )
        val etMinStock = createLabeledField(
            container,
            "Minimum Stock",
            "",
            android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        )
        val etCost = createLabeledField(
            container,
            "Cost Per Unit (PHP)",
            "",
            android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        )

        AlertDialog.Builder(ctx)
            .setTitle("Add Ingredient")
            .setView(container)
            .setPositiveButton("Add") { _, _ ->
                val name = etName.text.toString()
                if (name.isNotBlank()) {
                    val newIngredient = Ingredient(
                        id = viewModel.generateId(),
                        name = name,
                        category = etCategory.text.toString().ifBlank { "Dry Goods" },
                        unit = etUnit.text.toString().ifBlank { "pcs" },
                        currentStock = etStock.text.toString().toDoubleOrNull() ?: 0.0,
                        minimumStock = etMinStock.text.toString().toDoubleOrNull() ?: 1.0,
                        costPerUnit = etCost.text.toString().toDoubleOrNull() ?: 0.0,
                        lastRestocked = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            .format(Date())
                    )
                    viewModel.addIngredient(newIngredient)
                    setupFilterChips()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createLabeledField(
        container: LinearLayout,
        label: String,
        value: String,
        inputType: Int = android.text.InputType.TYPE_CLASS_TEXT
    ): EditText {
        val ctx = requireContext()
        val labelView = TextView(ctx).apply {
            text = label
            textSize = 12f
            setTextColor(ContextCompat.getColor(ctx, R.color.pos_text_secondary))
            setPadding(0, dpToPx(8), 0, dpToPx(4))
        }
        container.addView(labelView)

        val editText = EditText(ctx).apply {
            setText(value)
            this.inputType = inputType
            textSize = 14f
            setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
            setBackgroundResource(R.drawable.bg_input_field)
        }
        container.addView(editText)
        return editText
    }

    private fun formatCurrency(value: Double): String {
        return String.format(Locale.getDefault(), "PHP %,.2f", value)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val ALL_CATEGORY = "All"
    }
}
