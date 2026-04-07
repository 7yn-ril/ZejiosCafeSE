package com.example.zejioscafese.ui

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.zejioscafese.R
import com.example.zejioscafese.databinding.FragmentInventoryBinding
import com.example.zejioscafese.inventory.data.model.ProductCategoryOption
import com.example.zejioscafese.inventory.data.model.ProductEditorDraft
import com.example.zejioscafese.inventory.data.model.ProductRecipeIngredient
import com.example.zejioscafese.inventory.data.model.ProducibleProduct
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
        applyBottomNavThemeColors()
        setupAddButton()
        setupPaginationControls()
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
        producibleProductAdapter = ProducibleProductAdapter(
            onViewIngredientsClick = { showProductIngredientsDialog(it) },
            onEditClick = { showProductDialog(product = it) },
            onDeleteClick = { showSoftDeleteProductDialog(it) }
        )

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
            when (viewModel.screenMode.value ?: InventoryViewModel.ScreenMode.INGREDIENTS) {
                InventoryViewModel.ScreenMode.INGREDIENTS -> showAddDialog()
                InventoryViewModel.ScreenMode.PRODUCTION -> showProductDialog()
            }
        }
    }

    private fun setupPaginationControls() {
        binding.btnInventoryPreviousPage.setOnClickListener {
            viewModel.goToPreviousPage()
        }
        binding.btnInventoryNextPage.setOnClickListener {
            viewModel.goToNextPage()
        }
    }

    private fun observeViewModel() {
        viewModel.ingredientList.observe(viewLifecycleOwner) { list ->
            ingredientAdapter.submitList(list.toList())
            binding.rvIngredients.scrollToPosition(0)
            if (viewModel.screenMode.value == InventoryViewModel.ScreenMode.INGREDIENTS) {
                setupFilterChips()
            }
            renderCurrentMetrics()
        }

        viewModel.producibleProductList.observe(viewLifecycleOwner) { list ->
            producibleProductAdapter.submitList(list.toList())
            binding.rvProducibleProducts.scrollToPosition(0)
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

        viewModel.averageProduciblePrice.observe(viewLifecycleOwner) {
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

        viewModel.paginationState.observe(viewLifecycleOwner) { state ->
            renderPagination(state)
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
        binding.btnAddIngredient.visibility = View.VISIBLE
        binding.btnAddIngredient.setText(
            if (isIngredientsMode) R.string.inventory_add_ingredient
            else R.string.inventory_add_product
        )

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

    private fun renderPagination(state: InventoryViewModel.PaginationState) {
        val shouldShow = state.totalItems > INVENTORY_PAGE_SIZE
        binding.inventoryPaginationContainer.visibility = if (shouldShow) View.VISIBLE else View.GONE
        binding.tvInventoryPageInfo.text = getString(
            R.string.pagination_page_status,
            state.currentPage,
            state.totalPages
        )
        binding.btnInventoryPreviousPage.isEnabled = state.canGoPrevious
        binding.btnInventoryNextPage.isEnabled = state.canGoNext
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
                    formatCurrency(viewModel.averageProduciblePrice.value ?: 0.0)
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

    private fun showSoftDeleteProductDialog(product: ProducibleProduct) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.inventory_soft_delete_product))
            .setMessage(
                getString(
                    R.string.inventory_soft_delete_confirmation,
                    product.name
                )
            )
            .setPositiveButton(R.string.inventory_soft_delete_action) { _, _ ->
                viewModel.softDeleteProduct(product)
            }
            .setNegativeButton(R.string.inventory_dialog_cancel, null)
            .show()
    }

    private fun showProductIngredientsDialog(product: ProducibleProduct) {
        val recipe = viewModel.getRecipeForProduct(product.id)
        val message = if (recipe.isEmpty()) {
            getString(R.string.inventory_no_recipe_assigned)
        } else {
            recipe.joinToString(separator = "\n") { ingredient ->
                getString(
                    R.string.inventory_recipe_line,
                    ingredient.ingredientName,
                    formatQuantity(ingredient.requiredQuantity),
                    ingredient.ingredientUnit
                )
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle(
                getString(
                    R.string.inventory_product_ingredients_title,
                    product.name
                )
            )
            .setMessage(message)
            .setPositiveButton(R.string.inventory_dialog_close, null)
            .show()
    }

    private fun showProductDialog(product: ProducibleProduct? = null) {
        val ctx = requireContext()
        val categoryOptions = viewModel.getProductCategoriesForEditor()
        val ingredientOptions = viewModel.getIngredientOptionsForEditor()

        if (categoryOptions.isEmpty()) {
            Snackbar.make(
                binding.root,
                R.string.inventory_missing_categories,
                Snackbar.LENGTH_LONG
            ).show()
            return
        }

        if (ingredientOptions.isEmpty()) {
            Snackbar.make(
                binding.root,
                R.string.inventory_missing_ingredients,
                Snackbar.LENGTH_LONG
            ).show()
            return
        }

        val scrollView = ScrollView(ctx)
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(12), dpToPx(20), dpToPx(4))
        }
        scrollView.addView(container)

        val categorySpinner = createLabeledSpinner(
            container = container,
            label = getString(R.string.inventory_field_category),
            options = categoryOptions.map(ProductCategoryOption::name),
            selectedIndex = categoryOptions.indexOfFirst { option ->
                option.id == product?.categoryId
            }.coerceAtLeast(0)
        )

        val etProductName = createLabeledField(
            container,
            getString(R.string.inventory_field_product_name),
            product?.productName.orEmpty()
        )
        val etVariantName = createLabeledField(
            container,
            getString(R.string.inventory_field_variant_name),
            product?.variantName
                ?.takeUnless {
                    it.equals("standard", ignoreCase = true) ||
                        it.equals("combo", ignoreCase = true)
                }
                .orEmpty()
        )
        val etPrice = createLabeledField(
            container,
            getString(R.string.inventory_field_price),
            product?.price?.toString().orEmpty(),
            android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        )

        val recipeLabel = TextView(ctx).apply {
            text = getString(R.string.inventory_field_recipe)
            textSize = 12f
            setTextColor(ContextCompat.getColor(ctx, R.color.pos_text_secondary))
            setPadding(0, dpToPx(12), 0, dpToPx(4))
        }
        container.addView(recipeLabel)

        val ingredientRowsContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(ingredientRowsContainer)

        val rowHolders = mutableListOf<ProductIngredientRowHolder>()
        val existingRecipe = product?.let { viewModel.getRecipeForProduct(it.id) }.orEmpty()

        fun addIngredientRow(initial: ProductRecipeIngredient? = null) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dpToPx(4), 0, dpToPx(4))
            }

            val ingredientSpinner = Spinner(ctx).apply {
                adapter = ArrayAdapter(
                    ctx,
                    android.R.layout.simple_spinner_item,
                    ingredientOptions.map(Ingredient::name)
                ).also { adapter ->
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
            }

            val ingredientParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT)
            ingredientParams.weight = 1f
            row.addView(ingredientSpinner, ingredientParams)

            val quantityInput = EditText(ctx).apply {
                hint = getString(R.string.inventory_field_required_quantity)
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                setText(initial?.requiredQuantity?.toString().orEmpty())
                setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
                setBackgroundResource(R.drawable.bg_input_field)
            }
            val quantityParams = LinearLayout.LayoutParams(dpToPx(120), LinearLayout.LayoutParams.WRAP_CONTENT)
            quantityParams.marginStart = dpToPx(8)
            row.addView(quantityInput, quantityParams)

            val removeButton = ImageButton(ctx).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                background = ContextCompat.getDrawable(ctx, android.R.color.transparent)
                contentDescription = getString(R.string.inventory_remove_recipe_ingredient)
            }
            val removeParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            removeParams.marginStart = dpToPx(4)
            row.addView(removeButton, removeParams)

            val holder = ProductIngredientRowHolder(
                container = row,
                ingredientSpinner = ingredientSpinner,
                quantityInput = quantityInput
            )
            rowHolders.add(holder)
            ingredientRowsContainer.addView(row)

            initial?.let { currentIngredient ->
                val selectedIndex = ingredientOptions.indexOfFirst {
                    it.id == currentIngredient.ingredientId
                }
                if (selectedIndex >= 0) {
                    ingredientSpinner.setSelection(selectedIndex)
                }
            }

            removeButton.setOnClickListener {
                ingredientRowsContainer.removeView(row)
                rowHolders.remove(holder)
                if (rowHolders.isEmpty()) {
                    addIngredientRow()
                }
            }
        }

        if (existingRecipe.isEmpty()) {
            addIngredientRow()
        } else {
            existingRecipe.forEach(::addIngredientRow)
        }

        val addIngredientButton = Button(ctx).apply {
            text = getString(R.string.inventory_add_recipe_ingredient)
            setTextColor(ContextCompat.getColor(ctx, R.color.pos_secondary))
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_chip_unselected)
            setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
        }
        val addButtonParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        addButtonParams.topMargin = dpToPx(8)
        container.addView(addIngredientButton, addButtonParams)
        addIngredientButton.setOnClickListener { addIngredientRow() }

        val dialog = AlertDialog.Builder(ctx)
            .setTitle(
                if (product == null) {
                    getString(R.string.inventory_add_product_title)
                } else {
                    getString(R.string.inventory_edit_product_title)
                }
            )
            .setView(scrollView)
            .setPositiveButton(
                if (product == null) {
                    getString(R.string.inventory_dialog_add)
                } else {
                    getString(R.string.inventory_dialog_save)
                },
                null
            )
            .setNegativeButton(R.string.inventory_dialog_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selectedCategory = categoryOptions.getOrNull(categorySpinner.selectedItemPosition)
                val productName = etProductName.text.toString().trim()
                val variantName = etVariantName.text.toString().trim().ifBlank { "Standard" }
                val price = etPrice.text.toString().toDoubleOrNull()
                val groupedIngredients = rowHolders.mapNotNull { holder ->
                    val ingredient = ingredientOptions.getOrNull(holder.ingredientSpinner.selectedItemPosition)
                        ?: return@mapNotNull null
                    val requiredQuantity = holder.quantityInput.text.toString().toDoubleOrNull()
                        ?: return@mapNotNull null
                    if (requiredQuantity <= 0.0) {
                        return@mapNotNull null
                    }
                    ProductRecipeIngredient(
                        ingredientId = ingredient.id,
                        ingredientName = ingredient.name,
                        ingredientUnit = ingredient.unit,
                        requiredQuantity = requiredQuantity
                    )
                }.groupBy(ProductRecipeIngredient::ingredientId)
                    .mapNotNull { (_, ingredients) ->
                        val first = ingredients.firstOrNull() ?: return@mapNotNull null
                        first.copy(
                            requiredQuantity = ingredients.sumOf(ProductRecipeIngredient::requiredQuantity)
                        )
                    }

                when {
                    selectedCategory == null -> {
                        etProductName.error = getString(R.string.inventory_validation_category_required)
                    }

                    productName.isBlank() -> {
                        etProductName.error = getString(R.string.inventory_validation_product_name_required)
                    }

                    price == null || price <= 0.0 -> {
                        etPrice.error = getString(R.string.inventory_validation_price_required)
                    }

                    groupedIngredients.isEmpty() -> {
                        Snackbar.make(
                            binding.root,
                            R.string.inventory_validation_ingredients_required,
                            Snackbar.LENGTH_LONG
                        ).show()
                    }

                    else -> {
                        val draft = ProductEditorDraft(
                            productId = product?.productId,
                            productVariantId = product?.id,
                            categoryId = selectedCategory.id,
                            productName = productName,
                            variantName = variantName,
                            price = price,
                            ingredients = groupedIngredients
                        )

                        if (product == null) {
                            viewModel.addProduct(draft)
                        } else {
                            viewModel.updateProduct(draft)
                        }
                        dialog.dismiss()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun createLabeledSpinner(
        container: LinearLayout,
        label: String,
        options: List<String>,
        selectedIndex: Int = 0
    ): Spinner {
        val ctx = requireContext()
        val labelView = TextView(ctx).apply {
            text = label
            textSize = 12f
            setTextColor(ContextCompat.getColor(ctx, R.color.pos_text_secondary))
            setPadding(0, dpToPx(8), 0, dpToPx(4))
        }
        container.addView(labelView)

        return Spinner(ctx).apply {
            adapter = ArrayAdapter(
                ctx,
                android.R.layout.simple_spinner_item,
                options
            ).also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(selectedIndex.coerceIn(0, options.lastIndex.coerceAtLeast(0)))
            setBackgroundResource(R.drawable.bg_input_field)
            container.addView(this)
        }
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

    /**
     * Runtime fallback: resolves bottom nav colors from the current theme and
     * applies them programmatically. Handles cases where the system night mode
     * changes without an Activity recreation.
     */
    private fun applyBottomNavThemeColors() {
        val bgColor = resolveThemeColor(R.attr.bottomNavBackground)
        val iconTint = resolveThemeColor(R.attr.bottomNavIconTint)
        val textColor = resolveThemeColor(R.attr.bottomNavTextColor)
        val selectedColor = resolveThemeColor(R.attr.bottomNavSelectedItemColor)

        if (bgColor != 0 || iconTint != 0 || textColor != 0 || selectedColor != 0) {
            val bottomNav = binding.bottomInventoryNavigation

            if (bgColor != 0) {
                bottomNav.setBackgroundColor(bgColor)
            }

            val states = arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            )

            if (iconTint != 0 && selectedColor != 0) {
                bottomNav.itemIconTintList = ColorStateList(states, intArrayOf(selectedColor, iconTint))
            }

            if (textColor != 0) {
                bottomNav.itemTextColor = ColorStateList(states, intArrayOf(selectedColor, textColor))
            }
        }
    }

    /** Resolves a theme attribute to its color int value; returns 0 if not found. */
    private fun resolveThemeColor(attrRes: Int): Int {
        val typedValue = TypedValue()
        val resolved = requireContext().theme.resolveAttribute(attrRes, typedValue, true)
        return if (resolved) typedValue.data else 0
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun formatQuantity(quantity: Double): String {
        return if (quantity % 1.0 == 0.0) {
            quantity.toInt().toString()
        } else {
            String.format(Locale.getDefault(), "%.2f", quantity)
                .trimEnd('0')
                .trimEnd('.')
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val ALL_CATEGORY = "All"
        const val INVENTORY_PAGE_SIZE = 8
    }

    private data class ProductIngredientRowHolder(
        val container: View,
        val ingredientSpinner: Spinner,
        val quantityInput: EditText
    )
}
