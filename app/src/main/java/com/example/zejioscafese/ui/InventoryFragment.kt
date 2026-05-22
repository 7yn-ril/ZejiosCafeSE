package com.example.zejioscafese.ui

import android.content.res.ColorStateList
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.Fragment
import android.graphics.Rect
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.zejioscafese.R
import com.example.zejioscafese.databinding.FragmentInventoryBinding
import com.example.zejioscafese.inventory.data.model.ProductCategoryOption
import com.example.zejioscafese.inventory.data.model.ProductEditorDraft
import com.example.zejioscafese.inventory.data.model.ProductIngredientFilter
import com.example.zejioscafese.inventory.data.model.ProductRecipeIngredient
import com.example.zejioscafese.inventory.data.model.RecipePricing
import com.example.zejioscafese.inventory.data.model.ProducibleProduct
import com.example.zejioscafese.pos.data.model.Ingredient
import com.example.zejioscafese.pos.data.model.IngredientStockStatus
import com.example.zejioscafese.pos.data.model.IngredientUnits
import com.example.zejioscafese.pos.ui.RemoteImageLoader
import com.example.zejioscafese.ui.applyZejiosCafeButtonStyling
import com.example.zejioscafese.ui.showErrorDialog
import com.example.zejioscafese.ui.showInfoDialog
import com.example.zejioscafese.ui.showStyledDialog
import com.example.zejioscafese.ui.showWarningDialog
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InventoryFragment : Fragment() {

    private var _binding: FragmentInventoryBinding? = null
    private val binding: FragmentInventoryBinding
        get() = requireNotNull(_binding) { "Inventory view binding is only valid between onCreateView and onDestroyView." }
    private val viewModel: InventoryViewModel by activityViewModels()

    private lateinit var ingredientAdapter: IngredientAdapter
    private lateinit var producibleProductAdapter: ProducibleProductAdapter

    private var selectedChipCategory = ALL_CATEGORY
    private var productImageUrlInput: EditText? = null
    private var productImagePreview: ImageView? = null

    private val pickProductImageLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val imageUrl = uri.toString()
        productImageUrlInput?.setText(imageUrl)
        productImagePreview?.let { preview ->
            renderProductImagePreview(preview, imageUrl)
        }
    }

    // UI CHANGE: tracks the set of ingredient ids we've already alerted on
    // this session so the low-stock notice fires once per item, not on
    // every list refresh. Cleared if the item recovers above the threshold.
    private val notifiedLowStockIds = mutableSetOf<String>()

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
        setupMetricActions()
        setupPaginationControls()
        setupSortSpinner()
        setupFilterChips()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshInventoryIfStale()
    }

    // UI CHANGE: Ingredients list is now a single-column tabular view to
     // match the reference. Producible products keep the 3-column grid since
     // that screen wasn't part of the redesign brief.
    private fun setupRecyclerView() {
        ingredientAdapter = IngredientAdapter(
            onEditClick = { showEditDialog(it) },
            onRestockClick = { showRestockDialog(it) }
        )
        producibleProductAdapter = ProducibleProductAdapter(
            onViewIngredientsClick = { showProductIngredientsDialog(it) },
            onEditClick = { showProductDialog(product = it) },
            onDeleteClick = { showSoftDeleteProductDialog(it) },
            onRestoreClick = { showRestoreProductDialog(it) }
        )

        binding.rvIngredients.apply {
            adapter = ingredientAdapter
            layoutManager = LinearLayoutManager(requireContext())
            // UI CHANGE: hairline divider between table rows.
            if (itemDecorationCount == 0) {
                val divider = androidx.recyclerview.widget.DividerItemDecoration(
                    requireContext(),
                    LinearLayoutManager.VERTICAL
                )
                ContextCompat.getDrawable(requireContext(), R.drawable.divider_inventory_row)?.let {
                    divider.setDrawable(it)
                }
                addItemDecoration(divider)
            }
        }

        val gridSpacingPx = (12 * resources.displayMetrics.density).toInt()
        val gridSpan = 3
        binding.rvProducibleProducts.apply {
            adapter = producibleProductAdapter
            layoutManager = GridLayoutManager(requireContext(), gridSpan)
            if (itemDecorationCount == 0) {
                addItemDecoration(GridSpacingItemDecoration(gridSpan, gridSpacingPx))
            }
        }
    }

    /**
     * Even spacing for a fixed-span grid: every cell gets identical horizontal
     * margin and a uniform bottom margin, so columns line up flush with the
     * card padding instead of the doubling-margin pattern that comes from
     * just setting `layout_marginEnd` on each item.
     */
    private class GridSpacingItemDecoration(
        private val spanCount: Int,
        private val spacing: Int
    ) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            val position = parent.getChildAdapterPosition(view)
            if (position == RecyclerView.NO_POSITION) return
            val column = position % spanCount
            outRect.left = column * spacing / spanCount
            outRect.right = spacing - (column + 1) * spacing / spanCount
            if (position >= spanCount) outRect.top = spacing
        }
    }

    private fun setupSearch() {
        binding.etInventorySearch.doAfterTextChanged { text ->
            viewModel.setSearchQuery(text?.toString().orEmpty())
        }
    }

    private fun setupBottomNavigation() {
        binding.navTabItems.setOnClickListener {
            viewModel.setScreenMode(InventoryViewModel.ScreenMode.INGREDIENTS)
        }
        binding.navTabProducts.setOnClickListener {
            viewModel.setScreenMode(InventoryViewModel.ScreenMode.PRODUCTION)
        }
        val initialMode = viewModel.screenMode.value ?: InventoryViewModel.ScreenMode.INGREDIENTS
        applyBottomNavSelection(initialMode)
    }

    /**
     * Paints the segmented bottom nav so the active tab gets its accent
     * fill (brown for Items, green for Products) with a white icon and
     * label, while the inactive tab stays light and obviously tappable.
     */
    private fun applyBottomNavSelection(mode: InventoryViewModel.ScreenMode) {
        val ctx = requireContext()
        val white = ContextCompat.getColor(ctx, R.color.white)
        val itemsAccent = ContextCompat.getColor(ctx, R.color.pos_primary)
        val productsAccent = ContextCompat.getColor(ctx, R.color.pos_secondary)
        val isItems = mode == InventoryViewModel.ScreenMode.INGREDIENTS

        binding.navTabItems.setBackgroundResource(
            if (isItems) R.drawable.bg_inventory_mode_tab_ingredients_selected
            else R.drawable.bg_inventory_mode_tab_idle
        )
        binding.tvNavTabItems.setTextColor(if (isItems) white else itemsAccent)
        binding.icNavTabItems.imageTintList =
            ColorStateList.valueOf(if (isItems) white else itemsAccent)

        binding.navTabProducts.setBackgroundResource(
            if (!isItems) R.drawable.bg_inventory_mode_tab_production_selected
            else R.drawable.bg_inventory_mode_tab_idle
        )
        binding.tvNavTabProducts.setTextColor(if (!isItems) white else productsAccent)
        binding.icNavTabProducts.imageTintList =
            ColorStateList.valueOf(if (!isItems) white else productsAccent)
    }

    private fun setupFilterChips() {
        val categories = viewModel.getCategories()
        val container = binding.chipContainer
        if (selectedChipCategory !in categories) {
            selectedChipCategory = ALL_CATEGORY
            viewModel.setCategory(ALL_CATEGORY)
        }
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

        binding.btnSortDirection.setOnClickListener {
            viewModel.toggleSortDirection()
        }

        viewModel.sortDirectionLive.observe(viewLifecycleOwner) { direction ->
            val iconRes = when (direction) {
                InventoryViewModel.SortDirection.ASCENDING -> R.drawable.ic_arrow_upward_24
                InventoryViewModel.SortDirection.DESCENDING -> R.drawable.ic_arrow_downward_24
                null -> R.drawable.ic_arrow_upward_24
            }
            binding.btnSortDirection.setImageResource(iconRes)
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

    private fun setupMetricActions() {
        binding.cardInventoryLowStockMetric.setOnClickListener {
            showLowStockRestockDialog()
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
            notifyLowStockIfNeeded(list)
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
                showErrorDialog(requireContext(), errorMessage)
                viewModel.onInventoryErrorConsumed()
            }
        }
    }

    private fun renderInventoryMode(mode: InventoryViewModel.ScreenMode) {
        val isItemsMode = mode == InventoryViewModel.ScreenMode.INGREDIENTS

        binding.rvIngredients.visibility = if (isItemsMode) View.VISIBLE else View.GONE
        binding.rvProducibleProducts.visibility = if (isItemsMode) View.GONE else View.VISIBLE
        // Table header columns describe stock fields, so only the Items
        // screen should show it. Products use a grid card layout.
        binding.inventoryTableHeader.visibility = if (isItemsMode) View.VISIBLE else View.GONE
        binding.btnAddIngredient.visibility = View.VISIBLE
        binding.btnAddIngredient.setText(
            if (isItemsMode) R.string.inventory_add_ingredient
            else R.string.inventory_add_product
        )

        binding.tvInventoryHeaderTitle.setText(
            if (isItemsMode) R.string.inventory_header_ingredients_title
            else R.string.inventory_header_production_title
        )
        binding.tvInventoryHeaderSubtitle.setText(
            if (isItemsMode) R.string.inventory_header_ingredients_subtitle
            else R.string.inventory_header_production_subtitle
        )
        binding.etInventorySearch.hint = getString(
            if (isItemsMode) R.string.inventory_search_ingredients_hint
            else R.string.inventory_search_products_hint
        )
        if (!binding.etInventorySearch.text.isNullOrEmpty()) {
            binding.etInventorySearch.setText("")
        }

        binding.tvMetricLabelPrimary.setText(
            if (isItemsMode) R.string.inventory_metric_ingredients_title
            else R.string.inventory_metric_production_total_title
        )
        binding.tvMetricSubtitlePrimary.setText(
            if (isItemsMode) R.string.inventory_metric_ingredients_subtitle
            else R.string.inventory_metric_production_total_subtitle
        )
        binding.tvMetricLabelSecondary.setText(
            if (isItemsMode) R.string.inventory_metric_low_stock_title
            else R.string.inventory_metric_out_of_stock_title
        )
        binding.tvMetricSubtitleSecondary.setText(
            if (isItemsMode) R.string.inventory_metric_low_stock_subtitle
            else R.string.inventory_metric_out_of_stock_subtitle
        )
        binding.tvMetricLabelTertiary.setText(
            if (isItemsMode) R.string.inventory_metric_value_title
            else R.string.inventory_metric_sales_value_title
        )
        binding.tvMetricSubtitleTertiary.setText(
            if (isItemsMode) R.string.inventory_metric_value_subtitle
            else R.string.inventory_metric_sales_value_subtitle
        )

        // Restocking the low-stock metric only applies to stock-tracked
        // ingredients, so the click target should be live in Items mode
        // and inert (visual-only) in Products mode.
        binding.cardInventoryLowStockMetric.isClickable = isItemsMode
        binding.cardInventoryLowStockMetric.isFocusable = isItemsMode

        applyBottomNavSelection(mode)
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
        val mode = viewModel.screenMode.value ?: InventoryViewModel.ScreenMode.INGREDIENTS
        when (mode) {
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

    private fun showLowStockRestockDialog() {
        val lowStockItems = viewModel.lowStockIngredients.value.orEmpty()
        if (lowStockItems.isEmpty()) {
            showInfoDialog(requireContext(), getString(R.string.inventory_low_stock_dialog_empty))
            return
        }

        val ctx = requireContext()
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(8), dpToPx(20), dpToPx(4))
        }

        var lowStockDialog: AlertDialog? = null
        lowStockItems.forEachIndexed { index, ingredient ->
            if (index > 0) {
                container.addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1
                    ).apply {
                        topMargin = dpToPx(12)
                        bottomMargin = dpToPx(12)
                    }
                    setBackgroundColor(ContextCompat.getColor(ctx, R.color.pos_border))
                })
            }

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val details = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
            }
            row.addView(
                details,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )

            details.addView(TextView(ctx).apply {
                text = ingredient.name
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(ctx, R.color.pos_text_primary))
            })

            details.addView(TextView(ctx).apply {
                text = getString(
                    R.string.inventory_low_stock_dialog_stock_format,
                    formatQuantity(ingredient.currentStock),
                    ingredient.unit,
                    formatQuantity(ingredient.minimumStock),
                    ingredient.unit
                )
                textSize = 13f
                setTextColor(ContextCompat.getColor(ctx, R.color.pos_text_secondary))
                setPadding(0, dpToPx(4), 0, 0)
            })

            val restockButton = MaterialButton(ctx).apply {
                text = getString(R.string.inventory_restock_action)
                isAllCaps = false
                minHeight = dpToPx(36)
                minimumHeight = dpToPx(36)
                cornerRadius = dpToPx(12)
                backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, R.color.pos_primary)
                )
                setTextColor(ContextCompat.getColor(ctx, R.color.white))
                setOnClickListener {
                    lowStockDialog?.dismiss()
                    showRestockDialog(ingredient)
                }
            }
            row.addView(
                restockButton,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dpToPx(16) }
            )
            container.addView(row)
        }

        lowStockDialog = AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.inventory_low_stock_dialog_title))
            .setView(ScrollView(ctx).apply { addView(container) })
            .setNegativeButton(R.string.inventory_dialog_close, null)
            .showStyledDialog(ctx)
    }

    private fun showRestockDialog(ingredient: Ingredient) {
        val ctx = requireContext()
        if (ingredient.isLiquid) {
            showLiquidRestockDialog(ingredient)
            return
        }

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
            .showStyledDialog(ctx)
    }

    private fun showLiquidRestockDialog(ingredient: Ingredient) {
        val ctx = requireContext()
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(12), dpToPx(20), dpToPx(4))
        }

        val currentStock = TextView(ctx).apply {
            text = "Current stock: ${formatQuantity(ingredient.currentStock)} mL"
            textSize = 13f
            setTextColor(ContextCompat.getColor(ctx, R.color.pos_text_secondary))
            setPadding(0, 0, 0, dpToPx(8))
        }
        container.addView(currentStock)

        val etBottleCount = createLabeledField(
            container,
            "Bottles to Add",
            "",
            android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        ).apply {
            hint = "e.g. 3"
        }
        val etMlPerBottle = createLabeledField(
            container,
            "Per Bottle",
            ingredient.mlPerBottle?.let(::trimDecimal).orEmpty(),
            android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        ).apply {
            hint = "e.g. 200"
        }

        val preview = TextView(ctx).apply {
            textSize = 13f
            setTextColor(ContextCompat.getColor(ctx, R.color.pos_text_secondary))
            setPadding(0, dpToPx(12), 0, 0)
        }
        container.addView(preview)

        fun updatePreview() {
            val bottles = etBottleCount.text.toString().toDoubleOrNull()
            val mlPerBottle = etMlPerBottle.text.toString().toDoubleOrNull()
            val addedMl = if (
                bottles != null && bottles > 0.0 &&
                mlPerBottle != null && mlPerBottle > 0.0
            ) {
                bottles * mlPerBottle
            } else {
                0.0
            }

            if (addedMl <= 0.0) {
                preview.text = "Enter bottles and amount per bottle to compute total mL."
                return
            }

            val newStock = ingredient.currentStock + addedMl
            val servingLine = ingredient.mlPerServing?.takeIf { it > 0.0 }?.let { mlPerServing ->
                val addedServings = addedMl / mlPerServing
                val totalServings = newStock / mlPerServing
                "\nAdds ${formatServingCount(addedServings)} servings; new total ${formatServingCount(totalServings)} servings."
            }.orEmpty()
            val addedValue = addedMl * ingredient.costPerUnit
            val totalValue = newStock * ingredient.costPerUnit

            preview.text = "Adds ${formatQuantity(addedMl)} mL. New stock: ${formatQuantity(newStock)} mL.$servingLine\nAdded value: ${formatCurrency(addedValue)}; total value: ${formatCurrency(totalValue)}."
        }

        etBottleCount.doAfterTextChanged { updatePreview() }
        etMlPerBottle.doAfterTextChanged { updatePreview() }
        updatePreview()

        AlertDialog.Builder(ctx)
            .setTitle("Restock: ${ingredient.name}")
            .setView(container)
            .setPositiveButton("Restock") { _, _ ->
                val bottles = etBottleCount.text.toString().toDoubleOrNull()
                val mlPerBottle = etMlPerBottle.text.toString().toDoubleOrNull()
                if (
                    bottles != null && bottles > 0.0 &&
                    mlPerBottle != null && mlPerBottle > 0.0
                ) {
                    viewModel.restockIngredient(ingredient.id, bottles * mlPerBottle)
                    setupFilterChips()
                }
            }
            .setNegativeButton("Cancel", null)
            .showStyledDialog(ctx)
    }

    private fun showEditDialog(ingredient: Ingredient) {
        val ctx = requireContext()
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(12), dpToPx(20), dpToPx(4))
        }

        val etName = createLabeledField(container, "Name", ingredient.name)
        val categoryOptions = viewModel.getIngredientCategoriesForEditor()
        val categorySpinner = createLabeledSpinner(
            container = container,
            label = "Category",
            options = categoryOptions,
            selectedIndex = categoryOptions.indexOf(ingredient.category).coerceAtLeast(0)
        )
        val unitSpinner = createLabeledSpinner(
            container = container,
            label = "Unit",
            options = IngredientUnits.options,
            selectedIndex = unitOptionIndex(ingredient.unit)
        )
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
            "Cost (PHP)",
            displayCostForEditor(ingredient),
            android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        ).apply {
            hint = "For mL: cost per serving; for pcs: cost per piece"
        }

        val liquidSection = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(liquidSection)
        val etMlPerServing = createLabeledField(
            liquidSection,
            "Per Serving",
            ingredient.mlPerServing?.let(::trimDecimal).orEmpty(),
            android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        )
        val etMlPerBottle = createLabeledField(
            liquidSection,
            "Per Bottle",
            ingredient.mlPerBottle?.let(::trimDecimal).orEmpty(),
            android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        )

        fun applyUnitVisibility() {
            liquidSection.visibility = if (selectedUnit(unitSpinner) == IngredientUnits.ML) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
        applyUnitVisibility()
        unitSpinner.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    applyUnitVisibility()
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }

        AlertDialog.Builder(ctx)
            .setTitle("Edit Ingredient")
            .setView(container)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .showStyledDialog(ctx) { dialog ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val updatedUnit = selectedUnit(unitSpinner)
                    val updatedMlPerServing = unitValue(updatedUnit, etMlPerServing)
                    val updatedMlPerBottle = unitValue(updatedUnit, etMlPerBottle)

                    when {
                        updatedUnit == IngredientUnits.ML && updatedMlPerServing == null -> {
                            etMlPerServing.error = getString(R.string.inventory_field_required)
                        }

                        updatedUnit == IngredientUnits.ML && updatedMlPerBottle == null -> {
                            etMlPerBottle.error = getString(R.string.inventory_field_required)
                        }

                        else -> {
                            val updated = ingredient.copy(
                                name = etName.text.toString().ifBlank { ingredient.name },
                                category = categoryOptions
                                    .getOrNull(categorySpinner.selectedItemPosition)
                                    ?: ingredient.category,
                                unit = updatedUnit,
                                currentStock = etStock.text.toString().toDoubleOrNull()
                                    ?: ingredient.currentStock,
                                minimumStock = etMinStock.text.toString().toDoubleOrNull()
                                    ?: ingredient.minimumStock,
                                costPerUnit = costPerUnitFromEditor(
                                    unit = updatedUnit,
                                    mlPerServing = updatedMlPerServing,
                                    costInput = etCost.text.toString().toDoubleOrNull(),
                                    fallbackCostPerUnit = ingredient.costPerUnit
                                ),
                                mlPerServing = updatedMlPerServing,
                                mlPerBottle = updatedMlPerBottle
                            )
                            viewModel.updateIngredient(updated)
                            setupFilterChips()
                            dialog.dismiss()
                        }
                    }
                }
            }
    }

    private fun trimDecimal(value: Double): String {
        return if (value % 1.0 == 0.0) value.toInt().toString()
        else value.toString()
    }

    private fun displayCostForEditor(ingredient: Ingredient): String {
        if (ingredient.isLiquid && ingredient.mlPerServing == null) return ""
        val displayCost = ingredient.costPerServing ?: ingredient.costPerUnit
        return trimDecimal(displayCost)
    }

    private fun unitOptionIndex(unit: String): Int {
        val normalizedUnit = IngredientUnits.normalize(unit)
        return IngredientUnits.options.indexOf(normalizedUnit).coerceAtLeast(0)
    }

    private fun selectedUnit(spinner: Spinner): String {
        return IngredientUnits.normalize(
            IngredientUnits.options.getOrNull(spinner.selectedItemPosition)
                ?: IngredientUnits.PCS
        )
    }

    private fun unitValue(unit: String, field: EditText): Double? {
        if (!IngredientUnits.isMl(unit)) return null
        return field.text.toString().toDoubleOrNull()?.takeIf { it > 0.0 }
    }

    private fun costPerUnitFromEditor(
        unit: String,
        mlPerServing: Double?,
        costInput: Double?,
        fallbackCostPerUnit: Double
    ): Double {
        val cost = costInput ?: return fallbackCostPerUnit
        val servingSize = mlPerServing?.takeIf { it > 0.0 }
        return if (IngredientUnits.isMl(unit) && servingSize != null) {
            cost / servingSize
        } else {
            cost
        }
    }

    // UI CHANGE: Add Ingredient form rebuilt to match the spec — common
    // fields are always visible; the Category dropdown drives whether the
    // mL-only Per Serving and Per Bottle fields appear.
    private fun showAddDialog() {
        val ctx = requireContext()
        val scrollView = ScrollView(ctx)
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(12), dpToPx(20), dpToPx(4))
        }
        scrollView.addView(container)

        // Always-visible fields — Product Name, Category, Price per Piece,
        // Current Stock (pieces) — apply to every category.
        val etName = createLabeledField(
            container,
            getString(R.string.inventory_field_product_name),
            ""
        )
        val categoryOptions = viewModel.getIngredientCategoriesForEditor()
        val defaultIndex = categoryOptions.indexOf("Pantry").coerceAtLeast(0)
        val categorySpinner = createLabeledSpinner(
            container = container,
            label = getString(R.string.inventory_field_category),
            options = categoryOptions,
            selectedIndex = defaultIndex
        )
        val unitSpinner = createLabeledSpinner(
            container = container,
            label = "Unit",
            options = IngredientUnits.options,
            selectedIndex = unitOptionIndex(IngredientUnits.PCS)
        )
        val etPricePerPiece = createLabeledField(
            container,
            getString(R.string.inventory_field_price_per_piece),
            "",
            android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        )
        val etCurrentStockPieces = createLabeledField(
            container,
            getString(R.string.inventory_field_current_stock_pieces),
            "",
            android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        )

        val liquidSection = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(liquidSection)
        val etMlPerServing = createLabeledField(
            liquidSection,
            getString(R.string.inventory_field_ml_per_serving),
            "",
            android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        )
        val etMlPerBottle = createLabeledField(
            liquidSection,
            getString(R.string.inventory_field_ml_per_bottle),
            "",
            android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        )

        fun applyUnitVisibility() {
            liquidSection.visibility = if (selectedUnit(unitSpinner) == IngredientUnits.ML) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
        applyUnitVisibility()
        unitSpinner.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    applyUnitVisibility()
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }

        AlertDialog.Builder(ctx)
            .setTitle("Add Ingredient")
            .setView(scrollView)
            .setPositiveButton("Add", null)
            .setNegativeButton("Cancel", null)
            .showStyledDialog(ctx) { dialog ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val name = etName.text.toString().trim()
                    if (name.isBlank()) {
                        etName.error = getString(R.string.inventory_field_required)
                        return@setOnClickListener
                    }

                    val duplicate = viewModel.getIngredientOptionsForEditor()
                        .any { it.name.equals(name, ignoreCase = true) }
                    if (duplicate) {
                        etName.error = "An ingredient named \"$name\" already exists."
                        return@setOnClickListener
                    }

                    val selectedCategory = categoryOptions
                        .getOrNull(categorySpinner.selectedItemPosition) ?: "Pantry"
                    val unit = selectedUnit(unitSpinner)
                    val isMl = unit == IngredientUnits.ML
                    val pricePerPiece = etPricePerPiece.text.toString().toDoubleOrNull() ?: 0.0
                    val currentStock = etCurrentStockPieces.text.toString().toDoubleOrNull() ?: 0.0

                    val mlPerServing = if (isMl) {
                        etMlPerServing.text.toString().toDoubleOrNull()?.takeIf { it > 0.0 }
                    } else {
                        null
                    }
                    val mlPerBottle = if (isMl) {
                        etMlPerBottle.text.toString().toDoubleOrNull()?.takeIf { it > 0.0 }
                    } else {
                        null
                    }

                    when {
                        isMl && mlPerServing == null -> {
                            etMlPerServing.error = getString(R.string.inventory_field_required)
                            return@setOnClickListener
                        }

                        isMl && mlPerBottle == null -> {
                            etMlPerBottle.error = getString(R.string.inventory_field_required)
                            return@setOnClickListener
                        }
                    }

                    val storedCostPerUnit = if (isMl && mlPerServing != null && mlPerServing > 0.0) {
                        pricePerPiece / mlPerServing
                    } else {
                        pricePerPiece
                    }

                    val newIngredient = Ingredient(
                        id = viewModel.generateId(),
                        name = name,
                        category = selectedCategory,
                        unit = unit,
                        currentStock = currentStock,
                        minimumStock = 1.0,
                        costPerUnit = storedCostPerUnit,
                        lastRestocked = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            .format(Date()),
                        mlPerServing = mlPerServing,
                        mlPerBottle = mlPerBottle
                    )
                    viewModel.addIngredient(newIngredient)
                    setupFilterChips()
                    dialog.dismiss()
                }
            }
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
            .showStyledDialog(requireContext())
    }

    private fun showRestoreProductDialog(product: ProducibleProduct) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.inventory_restore_product))
            .setMessage(
                getString(
                    R.string.inventory_restore_confirmation,
                    product.name
                )
            )
            .setPositiveButton(R.string.inventory_restore_action) { _, _ ->
                viewModel.restoreProduct(product)
            }
            .setNegativeButton(R.string.inventory_dialog_cancel, null)
            .showStyledDialog(requireContext())
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
            .showStyledDialog(requireContext())
    }

    private fun showProductDialog(product: ProducibleProduct? = null) {
        val ctx = requireContext()
        val categoryOptions = viewModel.getProductCategoriesForEditor()
        val allIngredientOptions = viewModel.getIngredientOptionsForEditor()

        if (categoryOptions.isEmpty()) {
            showWarningDialog(requireContext(), getString(R.string.inventory_missing_categories))
            return
        }

        if (allIngredientOptions.isEmpty()) {
            showWarningDialog(requireContext(), getString(R.string.inventory_missing_ingredients))
            return
        }

        var currentIngredientOptions: List<Ingredient> = allIngredientOptions

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

        val imagePreview = ImageView(ctx).apply {
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_product_placeholder)
            contentDescription = getString(R.string.product_image)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        val imagePreviewParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(160)
        ).apply {
            topMargin = dpToPx(12)
        }
        container.addView(imagePreview, imagePreviewParams)

        val etImageUrl = createLabeledField(
            container = container,
            label = getString(R.string.inventory_field_product_image),
            value = product?.imageUrl.orEmpty(),
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
        )
        etImageUrl.doAfterTextChanged { text ->
            renderProductImagePreview(imagePreview, text?.toString().orEmpty())
        }
        renderProductImagePreview(imagePreview, product?.imageUrl.orEmpty())

        val chooseImageButton = MaterialButton(ctx).apply {
            text = getString(R.string.inventory_choose_product_image)
            isAllCaps = false
            setTextColor(ContextCompat.getColor(ctx, R.color.pos_secondary))
            icon = ContextCompat.getDrawable(ctx, R.drawable.ic_add_18)
            iconTint = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.pos_secondary))
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.pos_chip_bg))
            cornerRadius = dpToPx(14)
            insetTop = 0
            insetBottom = 0
            setOnClickListener {
                productImageUrlInput = etImageUrl
                productImagePreview = imagePreview
                pickProductImageLauncher.launch(arrayOf("image/*"))
            }
        }
        container.addView(
            chooseImageButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(44)
            ).apply {
                topMargin = dpToPx(8)
            }
        )

        val variantLabel = TextView(ctx).apply {
            text = getString(R.string.inventory_field_variant)
            textSize = 12f
            setTextColor(ContextCompat.getColor(ctx, R.color.pos_text_secondary))
            setPadding(0, dpToPx(12), 0, dpToPx(4))
        }
        container.addView(variantLabel)

        val variantRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        container.addView(variantRow)
        BEVERAGE_SIZE_VARIANTS.forEach { size ->
            variantRow.addView(
                TextView(ctx).apply {
                    text = size
                    textSize = 13f
                    setTextColor(ContextCompat.getColor(ctx, R.color.pos_text_primary))
                    setBackgroundResource(R.drawable.bg_hint_chip)
                    setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = dpToPx(8)
                }
            )
        }

        val priceLabel = TextView(ctx).apply {
            text = getString(R.string.inventory_field_price)
            textSize = 12f
            setTextColor(ContextCompat.getColor(ctx, R.color.pos_text_secondary))
            setPadding(0, dpToPx(8), 0, dpToPx(4))
        }
        container.addView(priceLabel)

        val priceDisplay = TextView(ctx).apply {
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.pos_text_primary))
            setBackgroundResource(R.drawable.bg_input_field)
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
        }
        container.addView(priceDisplay)

        val priceBreakdown = TextView(ctx).apply {
            textSize = 11f
            setTextColor(ContextCompat.getColor(ctx, R.color.pos_text_secondary))
            setPadding(0, dpToPx(4), 0, 0)
        }
        container.addView(priceBreakdown)

        fun selectedProductCategory(): ProductCategoryOption? {
            return categoryOptions.getOrNull(categorySpinner.selectedItemPosition)
        }

        fun renderVariantVisibility() {
            val shouldShowVariant = selectedProductCategory()
                ?.name
                .isBeverageCategoryName()
            variantLabel.visibility = if (shouldShowVariant) View.VISIBLE else View.GONE
            variantRow.visibility = if (shouldShowVariant) View.VISIBLE else View.GONE
        }

        renderVariantVisibility()

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

        fun computeRecipeCost(): Double {
            return rowHolders.sumOf { holder ->
                val ingredient = currentIngredientOptions
                    .getOrNull(holder.ingredientSpinner.selectedItemPosition)
                    ?: return@sumOf 0.0
                val qty = holder.quantityInput.text.toString().toDoubleOrNull()?.takeIf { it > 0.0 }
                    ?: return@sumOf 0.0
                qty * ingredient.costPerUnit
            }
        }

        fun refreshPriceDisplay() {
            val cost = computeRecipeCost()
            val price = cost * RecipePricing.MARKUP_MULTIPLIER
            priceDisplay.text = String.format(Locale.getDefault(), "PHP %,.2f", price)
            priceBreakdown.text = if (cost > 0.0) {
                String.format(
                    Locale.getDefault(),
                    "Recipe cost PHP %,.2f × %.0f markup",
                    cost,
                    RecipePricing.MARKUP_MULTIPLIER
                )
            } else {
                "Add ingredients to compute price"
            }
        }

        fun applyIngredientOptionsToSpinner(spinner: Spinner, options: List<Ingredient>) {
            spinner.adapter = ArrayAdapter(
                ctx,
                android.R.layout.simple_spinner_item,
                options.map(Ingredient::name)
            ).also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }

        fun addIngredientRow(initial: ProductRecipeIngredient? = null) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dpToPx(4), 0, dpToPx(4))
            }

            val ingredientSpinner = Spinner(ctx)
            applyIngredientOptionsToSpinner(ingredientSpinner, currentIngredientOptions)
            ingredientSpinner.onItemSelectedListener =
                object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: android.widget.AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        refreshPriceDisplay()
                    }

                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
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
                doAfterTextChanged { refreshPriceDisplay() }
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
                val selectedIndex = currentIngredientOptions.indexOfFirst {
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
                refreshPriceDisplay()
            }

            refreshPriceDisplay()
        }

        fun rebuildIngredientOptionsForCategory(extraIngredientIds: Set<String> = emptySet()) {
            val selectedName = selectedProductCategory()?.name
            val filtered = ProductIngredientFilter.filterForProductCategory(
                productCategoryName = selectedName,
                ingredients = allIngredientOptions
            )

            val previousSelectionIdByRow = rowHolders.map { holder ->
                currentIngredientOptions.getOrNull(holder.ingredientSpinner.selectedItemPosition)?.id
            }
            val rowSelectedIds = previousSelectionIdByRow.filterNotNull().toSet()
            val mustIncludeIds = extraIngredientIds + rowSelectedIds

            val newOptions = if (mustIncludeIds.isEmpty()) {
                filtered
            } else {
                val filteredIds = filtered.mapTo(mutableSetOf(), Ingredient::id)
                val extras = allIngredientOptions.filter {
                    it.id in mustIncludeIds && it.id !in filteredIds
                }
                filtered + extras
            }

            if (newOptions == currentIngredientOptions) return
            currentIngredientOptions = newOptions

            rowHolders.forEachIndexed { index, holder ->
                applyIngredientOptionsToSpinner(holder.ingredientSpinner, currentIngredientOptions)
                val previousId = previousSelectionIdByRow.getOrNull(index)
                val restoredIndex = previousId
                    ?.let { id -> currentIngredientOptions.indexOfFirst { it.id == id } }
                    ?: -1
                if (restoredIndex >= 0) {
                    holder.ingredientSpinner.setSelection(restoredIndex)
                }
            }

            refreshPriceDisplay()
        }

        rebuildIngredientOptionsForCategory(
            extraIngredientIds = existingRecipe.map(ProductRecipeIngredient::ingredientId).toSet()
        )

        if (existingRecipe.isEmpty()) {
            addIngredientRow()
        } else {
            existingRecipe.forEach(::addIngredientRow)
        }

        categorySpinner.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    renderVariantVisibility()
                    rebuildIngredientOptionsForCategory()
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
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
            dialog.applyZejiosCafeButtonStyling(ctx)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selectedCategory = selectedProductCategory()
                val isBeverageCategory = selectedCategory?.name.isBeverageCategoryName()
                val productName = etProductName.text.toString().trim()
                val variantName = when {
                    isBeverageCategory -> product?.variantName?.takeIf(String::isNotBlank)
                        ?: BEVERAGE_SIZE_VARIANTS.first()
                    product != null -> product.variantName
                    else -> STANDARD_VARIANT_NAME
                }
                val imageUrl = etImageUrl.text.toString().trim().takeIf(String::isNotBlank)
                val groupedIngredients = rowHolders.mapNotNull { holder ->
                    val ingredient = currentIngredientOptions.getOrNull(holder.ingredientSpinner.selectedItemPosition)
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

                val recipeCost = groupedIngredients.sumOf { ingredient ->
                    val unitCost = allIngredientOptions
                        .firstOrNull { it.id == ingredient.ingredientId }
                        ?.costPerUnit
                        ?: 0.0
                    ingredient.requiredQuantity * unitCost
                }
                val computedPrice = recipeCost * RecipePricing.MARKUP_MULTIPLIER

                when {
                    selectedCategory == null -> {
                        etProductName.error = getString(R.string.inventory_validation_category_required)
                    }

                    productName.isBlank() -> {
                        etProductName.error = getString(R.string.inventory_validation_product_name_required)
                    }

                    product == null && viewModel.hasProductWithName(productName) -> {
                        etProductName.error = "A product named \"$productName\" already exists."
                    }

                    groupedIngredients.isEmpty() -> {
                        showWarningDialog(
                            requireContext(),
                            getString(R.string.inventory_validation_ingredients_required)
                        )
                    }

                    computedPrice <= 0.0 -> {
                        showWarningDialog(
                            requireContext(),
                            "Set a per-unit cost on the recipe ingredients first so the price can be computed."
                        )
                    }

                    else -> {
                        val draft = ProductEditorDraft(
                            productId = product?.productId,
                            productVariantId = product?.id,
                            categoryId = selectedCategory.id,
                            productName = productName,
                            variantName = variantName,
                            price = computedPrice,
                            ingredients = groupedIngredients,
                            imageUrl = imageUrl,
                            createDefaultBeverageSizes = product == null && isBeverageCategory
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

        dialog.setOnDismissListener {
            if (productImageUrlInput == etImageUrl) {
                productImageUrlInput = null
                productImagePreview = null
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

    private fun renderProductImagePreview(imageView: ImageView, imageUrl: String) {
        RemoteImageLoader.load(
            imageView = imageView,
            imageUrl = imageUrl.trim(),
            fallbackResId = android.R.drawable.ic_menu_gallery
        )
    }

    private fun String?.isBeverageCategoryName(): Boolean {
        val normalized = this
            .orEmpty()
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]"), "")

        return normalized in BEVERAGE_CATEGORY_KEYS
    }

    private fun formatCurrency(value: Double): String {
        return String.format(Locale.getDefault(), "PHP %,.2f", value)
    }

    private fun notifyLowStockIfNeeded(list: List<Ingredient>) {
        val lowStockItems = list.filter { it.stockStatus == IngredientStockStatus.LOW_STOCK }
        val healthyIds = list.map(Ingredient::id).toSet() - lowStockItems.map(Ingredient::id).toSet()
        notifiedLowStockIds.removeAll(healthyIds)

        val freshlyLow = lowStockItems.filter { it.id !in notifiedLowStockIds }
        if (freshlyLow.isEmpty()) return

        val names = freshlyLow.joinToString(", ") { it.name }
        showWarningDialog(
            requireContext(),
            getString(R.string.inventory_low_stock_alert, names)
        )
        notifiedLowStockIds.addAll(freshlyLow.map(Ingredient::id))
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

    private fun formatServingCount(servings: Double): String {
        return if (servings % 1.0 == 0.0) {
            servings.toInt().toString()
        } else {
            String.format(Locale.getDefault(), "%.1f", servings)
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
        const val STANDARD_VARIANT_NAME = "Standard"
        val BEVERAGE_SIZE_VARIANTS = listOf("16oz", "22oz")
        val BEVERAGE_CATEGORY_KEYS = setOf(
            "milktea",
            "fruittea",
            "cremachee",
            "coffee",
            "noncoffee",
            "brevecoffee",
            "specialdrinks",
            "specialdrink",
            "coffeefrappes",
            "coffeefrappe",
            "noncoffeefrappes",
            "noncoffeefrappe",
            "lemonades",
            "lemonade",
            "smoothies",
            "smoothie"
        )
    }

    private data class ProductIngredientRowHolder(
        val container: View,
        val ingredientSpinner: Spinner,
        val quantityInput: EditText
    )
}
