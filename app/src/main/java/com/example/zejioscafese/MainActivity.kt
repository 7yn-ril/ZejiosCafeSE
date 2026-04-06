// app/src/main/java/com/example/zejioscafese/MainActivity.kt
package com.example.zejioscafese

import android.app.AlertDialog
import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import androidx.core.widget.doAfterTextChanged
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.example.zejioscafese.dashboard.model.AlertLevel
import com.example.zejioscafese.dashboard.model.DashboardPeriod
import com.example.zejioscafese.dashboard.model.DashboardSnapshot
import com.example.zejioscafese.dashboard.presentation.DashboardViewModel
import com.example.zejioscafese.dashboard.ui.DashboardAlertAdapter
import com.example.zejioscafese.dashboard.ui.DashboardInsightAdapter
import com.example.zejioscafese.dashboard.ui.DashboardTopItemAdapter
import com.example.zejioscafese.databinding.ActivityMainBinding
import com.example.zejioscafese.orders.data.repository.OrderRepository
import com.example.zejioscafese.orders.model.CafeOrder
import com.example.zejioscafese.orders.model.CafeOrderStatus
import com.example.zejioscafese.orders.ui.OrderManagementAdapter
import com.example.zejioscafese.pos.data.model.Product
import com.example.zejioscafese.pos.presentation.PosViewModel
import com.example.zejioscafese.pos.ui.CategoryAdapter
import com.example.zejioscafese.pos.ui.OrderItemAdapter
import com.example.zejioscafese.pos.ui.ProductAdapter
import com.example.zejioscafese.ui.InventoryFragment
import com.example.zejioscafese.ui.NavigationHost
import com.example.zejioscafese.ui.ReportsFragment
import com.example.zejioscafese.ui.Screen
import com.google.android.material.button.MaterialButton
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), NavigationHost {

    private enum class Section(
        @StringRes val labelRes: Int,
        @StringRes val titleRes: Int,
        @StringRes val subtitleRes: Int
    ) {
        DASHBOARD(R.string.dashboard, R.string.dashboard_title, R.string.dashboard_subtitle),
        POS(R.string.pos, R.string.pos_title, R.string.pos_subtitle),
        ORDERS(R.string.orders, R.string.orders_title, R.string.orders_subtitle),
        INVENTORY(R.string.inventory, R.string.inventory_title, R.string.inventory_subtitle),
        REPORTS(R.string.reports, R.string.reports_title, R.string.reports_subtitle),
        STAFF(R.string.staff, R.string.staff_title, R.string.staff_subtitle),
        PROFILE(R.string.profile, R.string.profile_title, R.string.profile_subtitle),
        SETTINGS(R.string.settings, R.string.settings_title, R.string.settings_subtitle)
    }

    private data class SidebarItem(
        val section: Section,
        val row: LinearLayout,
        val icon: ImageView,
        val label: TextView
    )

    private data class StaffCardViews(
        val nameView: TextView,
        val idView: TextView,
        val roleView: TextView,
        val shiftView: TextView,
        val statusView: TextView
    )

    private data class UserProfileState(
        var name: String,
        var email: String,
        var role: String,
        var phone: String,
        var address: String
    )

    private data class ReceiptLine(
        val label: String,
        val quantity: Int,
        val lineTotal: Double
    )

    private data class PendingCheckoutReceipt(
        val customerName: String?,
        val cashReceived: Double,
        val subtotal: Double,
        val total: Double,
        val lines: List<ReceiptLine>
    )

    private lateinit var binding: ActivityMainBinding
    private val viewModel: PosViewModel by viewModels()
    private val dashboardViewModel: DashboardViewModel by viewModels()

    private lateinit var productAdapter: ProductAdapter
    private lateinit var orderItemAdapter: OrderItemAdapter
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var dashboardInsightAdapter: DashboardInsightAdapter
    private lateinit var dashboardTopItemAdapter: DashboardTopItemAdapter
    private lateinit var dashboardAlertAdapter: DashboardAlertAdapter
    private lateinit var orderManagementAdapter: OrderManagementAdapter
    private lateinit var userProfileState: UserProfileState
    private val orders = mutableListOf<CafeOrder>()
    private val orderRepository = OrderRepository()
    private var selectedOrderStatus: CafeOrderStatus? = null
    private var orderSearchQuery: String = ""
    private val staffCards = mutableListOf<StaffCardViews>()
    private var hasCheckoutItems: Boolean = false
    private var isCheckoutSaving: Boolean = false
    private var pendingCheckoutReceipt: PendingCheckoutReceipt? = null
    private var dashboardSnapshot: DashboardSnapshot = DashboardSnapshot.empty()
    private var selectedDashboardPeriod: DashboardPeriod = DashboardPeriod.DAILY

    private var isSidebarExpanded: Boolean = true
    private var currentSection: Section = Section.POS
    private var isCheckoutExpanded: Boolean = false
    private var checkoutExpandedGuidePercent: Float = 0.70f
    private var checkoutAnimator: ValueAnimator? = null

    companion object {
        private const val STATE_CURRENT_SECTION = "current_section"
        private const val STATE_CHECKOUT_EXPANDED = "checkout_expanded"
        private const val CHECKOUT_COLLAPSED_GUIDE_PERCENT = 1f
        private const val SIDEBAR_LOGO_ASSET_PATH = "other_assets/ZejiosCafeLogo.jpg"
    }

    private val sidebarTextViews by lazy {
        listOf<View>(
            binding.tvSidebarTitle,
            binding.tvSidebarSubtitle,
            binding.tvSidebarDashboard,
            binding.tvSidebarPos,
            binding.tvSidebarOrders,
            binding.tvSidebarInventory,
            binding.tvSidebarReports,
            binding.tvSidebarStaff,
            binding.tvSidebarSettings,
            binding.tvProfileName,
            binding.tvProfileEmail
        )
    }

    private val sidebarItems by lazy {
        listOf(
            SidebarItem(Section.DASHBOARD, binding.itemDashboard, binding.ivSidebarDashboard, binding.tvSidebarDashboard),
            SidebarItem(Section.POS, binding.itemPos, binding.ivSidebarPos, binding.tvSidebarPos),
            SidebarItem(Section.ORDERS, binding.itemOrders, binding.ivSidebarOrders, binding.tvSidebarOrders),
            SidebarItem(Section.INVENTORY, binding.itemInventory, binding.ivSidebarInventory, binding.tvSidebarInventory),
            SidebarItem(Section.REPORTS, binding.itemReports, binding.ivSidebarReports, binding.tvSidebarReports),
            SidebarItem(Section.STAFF, binding.itemStaff, binding.ivSidebarStaff, binding.tvSidebarStaff),
            SidebarItem(Section.SETTINGS, binding.itemSettings, binding.ivSidebarSettings, binding.tvSidebarSettings)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isSidebarExpanded = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        captureCheckoutExpandedGuidePercent()
        isCheckoutExpanded = savedInstanceState?.getBoolean(STATE_CHECKOUT_EXPANDED) ?: false
        userProfileState = UserProfileState(
            name = getString(R.string.profile_name),
            email = getString(R.string.profile_email),
            role = getString(R.string.profile_role),
            phone = getString(R.string.profile_phone_value),
            address = getString(R.string.profile_address_value)
        )

        setupRecyclerViews()
        setupDashboard()
        setupOrders()
        setupSidebar()
        setupInteractions()
        setupStaffInteractions()
        setupProfileInteractions()
        observeViewModel()
        observeDashboardViewModel()
        val initialSection = savedInstanceState
            ?.getInt(STATE_CURRENT_SECTION)
            ?.let { restoredOrdinal -> Section.entries.getOrNull(restoredOrdinal) }
            ?: Section.POS
        renderSection(initialSection)
        loadSidebarLogo()
        applySidebarState(isSidebarExpanded, animate = false)
        configureCashOnlyCheckout()
        viewModel.setPaymentMethod(PosViewModel.PaymentMethod.CASH)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_CURRENT_SECTION, currentSection.ordinal)
        outState.putBoolean(STATE_CHECKOUT_EXPANDED, isCheckoutExpanded)
    }

    private fun setupRecyclerViews() {
        categoryAdapter = CategoryAdapter(onCategoryClick = viewModel::selectCategory)
        binding.rvCategories.apply {
            adapter = categoryAdapter
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
        }
        updatePosCategoryChipMode(compact = false)
        updatePosCategoryStripPadding(expanded = false)

        productAdapter = ProductAdapter(onCardClick = ::handleProductCardClick)
        binding.rvProducts.apply {
            val spanCount = resources.getInteger(R.integer.product_grid_span_count)
            adapter = productAdapter
            layoutManager = GridLayoutManager(this@MainActivity, spanCount)
            itemAnimator = null
            if (itemDecorationCount == 0) {
                addItemDecoration(GridSpacingItemDecoration(resources.getDimensionPixelSize(R.dimen.product_grid_spacing)))
            }
        }

        orderItemAdapter = OrderItemAdapter(
            onIncreaseClick = viewModel::increaseOrderItem,
            onDecreaseClick = viewModel::decreaseOrderItem
        )
        binding.rvOrderItems.apply {
            adapter = orderItemAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
            itemAnimator = null
            if (itemDecorationCount == 0) {
                addItemDecoration(VerticalSpaceItemDecoration(resources.getDimensionPixelSize(R.dimen.order_list_spacing)))
            }
        }
    }

    private fun setupDashboard() {
        dashboardInsightAdapter = DashboardInsightAdapter()
        dashboardTopItemAdapter = DashboardTopItemAdapter()
        dashboardAlertAdapter = DashboardAlertAdapter()

        binding.dashboardContent.rvInsights.apply {
            adapter = dashboardInsightAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
            itemAnimator = null
            if (itemDecorationCount == 0) {
                addItemDecoration(VerticalSpaceItemDecoration(resources.getDimensionPixelSize(R.dimen.order_list_spacing)))
            }
        }

        binding.dashboardContent.rvTopItems.apply {
            adapter = dashboardTopItemAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
            itemAnimator = null
            isNestedScrollingEnabled = false
            if (itemDecorationCount == 0) {
                addItemDecoration(VerticalSpaceItemDecoration(resources.getDimensionPixelSize(R.dimen.order_list_spacing)))
            }
        }

        binding.dashboardContent.rvAlerts.apply {
            adapter = dashboardAlertAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
            itemAnimator = null
            isNestedScrollingEnabled = false
            if (itemDecorationCount == 0) {
                addItemDecoration(VerticalSpaceItemDecoration(resources.getDimensionPixelSize(R.dimen.order_list_spacing)))
            }
        }

        bindDashboardSnapshot(dashboardSnapshot)
        setupDashboardChart()
        setupDashboardToggle()
    }

    private fun setupOrders() {
        orderManagementAdapter = OrderManagementAdapter(
            onOrderItemsClick = ::showOrderItemsDialog
        )

        binding.ordersContent.rvOrders.apply {
            adapter = orderManagementAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
            itemAnimator = null
            isNestedScrollingEnabled = false
        }

        orders.clear()
        loadOrdersFromSupabase()

        binding.ordersContent.etOrderSearch.doAfterTextChanged { text ->
            orderSearchQuery = text?.toString().orEmpty()
            applyOrderFilters()
        }

        binding.ordersContent.chipAllOrders.setOnClickListener {
            selectedOrderStatus = null
            applyOrderFilters()
        }

        binding.ordersContent.chipPendingOrders.setOnClickListener {
            selectedOrderStatus = CafeOrderStatus.PENDING
            applyOrderFilters()
        }

        binding.ordersContent.chipPreparingOrders.setOnClickListener {
            selectedOrderStatus = CafeOrderStatus.PREPARING
            applyOrderFilters()
        }

        binding.ordersContent.chipCompletedOrders.setOnClickListener {
            selectedOrderStatus = CafeOrderStatus.COMPLETED
            applyOrderFilters()
        }

        binding.ordersContent.btnNewOrder.setOnClickListener {
            showNewOrderDialog()
        }

        applyOrderFilters()
    }

    private fun loadOrdersFromSupabase(showError: Boolean = false) {
        lifecycleScope.launch {
            try {
                val fetchedOrders = orderRepository.fetchOrders()
                orders.clear()
                orders.addAll(fetchedOrders)
                applyOrderFilters()
            } catch (exception: Exception) {
                if (showError) {
                    Snackbar.make(
                        binding.root,
                        getString(
                            R.string.orders_load_failed,
                            exception.message ?: "Please try again."
                        ),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun handleProductCardClick(product: Product) {
        viewModel.increaseProduct(product)
        if (currentSection != Section.POS) {
            renderSection(Section.POS)
        }
        setCheckoutExpanded(expanded = true, animate = true)
    }

    private fun addOrReplaceOrder(order: CafeOrder) {
        orders.removeAll { it.id == order.id }
        orders.add(0, order)
        applyOrderFilters()
    }

    private fun showOrderItemsDialog(order: CafeOrder) {
        val itemsToShow = order.orderedItems.ifEmpty { listOf(order.itemsSummary) }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.order_items_dialog_title, order.id))
            .setItems(itemsToShow.toTypedArray(), null)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showCheckoutReviewDialog() {
        val orderItems = viewModel.orderItems.value.orEmpty()
        if (orderItems.isEmpty()) {
            Snackbar.make(
                binding.root,
                getString(R.string.checkout_requires_items),
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        val subtotal = viewModel.subtotal.value ?: 0.0
        val total = viewModel.total.value ?: subtotal
        val receiptLines = orderItems.map { item ->
            ReceiptLine(
                label = item.product.name,
                quantity = item.quantity,
                lineTotal = item.lineTotal
            )
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp(), 20.dp(), 24.dp(), 12.dp())
        }

        content.addView(
            createDialogText(
                text = getString(R.string.checkout_review_caption),
                textSizeSp = 14f,
                textColorRes = R.color.pos_text_secondary
            )
        )

        content.addView(createSectionLabel(getString(R.string.order_number_label)))
        content.addView(
            createDialogText(
                text = viewModel.orderNumber.value.orEmpty(),
                textSizeSp = 18f,
                typeface = Typeface.DEFAULT_BOLD
            )
        )

        content.addView(createSectionLabel(getString(R.string.checkout_review_items_title)))
        receiptLines.forEach { line ->
            content.addView(
                createDialogText(
                    text = formatReceiptLine(line),
                    textSizeSp = 14f,
                    typeface = Typeface.MONOSPACE
                )
            )
        }

        content.addView(createSectionLabel(getString(R.string.subtotal)))
        content.addView(createDialogText(formatCurrency(subtotal), textSizeSp = 16f))
        content.addView(createSectionLabel(getString(R.string.total)))
        content.addView(
            createDialogText(
                text = formatCurrency(total),
                textSizeSp = 22f,
                typeface = Typeface.DEFAULT_BOLD
            )
        )

        val customerNameInput = createDialogInput(
            hint = getString(R.string.checkout_customer_name_hint),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        )
        content.addView(createSectionLabel(getString(R.string.order_field_customer_name)))
        content.addView(customerNameInput)

        val paymentInput = createDialogInput(
            hint = getString(R.string.checkout_cash_received_hint),
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        )
        content.addView(createSectionLabel(getString(R.string.cash)))
        content.addView(paymentInput)

        val paymentHelper = createDialogText(
            text = getString(R.string.checkout_change_due_pending),
            textSizeSp = 13f,
            textColorRes = R.color.pos_text_secondary
        )
        content.addView(paymentHelper)

        val scrollView = ScrollView(this).apply {
            addView(content)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.checkout_review_title))
            .setView(scrollView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.checkout_confirm_payment, null)
            .create()

        dialog.setOnShowListener {
            val confirmButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

            fun refreshPaymentState() {
                val cashReceived = paymentInput.text?.toString().orEmpty().toCashAmount()
                val change = cashReceived?.minus(total)
                val isValid = cashReceived != null && change != null && change >= 0

                confirmButton.isEnabled = isValid && !isCheckoutSaving
                paymentHelper.text = when {
                    cashReceived == null -> getString(R.string.checkout_change_due_pending)
                    change == null || change < 0 -> getString(R.string.checkout_cash_required)
                    else -> getString(R.string.checkout_change_due, formatCurrency(change))
                }
            }

            paymentInput.doAfterTextChanged { refreshPaymentState() }
            refreshPaymentState()

            confirmButton.setOnClickListener {
                val cashReceived = paymentInput.text?.toString().orEmpty().toCashAmount()
                if (cashReceived == null || cashReceived < total) {
                    refreshPaymentState()
                    return@setOnClickListener
                }

                pendingCheckoutReceipt = PendingCheckoutReceipt(
                    customerName = customerNameInput.text?.toString()?.trim()?.takeIf(String::isNotBlank),
                    cashReceived = cashReceived,
                    subtotal = subtotal,
                    total = total,
                    lines = receiptLines
                )

                dialog.dismiss()
                viewModel.checkout(customerName = customerNameInput.text?.toString())
            }
        }

        dialog.show()
    }

    private fun showReceiptDialog(order: CafeOrder, receipt: PendingCheckoutReceipt) {
        val customerName = receipt.customerName?.takeIf(String::isNotBlank)
            ?: order.customerName.ifBlank { getString(R.string.receipt_walk_in_customer) }
        val change = (receipt.cashReceived - receipt.total).coerceAtLeast(0.0)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp(), 20.dp(), 24.dp(), 12.dp())
        }

        content.addView(
            createDialogText(
                text = getString(R.string.receipt_subtitle),
                textSizeSp = 14f,
                textColorRes = R.color.pos_text_secondary
            )
        )

        content.addView(createSectionLabel(getString(R.string.receipt_order_label)))
        content.addView(createDialogText(order.id, textSizeSp = 18f, typeface = Typeface.DEFAULT_BOLD))
        content.addView(createSectionLabel(getString(R.string.receipt_customer_label)))
        content.addView(createDialogText(customerName, textSizeSp = 16f))
        content.addView(createSectionLabel(getString(R.string.receipt_time_label)))
        content.addView(createDialogText(order.timeLabel, textSizeSp = 16f))
        content.addView(createSectionLabel(getString(R.string.receipt_payment_method_label)))
        content.addView(createDialogText(getString(R.string.cash), textSizeSp = 16f))
        content.addView(createSectionLabel(getString(R.string.items_label)))
        receipt.lines.forEach { line ->
            content.addView(
                createDialogText(
                    text = formatReceiptLine(line),
                    textSizeSp = 14f,
                    typeface = Typeface.MONOSPACE
                )
            )
        }
        content.addView(createSectionLabel(getString(R.string.subtotal)))
        content.addView(createDialogText(formatCurrency(receipt.subtotal), textSizeSp = 16f))
        content.addView(createSectionLabel(getString(R.string.total)))
        content.addView(
            createDialogText(
                text = formatCurrency(receipt.total),
                textSizeSp = 20f,
                typeface = Typeface.DEFAULT_BOLD
            )
        )
        content.addView(createSectionLabel(getString(R.string.receipt_cash_received_label)))
        content.addView(createDialogText(formatCurrency(receipt.cashReceived), textSizeSp = 16f))
        content.addView(createSectionLabel(getString(R.string.receipt_change_label)))
        content.addView(
            createDialogText(
                text = formatCurrency(change),
                textSizeSp = 18f,
                typeface = Typeface.DEFAULT_BOLD
            )
        )

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.receipt_title))
            .setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton(R.string.receipt_done, null)
            .show()
    }

    private fun createSectionLabel(text: String): TextView {
        return createDialogText(
            text = text,
            textSizeSp = 12f,
            typeface = Typeface.DEFAULT_BOLD,
            textColorRes = R.color.pos_text_secondary,
            topMarginDp = 16
        )
    }

    private fun createDialogInput(hint: String, inputType: Int): EditText {
        return EditText(this).apply {
            this.hint = hint
            this.inputType = inputType
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pos_text_primary))
            setHintTextColor(ContextCompat.getColor(this@MainActivity, R.color.pos_text_secondary))
            background = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.edit_text)
            setPadding(16.dp(), 14.dp(), 16.dp(), 14.dp())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 6.dp()
            }
        }
    }

    private fun createDialogText(
        text: String,
        textSizeSp: Float,
        typeface: Typeface = Typeface.DEFAULT,
        textColorRes: Int = R.color.pos_text_primary,
        topMarginDp: Int = 0
    ): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = textSizeSp
            setTypeface(typeface)
            setTextColor(ContextCompat.getColor(this@MainActivity, textColorRes))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = topMarginDp.dp()
            }
        }
    }

    private fun formatReceiptLine(line: ReceiptLine): String {
        return "${line.quantity} x ${line.label}  ${formatCurrency(line.lineTotal)}"
    }

    private fun String.toCashAmount(): Double? {
        return replace(",", "").trim().toDoubleOrNull()
    }

    private fun formatCurrency(amount: Double): String {
        return getString(R.string.currency_format, amount)
    }

    private fun applyOrderFilters() {
        val normalizedQuery = orderSearchQuery.trim().lowercase(Locale.getDefault())

        val filteredOrders = orders.filter { order ->
            val matchesStatus = selectedOrderStatus == null || order.status == selectedOrderStatus
            val matchesQuery = normalizedQuery.isBlank() || listOf(
                order.id,
                order.customerName,
                order.itemsSummary,
                order.tableLabel,
                order.orderedItems.joinToString(" ")
            ).joinToString(" ").lowercase(Locale.getDefault()).contains(normalizedQuery)
            matchesStatus && matchesQuery
        }

        orderManagementAdapter.submitList(filteredOrders.toList())

        val start = if (filteredOrders.isEmpty()) 0 else 1
        binding.ordersContent.tvOrdersShowing.text = getString(
            R.string.showing_orders_range,
            start,
            filteredOrders.size,
            orders.size
        )

        updateOrderStatusCounts()
        updateOrderStatusChipStyles()
    }

    private fun updateOrderStatusCounts() {
        binding.ordersContent.tvAllOrdersCount.text = orders.size.toString()
        binding.ordersContent.tvPendingOrdersCount.text =
            orders.count { it.status == CafeOrderStatus.PENDING }.toString()
        binding.ordersContent.tvPreparingOrdersCount.text =
            orders.count { it.status == CafeOrderStatus.PREPARING }.toString()
        binding.ordersContent.tvCompletedOrdersCount.text =
            orders.count { it.status == CafeOrderStatus.COMPLETED }.toString()
    }

    private fun updateOrderStatusChipStyles() {
        setOrderChipState(
            chip = binding.ordersContent.chipAllOrders,
            countView = binding.ordersContent.tvAllOrdersCount,
            selected = selectedOrderStatus == null,
            inactiveTextColorRes = R.color.pos_text_secondary
        )

        setOrderChipState(
            chip = binding.ordersContent.chipPendingOrders,
            countView = binding.ordersContent.tvPendingOrdersCount,
            selected = selectedOrderStatus == CafeOrderStatus.PENDING,
            inactiveTextColorRes = R.color.pos_warning
        )

        setOrderChipState(
            chip = binding.ordersContent.chipPreparingOrders,
            countView = binding.ordersContent.tvPreparingOrdersCount,
            selected = selectedOrderStatus == CafeOrderStatus.PREPARING,
            inactiveTextColorRes = R.color.pos_info
        )

        setOrderChipState(
            chip = binding.ordersContent.chipCompletedOrders,
            countView = binding.ordersContent.tvCompletedOrdersCount,
            selected = selectedOrderStatus == CafeOrderStatus.COMPLETED,
            inactiveTextColorRes = R.color.pos_secondary
        )
    }

    private fun setOrderChipState(
        chip: LinearLayout,
        countView: TextView,
        selected: Boolean,
        inactiveTextColorRes: Int
    ) {
        chip.setBackgroundResource(if (selected) R.drawable.bg_hint_chip else 0)
        countView.setBackgroundResource(if (selected) R.drawable.bg_sidebar_item_selected else R.drawable.bg_hint_chip)
        countView.setTextColor(
            ContextCompat.getColor(this, if (selected) R.color.white else inactiveTextColorRes)
        )
    }

    private fun showNewOrderDialog() {
        val availableProducts = viewModel.products.value.orEmpty().sortedBy { it.name }
        val selectedProductIds = linkedSetOf<String>()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(12), dpToPx(20), dpToPx(4))
        }

        val etCustomerName = createStaffLabeledField(
            container,
            getString(R.string.order_field_customer_name),
            ""
        )
        val etTableNumber = createStaffLabeledField(
            container,
            getString(R.string.order_field_table_number),
            "",
            android.text.InputType.TYPE_CLASS_NUMBER
        )

        val tvItemsPreview = createLabeledReadOnlyValue(
            container,
            getString(R.string.order_field_items_summary),
            getString(R.string.order_no_products_selected)
        )

        val btnSelectProducts = MaterialButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(8)
            }
            text = getString(R.string.order_select_products)
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            cornerRadius = dpToPx(12)
            strokeWidth = dpToPx(1)
            strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.pos_border))
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.pos_surface))
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pos_text_primary))
        }
        container.addView(btnSelectProducts)

        val tvTotalPreview = createLabeledReadOnlyValue(
            container,
            getString(R.string.order_field_total_amount),
            getString(R.string.currency_format, 0.0)
        )

        val etStatus = createStaffLabeledField(
            container,
            getString(R.string.order_field_status),
            getString(R.string.pending)
        )

        fun selectedProducts(): List<Product> {
            return availableProducts.filter { selectedProductIds.contains(it.id) }
        }

        fun refreshSelectionPreview() {
            val products = selectedProducts()
            tvItemsPreview.text = if (products.isEmpty()) {
                getString(R.string.order_no_products_selected)
            } else {
                products.joinToString(", ") { it.name }
            }
            val total = products.sumOf { it.price }
            tvTotalPreview.text = getString(R.string.currency_format, total)
            btnSelectProducts.text = if (products.isEmpty()) {
                getString(R.string.order_select_products)
            } else {
                getString(R.string.order_select_products_count, products.size)
            }
        }

        btnSelectProducts.setOnClickListener {
            if (availableProducts.isEmpty()) {
                Snackbar.make(
                    binding.root,
                    getString(R.string.order_no_products_available),
                    Snackbar.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            showOrderProductPicker(
                availableProducts = availableProducts,
                selectedProductIds = selectedProductIds,
                onSelectionSaved = { refreshSelectionPreview() }
            )
        }

        refreshSelectionPreview()

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.order_dialog_new_title))
            .setView(container)
            .setPositiveButton(getString(R.string.create_order), null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val customerName = etCustomerName.text.toString().trim()
                if (customerName.isBlank()) {
                    etCustomerName.error = getString(R.string.order_customer_required)
                    return@setOnClickListener
                }

                val chosenProducts = selectedProducts()
                if (chosenProducts.isEmpty()) {
                    Snackbar.make(
                        binding.root,
                        getString(R.string.order_select_products_required),
                        Snackbar.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                val itemsSummary = chosenProducts.joinToString(", ") { it.name }
                val totalAmount = chosenProducts.sumOf { it.price }


                val newOrder = CafeOrder(
                    id = generateNextOrderId(),
                    customerName = customerName,
                    tableLabel = etTableNumber.text.toString().trim().ifBlank { "1" },
                    itemsSummary = itemsSummary,
                    itemCount = chosenProducts.size,
                    timeLabel = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()),
                    status = parseOrderStatus(etStatus.text.toString()),
                    total = totalAmount,
                    initials = extractInitials(customerName)
                )

                orders.add(0, newOrder)
                selectedOrderStatus = null
                orderSearchQuery = ""
                binding.ordersContent.etOrderSearch.setText("")
                applyOrderFilters()

                Snackbar.make(
                    binding.root,
                    getString(R.string.order_created_message, newOrder.id),
                    Snackbar.LENGTH_SHORT
                ).show()

                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showOrderProductPicker(
        availableProducts: List<Product>,
        selectedProductIds: MutableSet<String>,
        onSelectionSaved: () -> Unit
    ) {
        val productLabels = availableProducts.map { product ->
            getString(R.string.order_product_picker_entry, product.name, product.price)
        }.toTypedArray()

        val checkedItems = availableProducts
            .map { selectedProductIds.contains(it.id) }
            .toBooleanArray()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.order_product_picker_title))
            .setMultiChoiceItems(productLabels, checkedItems) { _, which, isChecked ->
                val productId = availableProducts[which].id
                if (isChecked) {
                    selectedProductIds.add(productId)
                } else {
                    selectedProductIds.remove(productId)
                }
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                onSelectionSaved()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun parseOrderStatus(value: String): CafeOrderStatus {
        val normalized = value.trim().lowercase(Locale.getDefault())
        return when {
            normalized.startsWith("prep") -> CafeOrderStatus.PREPARING
            normalized.startsWith("comp") || normalized.startsWith("done") -> CafeOrderStatus.COMPLETED
            else -> CafeOrderStatus.PENDING
        }
    }

    private fun generateNextOrderId(): String {
        val maxIdNumber = orders
            .mapNotNull { Regex("#ORD-(\\d+)").find(it.id)?.groupValues?.get(1)?.toIntOrNull() }
            .maxOrNull()
            ?: 1000

        return String.format(Locale.getDefault(), "#ORD-%04d", maxIdNumber + 1)
    }

    private fun extractInitials(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) {
            return "NA"
        }
        return parts.take(2).map { it.first().uppercaseChar() }.joinToString("")
    }

    private fun bindDashboardSnapshot(snapshot: DashboardSnapshot) {
        dashboardSnapshot = snapshot
        dashboardInsightAdapter.submitList(snapshot.insights)
        dashboardTopItemAdapter.submitList(snapshot.topItems)
        dashboardAlertAdapter.submitList(snapshot.alerts)
        bindDashboardMetrics(snapshot)
        bindDashboardFocus(snapshot.alerts)
        updateDashboardChart(selectedDashboardPeriod)
    }

    private fun bindDashboardFocus(alerts: List<com.example.zejioscafese.dashboard.model.DashboardAlert>) {
        val criticalCount = alerts.count { it.level == AlertLevel.CRITICAL }
        val warningCount = alerts.count { it.level == AlertLevel.WARNING }
        val priorityAlert = alerts.firstOrNull { it.level == AlertLevel.CRITICAL } ?: alerts.firstOrNull()

        binding.dashboardContent.tvActionCount.text = getString(
            R.string.dashboard_focus_badge_format,
            alerts.size
        )
        binding.dashboardContent.tvActionHeadline.text =
            priorityAlert?.title ?: getString(R.string.dashboard_focus_fallback_headline)
        binding.dashboardContent.tvActionSupport.text = getString(
            R.string.dashboard_focus_support_format,
            criticalCount,
            warningCount
        )
    }

    private fun bindDashboardMetrics(snapshot: DashboardSnapshot) {
        val dashboardRoot = binding.dashboardContent.root
        bindMetric(
            dashboardRoot.findViewById(R.id.tvMetricSalesValue),
            dashboardRoot.findViewById(R.id.tvMetricSalesDelta),
            snapshot.salesMetric.value,
            snapshot.salesMetric.delta,
            positive = snapshot.salesMetric.positive
        )
        bindMetric(
            dashboardRoot.findViewById(R.id.tvMetricOrdersValue),
            dashboardRoot.findViewById(R.id.tvMetricOrdersDelta),
            snapshot.ordersMetric.value,
            snapshot.ordersMetric.delta,
            positive = snapshot.ordersMetric.positive
        )
        bindMetric(
            dashboardRoot.findViewById(R.id.tvMetricProfitValue),
            dashboardRoot.findViewById(R.id.tvMetricProfitDelta),
            snapshot.profitMetric.value,
            snapshot.profitMetric.delta,
            positive = snapshot.profitMetric.positive
        )
        bindMetric(
            dashboardRoot.findViewById(R.id.tvMetricActiveOrdersValue),
            dashboardRoot.findViewById(R.id.tvMetricActiveOrdersDelta),
            snapshot.activeOrdersMetric.value,
            snapshot.activeOrdersMetric.delta,
            positive = snapshot.activeOrdersMetric.positive
        )
        bindMetric(
            dashboardRoot.findViewById(R.id.tvMetricLowStockValue),
            dashboardRoot.findViewById(R.id.tvMetricLowStockDelta),
            snapshot.lowStockMetric.value,
            snapshot.lowStockMetric.delta,
            positive = snapshot.lowStockMetric.positive
        )
    }

    private fun bindMetric(
        valueView: TextView,
        deltaView: TextView,
        value: String,
        delta: String,
        positive: Boolean
    ) {
        valueView.text = value
        deltaView.text = delta
        deltaView.setTextColor(
            ContextCompat.getColor(
                this,
                if (positive) R.color.pos_secondary else R.color.pos_badge
            )
        )
    }

    private fun setupDashboardChart() {
        binding.dashboardContent.chartSalesPerformance.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(false)
            setScaleEnabled(false)
            setPinchZoom(false)
            setNoDataText("")
            setViewPortOffsets(28f, 18f, 24f, 42f)
            axisRight.isEnabled = false
            axisLeft.apply {
                axisMinimum = 0f
                textColor = ContextCompat.getColor(this@MainActivity, R.color.pos_text_secondary)
                gridColor = ContextCompat.getColor(this@MainActivity, R.color.pos_border)
                setDrawAxisLine(false)
            }
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                textColor = ContextCompat.getColor(this@MainActivity, R.color.pos_text_secondary)
                gridColor = ContextCompat.getColor(this@MainActivity, R.color.pos_border)
                setDrawAxisLine(false)
                setDrawGridLines(false)
            }
        }

        updateDashboardChart(selectedDashboardPeriod)
    }

    private fun setupDashboardToggle() {
        binding.dashboardContent.togglePeriodGroup.check(binding.dashboardContent.btnChartDaily.id)
        binding.dashboardContent.togglePeriodGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            selectedDashboardPeriod = when (checkedId) {
                binding.dashboardContent.btnChartWeekly.id -> DashboardPeriod.WEEKLY
                binding.dashboardContent.btnChartMonthly.id -> DashboardPeriod.MONTHLY
                else -> DashboardPeriod.DAILY
            }
            updateDashboardChart(selectedDashboardPeriod)
            styleDashboardToggleButtons()
        }
        styleDashboardToggleButtons()
    }

    private fun updateDashboardChart(period: DashboardPeriod) {
        val points = dashboardSnapshot.charts[period].orEmpty()
        val entries = points.mapIndexed { index, point -> Entry(index.toFloat(), point.sales) }
        val labels = points.map { it.label }

        val lineColor = ContextCompat.getColor(this, R.color.pos_primary)
        val fillShade = ContextCompat.getColor(this, R.color.pos_chart_fill)

        val dataSet = LineDataSet(entries, getString(R.string.revenue)).apply {
            color = lineColor
            lineWidth = 3f
            setCircleColor(lineColor)
            circleRadius = 4f
            circleHoleRadius = 2f
            setCircleHoleColor(ContextCompat.getColor(this@MainActivity, R.color.white))
            setDrawValues(false)
            setDrawFilled(true)
            fillColor = fillShade
            fillAlpha = 110
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        binding.dashboardContent.chartSalesPerformance.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        binding.dashboardContent.chartSalesPerformance.data = LineData(dataSet)
        binding.dashboardContent.chartSalesPerformance.animateX(400)
        binding.dashboardContent.chartSalesPerformance.invalidate()
    }

    private fun styleDashboardToggleButtons() {
        val selectedBg = ContextCompat.getColor(this, R.color.pos_primary)
        val unselectedBg = ContextCompat.getColor(this, R.color.pos_surface_soft)
        val selectedText = ContextCompat.getColor(this, R.color.white)
        val unselectedText = ContextCompat.getColor(this, R.color.pos_text_primary)
        val stroke = ContextCompat.getColor(this, R.color.pos_border)

        listOf(
            binding.dashboardContent.btnChartDaily,
            binding.dashboardContent.btnChartWeekly,
            binding.dashboardContent.btnChartMonthly
        ).forEach { button ->
            val checked = binding.dashboardContent.togglePeriodGroup.checkedButtonId == button.id
            button.backgroundTintList = ColorStateList.valueOf(if (checked) selectedBg else unselectedBg)
            button.setTextColor(if (checked) selectedText else unselectedText)
            button.strokeColor = ColorStateList.valueOf(if (checked) selectedBg else stroke)
            button.strokeWidth = if (checked) 0 else resources.getDimensionPixelSize(R.dimen.payment_button_stroke_width)
        }
    }

    private fun setupSidebar() {
        binding.btnToggleSidebar.setOnClickListener {
            isSidebarExpanded = !isSidebarExpanded
            applySidebarState(isSidebarExpanded, animate = true)
        }

        sidebarItems.forEach { item ->
            item.row.setOnClickListener { renderSection(item.section) }
        }
    }

    private fun setupInteractions() {
        binding.etSearch.doAfterTextChanged { text ->
            viewModel.updateSearchQuery(text?.toString().orEmpty())
        }

        binding.btnFilterSort.setOnClickListener { showSortMenu(it) }

        binding.btnClear.setOnClickListener { viewModel.clearOrder() }
        binding.btnCheckout.setOnClickListener {
            if (viewModel.orderItems.value.isNullOrEmpty()) {
                Snackbar.make(
                    binding.root,
                    getString(R.string.checkout_requires_items),
                    Snackbar.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            showCheckoutReviewDialog()
        }
        binding.btnCloseCheckout.setOnClickListener {
            setCheckoutExpanded(expanded = false, animate = true)
        }
        binding.fabCart.setOnClickListener {
            if (currentSection != Section.POS) {
                renderSection(Section.POS)
            }
            setCheckoutExpanded(expanded = true, animate = true)
        }

        binding.btnCash.setOnClickListener {
            viewModel.setPaymentMethod(PosViewModel.PaymentMethod.CASH)
        }

        binding.avatar.setOnClickListener {
            showAvatarMenu(it)
        }
    }

    private fun setupStaffInteractions() {
        binding.staffContent.btnAddStaff.setOnClickListener {
            showStaffDialog(card = null)
        }

        val initialStaffCards = listOf(
            StaffCardViews(
                nameView = binding.staffContent.tvStaffNameOne,
                idView = binding.staffContent.tvStaffIdOne,
                roleView = binding.staffContent.tvStaffRoleOne,
                shiftView = binding.staffContent.tvStaffShiftOne,
                statusView = binding.staffContent.tvStaffStatusOne
            ),
            StaffCardViews(
                nameView = binding.staffContent.tvStaffNameTwo,
                idView = binding.staffContent.tvStaffIdTwo,
                roleView = binding.staffContent.tvStaffRoleTwo,
                shiftView = binding.staffContent.tvStaffShiftTwo,
                statusView = binding.staffContent.tvStaffStatusTwo
            ),
            StaffCardViews(
                nameView = binding.staffContent.tvStaffNameThree,
                idView = binding.staffContent.tvStaffIdThree,
                roleView = binding.staffContent.tvStaffRoleThree,
                shiftView = binding.staffContent.tvStaffShiftThree,
                statusView = binding.staffContent.tvStaffStatusThree
            )
        )

        staffCards.clear()
        staffCards.addAll(initialStaffCards)

        listOf(
            binding.staffContent.btnEditStaffOne,
            binding.staffContent.btnEditStaffTwo,
            binding.staffContent.btnEditStaffThree
        ).zip(initialStaffCards).forEach { (button, card) ->
            button.setOnClickListener {
                showStaffDialog(card)
            }
        }
    }

    private fun setupProfileInteractions() {
        binding.profileCard.setOnClickListener {
            renderSection(Section.PROFILE)
        }

        binding.profileContent.btnEditProfile.setOnClickListener {
            showEditProfileDialog()
        }

        binding.profileContent.btnEditProfileInline.setOnClickListener {
            showEditProfileDialog()
        }

        binding.profileContent.btnLogoutProfile.setOnClickListener {
            showLogoutConfirmationDialog()
        }

        applyUserProfileStateToUi()
    }

    private fun showAvatarMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, getString(R.string.profile_menu_view_profile))
            menu.add(0, 2, 1, getString(R.string.profile_menu_edit_profile))
            menu.add(0, 3, 2, getString(R.string.profile_menu_go_to_settings))
            menu.add(0, 4, 3, getString(R.string.logout))

            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        renderSection(Section.PROFILE)
                        true
                    }
                    2 -> {
                        renderSection(Section.PROFILE)
                        showEditProfileDialog()
                        true
                    }
                    3 -> {
                        renderSection(Section.SETTINGS)
                        true
                    }
                    4 -> {
                        showLogoutConfirmationDialog()
                        true
                    }
                    else -> false
                }
            }
        }.show()
    }

    private fun showEditProfileDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(12), dpToPx(20), dpToPx(4))
        }

        val etName = createStaffLabeledField(
            container,
            getString(R.string.staff_field_full_name),
            userProfileState.name
        )
        val etEmail = createStaffLabeledField(
            container,
            getString(R.string.profile_field_email),
            userProfileState.email,
            android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        )
        val etRole = createStaffLabeledField(
            container,
            getString(R.string.profile_field_role),
            userProfileState.role
        )
        val etPhone = createStaffLabeledField(
            container,
            getString(R.string.profile_field_phone),
            userProfileState.phone,
            android.text.InputType.TYPE_CLASS_PHONE
        )
        val etAddress = createStaffLabeledField(
            container,
            getString(R.string.profile_field_address),
            userProfileState.address
        )

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.profile_edit_dialog_title))
            .setView(container)
            .setPositiveButton(getString(R.string.profile_save_changes), null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = etName.text.toString().trim()
                val email = etEmail.text.toString().trim()

                if (name.isBlank()) {
                    etName.error = getString(R.string.profile_name_required)
                    return@setOnClickListener
                }

                if (!email.contains("@") || !email.contains(".")) {
                    etEmail.error = getString(R.string.profile_email_invalid)
                    return@setOnClickListener
                }

                userProfileState = userProfileState.copy(
                    name = name,
                    email = email,
                    role = etRole.text.toString().trim().ifBlank { userProfileState.role },
                    phone = etPhone.text.toString().trim().ifBlank { userProfileState.phone },
                    address = etAddress.text.toString().trim().ifBlank { userProfileState.address }
                )

                applyUserProfileStateToUi()

                Snackbar.make(
                    binding.root,
                    getString(R.string.profile_updated_message),
                    Snackbar.LENGTH_SHORT
                ).show()

                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.logout_confirm_title))
            .setMessage(getString(R.string.logout_confirm_message))
            .setPositiveButton(getString(R.string.logout)) { _, _ ->
                viewModel.clearOrder()
                selectedOrderStatus = null
                orderSearchQuery = ""
                if (::orderManagementAdapter.isInitialized) {
                    applyOrderFilters()
                }
                renderSection(Section.POS)
                Snackbar.make(
                    binding.root,
                    getString(R.string.logout_success_message),
                    Snackbar.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun applyUserProfileStateToUi() {
        binding.tvProfileName.text = userProfileState.name
        binding.tvProfileEmail.text = userProfileState.email

        binding.profileContent.tvProfilePageName.text = userProfileState.name
        binding.profileContent.tvProfilePageRole.text = userProfileState.role
        binding.profileContent.tvProfilePageEmail.text = userProfileState.email
        binding.profileContent.tvProfilePageEmailDetail.text = userProfileState.email
        binding.profileContent.tvProfilePagePhone.text = userProfileState.phone
        binding.profileContent.tvProfilePageAddress.text = userProfileState.address
        binding.profileContent.tvProfilePageRoleDetail.text = userProfileState.role

        binding.avatar.contentDescription = getString(R.string.profile_avatar_for, userProfileState.name)
    }

    private fun showStaffDialog(card: StaffCardViews?) {
        val isEditMode = card != null
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(12), dpToPx(20), dpToPx(4))
        }

        val etName = createStaffLabeledField(
            container,
            getString(R.string.staff_field_full_name),
            card?.nameView?.text?.toString().orEmpty()
        )
        val etEmployeeId = createStaffLabeledField(
            container,
            getString(R.string.staff_field_employee_id),
            card?.idView?.text?.toString()?.removePrefix("ID:")?.trim().orEmpty()
        )
        val etRole = createStaffLabeledField(
            container,
            getString(R.string.staff_field_role),
            card?.roleView?.text?.toString().orEmpty()
        )
        val etShift = createStaffLabeledField(
            container,
            getString(R.string.staff_field_shift),
            card?.shiftView?.text?.toString().orEmpty()
        )
        val etStatus = createStaffLabeledField(
            container,
            getString(R.string.staff_field_status),
            card?.statusView?.text?.toString().orEmpty()
        )

        AlertDialog.Builder(this)
            .setTitle(
                getString(
                    if (isEditMode) R.string.staff_dialog_edit_title else R.string.staff_dialog_add_title
                )
            )
            .setView(container)
            .setPositiveButton(
                getString(
                    if (isEditMode) R.string.staff_dialog_save_action else R.string.staff_dialog_add_action
                )
            ) { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isBlank()) {
                    return@setPositiveButton
                }

                if (isEditMode) {
                    val staffCard = card!!
                    staffCard.nameView.text = name

                    val enteredId = etEmployeeId.text.toString().trim()
                    if (enteredId.isNotBlank()) {
                        staffCard.idView.text = getString(
                            R.string.staff_id_format,
                            normalizeStaffEmployeeId(enteredId)
                        )
                    }

                    staffCard.roleView.text = etRole.text.toString().trim().ifBlank { staffCard.roleView.text.toString() }
                    staffCard.shiftView.text = etShift.text.toString().trim().ifBlank { staffCard.shiftView.text.toString() }
                    staffCard.statusView.text = etStatus.text.toString().trim().ifBlank { staffCard.statusView.text.toString() }

                    Snackbar.make(
                        binding.root,
                        getString(R.string.staff_updated_message, name),
                        Snackbar.LENGTH_SHORT
                    ).show()
                } else {
                    val newEmployeeId = normalizeStaffEmployeeId(
                        etEmployeeId.text.toString().trim().ifBlank { generateNextStaffEmployeeId() }
                    )
                    val role = etRole.text.toString().trim().ifBlank { getString(R.string.staff_role_barista) }
                    val shift = etShift.text.toString().trim().ifBlank { getString(R.string.staff_shift_three) }
                    val status = etStatus.text.toString().trim().ifBlank { getString(R.string.staff_status_active) }

                    addStaffCard(
                        name = name,
                        employeeId = newEmployeeId,
                        role = role,
                        shift = shift,
                        status = status
                    )

                    Snackbar.make(
                        binding.root,
                        getString(R.string.staff_added_message, name),
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun addStaffCard(
        name: String,
        employeeId: String,
        role: String,
        shift: String,
        status: String
    ) {
        val cardRoot = layoutInflater.inflate(
            R.layout.item_staff_profile_card,
            binding.staffContent.staffCardsContainer,
            false
        )

        val nameView = cardRoot.findViewById<TextView>(R.id.tvStaffName)
        val idView = cardRoot.findViewById<TextView>(R.id.tvStaffId)
        val roleView = cardRoot.findViewById<TextView>(R.id.tvStaffRole)
        val shiftView = cardRoot.findViewById<TextView>(R.id.tvStaffShift)
        val statusView = cardRoot.findViewById<TextView>(R.id.tvStaffStatus)
        val editButton = cardRoot.findViewById<MaterialButton>(R.id.btnEditStaff)

        nameView.text = name
        idView.text = getString(R.string.staff_id_format, employeeId)
        roleView.text = role
        shiftView.text = shift
        statusView.text = status

        val newCard = StaffCardViews(
            nameView = nameView,
            idView = idView,
            roleView = roleView,
            shiftView = shiftView,
            statusView = statusView
        )

        editButton.setOnClickListener {
            showStaffDialog(newCard)
        }

        binding.staffContent.staffCardsContainer.addView(cardRoot)
        staffCards.add(newCard)
    }

    private fun normalizeStaffEmployeeId(value: String): String {
        var normalized = value.trim()
        if (normalized.startsWith("ID:", ignoreCase = true)) {
            normalized = normalized.substringAfter(':').trim()
        }

        normalized = normalized.removePrefix("#")
        if (!normalized.startsWith("EMP-", ignoreCase = true)) {
            normalized = "EMP-$normalized"
        }

        return "#${normalized.uppercase(Locale.getDefault())}"
    }

    private fun generateNextStaffEmployeeId(): String {
        val nextNumber = staffCards
            .mapNotNull { card ->
                Regex("EMP-(\\d+)", RegexOption.IGNORE_CASE)
                    .find(card.idView.text.toString())
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
            }
            .maxOrNull()
            ?.plus(1)
            ?: 2401

        return "#EMP-$nextNumber"
    }

    private fun createStaffLabeledField(
        container: LinearLayout,
        label: String,
        value: String,
        inputType: Int = android.text.InputType.TYPE_CLASS_TEXT
    ): EditText {
        val labelView = TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pos_text_secondary))
            setPadding(0, dpToPx(8), 0, dpToPx(4))
        }
        container.addView(labelView)

        val editText = EditText(this).apply {
            setText(value)
            this.inputType = inputType
            textSize = 14f
            setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
            setBackgroundResource(R.drawable.bg_input_field)
        }
        container.addView(editText)
        return editText
    }

    private fun createLabeledReadOnlyValue(
        container: LinearLayout,
        label: String,
        value: String
    ): TextView {
        val labelView = TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pos_text_secondary))
            setPadding(0, dpToPx(8), 0, dpToPx(4))
        }
        container.addView(labelView)

        val valueView = TextView(this).apply {
            text = value
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pos_text_primary))
            setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
            setBackgroundResource(R.drawable.bg_input_field)
        }
        container.addView(valueView)
        return valueView
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun Int.dp(): Int {
        return dpToPx(this)
    }

    private fun observeViewModel() {
        viewModel.categories.observe(this) { categories ->
            categoryAdapter.submitList(categories)
        }

        viewModel.selectedCategory.observe(this) { selected ->
            categoryAdapter.selectedCategory = selected
        }

        viewModel.products.observe(this) { products ->
            productAdapter.submitList(products)
        }

        viewModel.orderQuantities.observe(this) { quantities ->
            productAdapter.submitQuantities(quantities)
        }

        viewModel.orderItems.observe(this) { items ->
            orderItemAdapter.submitList(items)
            val hasItems = items.isNotEmpty()
            hasCheckoutItems = hasItems
            binding.emptyOrderState.visibility = if (hasItems) View.GONE else View.VISIBLE
            binding.rvOrderItems.visibility = if (hasItems) View.VISIBLE else View.GONE
            updateCheckoutButtonState()
        }

        viewModel.orderNumber.observe(this) { orderNumber ->
            binding.tvOrderNumber.text = getString(R.string.order_number_format, orderNumber)
        }

        viewModel.subtotal.observe(this) { amount ->
            binding.tvSubtotal.text = getString(R.string.currency_format, amount)
        }

        viewModel.tax.observe(this) { amount ->
            binding.tvTax.text = getString(R.string.currency_format, amount)
        }

        viewModel.total.observe(this) { amount ->
            binding.tvTotal.text = getString(R.string.currency_format, amount)
        }

        viewModel.selectedPaymentMethod.observe(this) { paymentMethod ->
            applyPaymentSelection(paymentMethod)
        }

        viewModel.menuLoadError.observe(this) { errorMessage ->
            if (!errorMessage.isNullOrBlank()) {
                Snackbar.make(
                    binding.root,
                    errorMessage,
                    Snackbar.LENGTH_LONG
                ).show()
                viewModel.onMenuLoadErrorConsumed()
            }
        }

        viewModel.isCheckoutInProgress.observe(this) { isInProgress ->
            isCheckoutSaving = isInProgress
            updateCheckoutButtonState()
        }

        // One-shot checkout success event
        viewModel.checkoutEvent.observe(this) { savedOrder ->
            if (savedOrder != null) {
                val receipt = pendingCheckoutReceipt
                pendingCheckoutReceipt = null
                addOrReplaceOrder(savedOrder)
                loadOrdersFromSupabase(showError = false)
                viewModel.refreshMenu()
                dashboardViewModel.refreshDashboard()
                if (receipt != null) {
                    showReceiptDialog(savedOrder, receipt)
                } else {
                    Snackbar.make(
                        binding.root,
                        getString(R.string.checkout_saved_message, savedOrder.id),
                        Snackbar.LENGTH_LONG
                    ).show()
                }

                // Close and reset the cart panel after a successful checkout.
                // The cart data (items, subtotal, total) is already cleared by
                // viewModel.clearOrder() inside checkout(), so collapsing the
                // panel leaves it in its empty state for the next order.
                setCheckoutExpanded(expanded = false, animate = true)

                viewModel.onCheckoutEventConsumed()
            }
        }
        viewModel.checkoutError.observe(this) { errorMessage ->
            if (!errorMessage.isNullOrBlank()) {
                pendingCheckoutReceipt = null
                Snackbar.make(
                    binding.root,
                    getString(R.string.checkout_save_failed, errorMessage),
                    Snackbar.LENGTH_LONG
                ).show()
                viewModel.onCheckoutErrorConsumed()
            }
        }
    }

    private fun observeDashboardViewModel() {
        dashboardViewModel.dashboardSnapshot.observe(this) { snapshot ->
            bindDashboardSnapshot(snapshot)
        }

        dashboardViewModel.dashboardError.observe(this) { errorMessage ->
            if (!errorMessage.isNullOrBlank()) {
                Snackbar.make(
                    binding.root,
                    errorMessage,
                    Snackbar.LENGTH_LONG
                ).show()
                dashboardViewModel.onDashboardErrorConsumed()
            }
        }
    }

    private fun showSortMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, getString(R.string.sort_name_asc))
            menu.add(0, 2, 1, getString(R.string.sort_name_desc))
            menu.add(0, 3, 2, getString(R.string.sort_price_asc))
            menu.add(0, 4, 3, getString(R.string.sort_price_desc))
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> viewModel.setSortOption(PosViewModel.SortOption.NAME_ASC)
                    2 -> viewModel.setSortOption(PosViewModel.SortOption.NAME_DESC)
                    3 -> viewModel.setSortOption(PosViewModel.SortOption.PRICE_ASC)
                    4 -> viewModel.setSortOption(PosViewModel.SortOption.PRICE_DESC)
                }
                true
            }
        }.show()
    }

    override fun navigateTo(screen: Screen) {
        val section = when (screen) {
            Screen.DASHBOARD -> Section.DASHBOARD
            Screen.POS -> Section.POS
            Screen.ORDERS -> Section.ORDERS
            Screen.INVENTORY -> Section.INVENTORY
            Screen.REPORTS -> Section.REPORTS
            Screen.STAFF -> Section.STAFF
            Screen.SETTINGS -> Section.SETTINGS
        }
        renderSection(section)
    }

    private fun renderSection(section: Section) {
        currentSection = section
        binding.tvTopTitle.text = getString(section.titleRes)
        binding.tvTopSubtitle.text = getString(section.subtitleRes)

        val showFragmentScreen = section == Section.INVENTORY || section == Section.REPORTS
        val showPos = section == Section.POS
        val showDashboard = section == Section.DASHBOARD
        val showOrders = section == Section.ORDERS
        val showStaff = section == Section.STAFF
        val showProfile = section == Section.PROFILE
        val showPlaceholder = !showPos && !showDashboard && !showOrders && !showStaff && !showProfile && !showFragmentScreen

        binding.topBar.visibility = if (showFragmentScreen || showStaff) View.GONE else View.VISIBLE
        binding.leftPanel.visibility = if (showPos) View.VISIBLE else View.GONE
        binding.rightPanel.visibility = if (showPos && isCheckoutExpanded) View.VISIBLE else View.GONE
        binding.dashboardContent.root.visibility = if (showDashboard) View.VISIBLE else View.GONE
        binding.ordersContent.root.visibility = if (showOrders) View.VISIBLE else View.GONE
        binding.staffContent.root.visibility = if (showStaff) View.VISIBLE else View.GONE
        binding.profileContent.root.visibility = if (showProfile) View.VISIBLE else View.GONE
        binding.placeholderContent.root.visibility = if (showPlaceholder) View.VISIBLE else View.GONE
        binding.fragmentContainer.visibility = if (showFragmentScreen) View.VISIBLE else View.GONE

        if (showPos) {
            applyCheckoutPanelState(expanded = isCheckoutExpanded, animate = false)
        } else {
            checkoutAnimator?.cancel()
            binding.rightPanel.visibility = View.GONE
            binding.fabCart.visibility = View.GONE
            binding.rightPanel.alpha = 1f
            binding.rightPanel.translationX = 0f
            binding.rightPanel.translationY = 0f
        }

        if (showPlaceholder) {
            binding.placeholderContent.tvPlaceholderTitle.text = getString(
                R.string.section_ready_title,
                getString(section.labelRes)
            )
        }

        if (showOrders) {
            loadOrdersFromSupabase(showError = false)
        }

        if (!showPos) {
            updatePosCategoryChipMode(compact = false)
            updatePosCategoryStripPadding(expanded = false)
        }

        if (showDashboard) {
            dashboardViewModel.startAutoRefresh()
        } else {
            dashboardViewModel.stopAutoRefresh()
        }

        updateHostedFragment(section)
        applySidebarAppearance(isSidebarExpanded)
    }

    private fun captureCheckoutExpandedGuidePercent() {
        val layoutParams = binding.contentGuide.layoutParams as? ConstraintLayout.LayoutParams ?: return
        if (layoutParams.guidePercent in 0f..1f) {
            checkoutExpandedGuidePercent = layoutParams.guidePercent
        }
    }

    private fun setCheckoutExpanded(expanded: Boolean, animate: Boolean) {
        if (isCheckoutExpanded == expanded && currentSection == Section.POS) {
            return
        }
        isCheckoutExpanded = expanded
        if (currentSection == Section.POS) {
            applyCheckoutPanelState(expanded = expanded, animate = animate)
        }
    }

    private fun applyCheckoutPanelState(expanded: Boolean, animate: Boolean) {
        val guideParams = binding.contentGuide.layoutParams as? ConstraintLayout.LayoutParams ?: return
        val targetPercent = if (expanded) checkoutExpandedGuidePercent else CHECKOUT_COLLAPSED_GUIDE_PERCENT
        val startPercent = guideParams.guidePercent
        val checkoutPanel = binding.rightPanel
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val slideOffset = resources.getDimension(R.dimen.checkout_panel_slide_offset)

        checkoutAnimator?.cancel()
        updatePosCategoryChipMode(compact = expanded && isLandscape)
        updatePosCategoryStripPadding(expanded)

        if (!animate || abs(startPercent - targetPercent) < 0.001f) {
            binding.contentGuide.updateLayoutParams<ConstraintLayout.LayoutParams> {
                guidePercent = targetPercent
            }
            checkoutPanel.visibility = if (expanded) View.VISIBLE else View.GONE
            binding.fabCart.visibility = if (!expanded && currentSection == Section.POS) View.VISIBLE else View.GONE
            checkoutPanel.alpha = 1f
            checkoutPanel.translationX = 0f
            checkoutPanel.translationY = 0f
            return
        }

        if (expanded) {
            binding.fabCart.visibility = View.GONE
            checkoutPanel.visibility = View.VISIBLE
            checkoutPanel.alpha = 0f
            if (isLandscape) {
                checkoutPanel.translationX = slideOffset
                checkoutPanel.translationY = 0f
            } else {
                checkoutPanel.translationY = slideOffset
                checkoutPanel.translationX = 0f
            }
        }

        checkoutAnimator = ValueAnimator.ofFloat(startPercent, targetPercent).apply {
            duration = 260L
            addUpdateListener { animator ->
                val progress = animator.animatedFraction
                val panelProgress = if (expanded) progress else 1f - progress

                binding.contentGuide.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    guidePercent = animator.animatedValue as Float
                }

                checkoutPanel.alpha = panelProgress
                if (isLandscape) {
                    checkoutPanel.translationX = (1f - panelProgress) * slideOffset
                    checkoutPanel.translationY = 0f
                } else {
                    checkoutPanel.translationY = (1f - panelProgress) * slideOffset
                    checkoutPanel.translationX = 0f
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                private fun settleCheckoutPanel() {
                    checkoutPanel.visibility = if (expanded) View.VISIBLE else View.GONE
                    binding.fabCart.visibility = if (!expanded && currentSection == Section.POS) View.VISIBLE else View.GONE
                    checkoutPanel.alpha = 1f
                    checkoutPanel.translationX = 0f
                    checkoutPanel.translationY = 0f
                    checkoutAnimator = null
                }

                override fun onAnimationCancel(animation: Animator) {
                    settleCheckoutPanel()
                }

                override fun onAnimationEnd(animation: Animator) {
                    settleCheckoutPanel()
                }
            })
            start()
        }
    }

    private fun updateHostedFragment(section: Section) {
        val fragment = when (section) {
            Section.INVENTORY -> InventoryFragment()
            Section.REPORTS -> ReportsFragment()
            else -> null
        }

        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        if (fragment == null) {
            if (currentFragment != null) {
                supportFragmentManager.commit {
                    setReorderingAllowed(true)
                    remove(currentFragment)
                }
            }
            return
        }

        val tag = section.name
        if (currentFragment?.tag == tag) {
            return
        }

        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.fragmentContainer, fragment, tag)
        }
    }

    private fun applySidebarState(expanded: Boolean, animate: Boolean) {
        val targetWidth = resources.getDimensionPixelSize(
            if (expanded) R.dimen.sidebar_expanded_width else R.dimen.sidebar_collapsed_width
        )

        sidebarTextViews.forEach { view ->
            view.visibility = if (expanded) View.VISIBLE else View.GONE
        }

        val horizontalPadding = resources.getDimensionPixelSize(
            if (expanded) R.dimen.sidebar_row_horizontal_padding else R.dimen.sidebar_row_collapsed_padding
        )
        val collapsedRowMargin = resources.getDimensionPixelSize(R.dimen.sidebar_row_collapsed_margin)
        val rowMargin = if (expanded) 0 else collapsedRowMargin
        sidebarItems.forEach { item ->
            item.row.gravity = if (expanded) Gravity.CENTER_VERTICAL else Gravity.CENTER
            item.row.updatePaddingRelative(start = horizontalPadding, end = horizontalPadding)
            item.row.updateLayoutParams<LinearLayout.LayoutParams> {
                marginStart = rowMargin
                marginEnd = rowMargin
            }
        }

        binding.profileCard.gravity = if (expanded) Gravity.CENTER_VERTICAL else Gravity.CENTER
        binding.profileCard.updateLayoutParams<LinearLayout.LayoutParams> {
            marginStart = rowMargin
            marginEnd = rowMargin
        }
        val contentGap = if (expanded) 0 else resources.getDimensionPixelSize(R.dimen.main_content_gap_collapsed)
        binding.mainContainer.updateLayoutParams<androidx.constraintlayout.widget.ConstraintLayout.LayoutParams> {
            marginStart = contentGap
        }
        binding.btnToggleSidebar.rotation = if (expanded) 0f else 180f
        applySidebarAppearance(expanded)

        val startWidth = binding.sidebarContainer.layoutParams.width
        if (!animate || startWidth <= 0) {
            binding.sidebarContainer.updateLayoutParams { width = targetWidth }
            return
        }

        ValueAnimator.ofInt(startWidth, targetWidth).apply {
            duration = 220L
            addUpdateListener { animator ->
                binding.sidebarContainer.updateLayoutParams {
                    width = animator.animatedValue as Int
                }
            }
            start()
        }
    }

    private fun applySidebarAppearance(expanded: Boolean) {
        val sidebarText = ContextCompat.getColor(this, R.color.pos_text_on_sidebar)
        val sidebarMuted = ContextCompat.getColor(this, R.color.pos_text_on_sidebar_muted)
        val white = ContextCompat.getColor(this, R.color.white)

        binding.sidebarSurface.setBackgroundResource(R.drawable.bg_sidebar_surface)
        val profileSelected = currentSection == Section.PROFILE
        binding.profileCard.setBackgroundResource(
            if (profileSelected) R.drawable.bg_sidebar_item_selected else R.drawable.bg_profile_card
        )
        binding.ivSidebarLogo.imageTintList = null
        binding.btnToggleSidebar.imageTintList = ColorStateList.valueOf(white)
        binding.tvSidebarTitle.setTextColor(white)
        binding.tvSidebarSubtitle.setTextColor(sidebarMuted)
        binding.tvProfileName.setTextColor(white)
        binding.tvProfileEmail.setTextColor(if (profileSelected) white else sidebarMuted)

        sidebarItems.forEach { item ->
            val isSelected = item.section == currentSection
            item.row.setBackgroundResource(if (isSelected) R.drawable.bg_sidebar_item_selected else 0)
            val itemColor = if (isSelected) white else sidebarText
            item.icon.imageTintList = ColorStateList.valueOf(itemColor)
            item.label.setTextColor(itemColor)
            item.label.setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun loadSidebarLogo() {
        runCatching {
            assets.open(SIDEBAR_LOGO_ASSET_PATH).use(BitmapFactory::decodeStream)
        }.getOrNull()?.let { bitmap ->
            binding.ivSidebarLogo.scaleType = ImageView.ScaleType.CENTER_INSIDE
            binding.ivSidebarLogo.setImageBitmap(bitmap)
        }
    }

    private fun applyPaymentSelection(method: PosViewModel.PaymentMethod) {
        val selectedBg = ContextCompat.getColor(this, R.color.pos_secondary)
        val unselectedBg = ContextCompat.getColor(this, R.color.pos_surface_soft)
        val selectedText = ContextCompat.getColor(this, R.color.white)
        val unselectedText = ContextCompat.getColor(this, R.color.pos_text_primary)
        val unselectedStroke = ContextCompat.getColor(this, R.color.pos_border)

        val buttons = mapOf(
            binding.btnCash to PosViewModel.PaymentMethod.CASH,
            binding.btnGcash to PosViewModel.PaymentMethod.GCASH,
            binding.btnCard to PosViewModel.PaymentMethod.CARD
        )

        buttons.forEach { (button, value) ->
            val isSelected = method == value
            button.backgroundTintList = ColorStateList.valueOf(if (isSelected) selectedBg else unselectedBg)
            button.setTextColor(if (isSelected) selectedText else unselectedText)
            button.strokeColor = ColorStateList.valueOf(if (isSelected) selectedBg else unselectedStroke)
            button.strokeWidth = if (isSelected) 0 else resources.getDimensionPixelSize(R.dimen.payment_button_stroke_width)
        }

        binding.btnClear.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.pos_clear_bg)
        )
        binding.btnClear.setTextColor(ContextCompat.getColor(this, R.color.pos_text_primary))

        binding.btnCheckout.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.pos_secondary)
        )
        binding.btnCheckout.setTextColor(ContextCompat.getColor(this, R.color.pos_checkout_text))
    }

    private fun configureCashOnlyCheckout() {
        binding.btnGcash.visibility = View.GONE
        binding.btnCard.visibility = View.GONE
        (binding.tvTax.parent as? View)?.visibility = View.GONE
        updateCheckoutButtonState()
    }

    private fun updateCheckoutButtonState() {
        val isEnabled = hasCheckoutItems && !isCheckoutSaving
        binding.btnCheckout.isEnabled = isEnabled
        binding.btnCheckout.alpha = if (isEnabled) 1f else 0.6f
    }

    private fun updatePosCategoryStripPadding(expanded: Boolean) {
        val checkoutOpenInLandscape =
            expanded && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val targetPaddingEnd = resources.getDimensionPixelSize(
            if (checkoutOpenInLandscape) {
                R.dimen.pos_category_padding_end_checkout_open
            } else {
                R.dimen.pos_category_padding_end_default
            }
        )

        if (binding.rvCategories.paddingEnd == targetPaddingEnd) {
            return
        }

        binding.rvCategories.setPaddingRelative(
            binding.rvCategories.paddingStart,
            binding.rvCategories.paddingTop,
            targetPaddingEnd,
            binding.rvCategories.paddingBottom
        )
    }

    private fun updatePosCategoryChipMode(compact: Boolean) {
        if (!::categoryAdapter.isInitialized) {
            return
        }
        categoryAdapter.compactMode = compact
    }

    private class GridSpacingItemDecoration(
        private val spacing: Int
    ) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            outRect.left = spacing / 2
            outRect.right = spacing / 2
            outRect.bottom = spacing
        }
    }

    private class VerticalSpaceItemDecoration(
        private val spacing: Int
    ) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            if (parent.getChildAdapterPosition(view) > 0) {
                outRect.top = spacing
            }
        }
    }

    private class HorizontalSpaceItemDecoration(
        private val spacing: Int
    ) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            if (parent.getChildAdapterPosition(view) > 0) {
                outRect.left = spacing
            }
        }
    }
}

