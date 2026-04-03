// app/src/main/java/com/example/zejioscafese/MainActivity.kt
package com.example.zejioscafese

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.zejioscafese.dashboard.data.DashboardSampleData
import com.example.zejioscafese.dashboard.model.AlertLevel
import com.example.zejioscafese.dashboard.model.DashboardPeriod
import com.example.zejioscafese.dashboard.ui.DashboardAlertAdapter
import com.example.zejioscafese.dashboard.ui.DashboardInsightAdapter
import com.example.zejioscafese.dashboard.ui.DashboardTopItemAdapter
import com.example.zejioscafese.databinding.ActivityMainBinding
import com.example.zejioscafese.orders.data.OrderSampleData
import com.example.zejioscafese.orders.ui.OrderManagementAdapter
import com.example.zejioscafese.pos.presentation.PosViewModel
import com.example.zejioscafese.pos.ui.CategoryAdapter
import com.example.zejioscafese.pos.ui.OrderItemAdapter
import com.example.zejioscafese.pos.ui.ProductAdapter
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter

class MainActivity : AppCompatActivity() {

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
        SETTINGS(R.string.settings, R.string.settings_title, R.string.settings_subtitle)
    }

    private data class SidebarItem(
        val section: Section,
        val row: LinearLayout,
        val icon: ImageView,
        val label: TextView
    )

    private lateinit var binding: ActivityMainBinding
    private val viewModel: PosViewModel by viewModels()

    private lateinit var productAdapter: ProductAdapter
    private lateinit var orderItemAdapter: OrderItemAdapter
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var dashboardInsightAdapter: DashboardInsightAdapter
    private lateinit var dashboardTopItemAdapter: DashboardTopItemAdapter
    private lateinit var dashboardAlertAdapter: DashboardAlertAdapter
    private lateinit var orderManagementAdapter: OrderManagementAdapter

    private var isSidebarExpanded: Boolean = true
    private var currentSection: Section = Section.POS

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

        setupRecyclerViews()
        setupDashboard()
        setupOrders()
        setupSidebar()
        setupInteractions()
        observeViewModel()
        renderSection(Section.POS)
        applySidebarState(isSidebarExpanded, animate = false)
        viewModel.setPaymentMethod(PosViewModel.PaymentMethod.CASH)

        // Restore or default to POS
        if (savedInstanceState != null) {
            val restored = savedInstanceState.getString(KEY_CURRENT_SCREEN, SCREEN_POS)
            currentScreen = "" // force navigateTo to proceed
            navigateTo(restored)
        } else {
            // Default: POS screen is shown inline, no fragment needed
            applySidebarHighlight(SCREEN_POS)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_CURRENT_SCREEN, currentScreen)
    }

    private fun setupRecyclerViews() {
        categoryAdapter = CategoryAdapter(onCategoryClick = viewModel::selectCategory)
        binding.rvCategories.apply {
            adapter = categoryAdapter
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
        }

        productAdapter = ProductAdapter(onCardClick = viewModel::increaseProduct)
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
        val alerts = DashboardSampleData.alerts

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

        dashboardInsightAdapter.submitList(DashboardSampleData.insights)
        dashboardTopItemAdapter.submitList(DashboardSampleData.topItems)
        dashboardAlertAdapter.submitList(alerts)

        bindDashboardMetrics()
        bindDashboardFocus(alerts)
        setupDashboardChart()
        setupDashboardToggle()
    }

    private fun setupOrders() {
        orderManagementAdapter = OrderManagementAdapter()

        binding.ordersContent.rvOrders.apply {
            adapter = orderManagementAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
            itemAnimator = null
            isNestedScrollingEnabled = false
        }

        val orders = OrderSampleData.orders
        orderManagementAdapter.submitList(orders)
        binding.ordersContent.tvOrdersShowing.text = getString(
            R.string.showing_orders_range,
            1,
            orders.size,
            24
        )
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

    private fun bindDashboardMetrics() {
        val dashboardRoot = binding.dashboardContent.root
        bindMetric(
            dashboardRoot.findViewById(R.id.tvMetricSalesValue),
            dashboardRoot.findViewById(R.id.tvMetricSalesDelta),
            getString(R.string.dashboard_metric_sales_value),
            getString(R.string.dashboard_metric_sales_delta),
            positive = true
        )
        bindMetric(
            dashboardRoot.findViewById(R.id.tvMetricOrdersValue),
            dashboardRoot.findViewById(R.id.tvMetricOrdersDelta),
            getString(R.string.dashboard_metric_orders_value),
            getString(R.string.dashboard_metric_orders_delta),
            positive = true
        )
        bindMetric(
            dashboardRoot.findViewById(R.id.tvMetricProfitValue),
            dashboardRoot.findViewById(R.id.tvMetricProfitDelta),
            getString(R.string.dashboard_metric_profit_value),
            getString(R.string.dashboard_metric_profit_delta),
            positive = true
        )
        bindMetric(
            dashboardRoot.findViewById(R.id.tvMetricActiveOrdersValue),
            dashboardRoot.findViewById(R.id.tvMetricActiveOrdersDelta),
            getString(R.string.dashboard_metric_active_orders_value),
            getString(R.string.dashboard_metric_active_orders_delta),
            positive = false
        )
        bindMetric(
            dashboardRoot.findViewById(R.id.tvMetricLowStockValue),
            dashboardRoot.findViewById(R.id.tvMetricLowStockDelta),
            getString(R.string.dashboard_metric_low_stock_value),
            getString(R.string.dashboard_metric_low_stock_delta),
            positive = false
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

        updateDashboardChart(DashboardPeriod.DAILY)
    }

    private fun setupDashboardToggle() {
        binding.dashboardContent.togglePeriodGroup.check(binding.dashboardContent.btnChartDaily.id)
        binding.dashboardContent.togglePeriodGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val period = when (checkedId) {
                binding.dashboardContent.btnChartWeekly.id -> DashboardPeriod.WEEKLY
                binding.dashboardContent.btnChartMonthly.id -> DashboardPeriod.MONTHLY
                else -> DashboardPeriod.DAILY
            }
            updateDashboardChart(period)
            styleDashboardToggleButtons()
        }
        styleDashboardToggleButtons()
    }

    private fun updateDashboardChart(period: DashboardPeriod) {
        val points = DashboardSampleData.chart(period)
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
        binding.btnCheckout.setOnClickListener { }
        binding.fabCart.setOnClickListener { renderSection(Section.POS) }

        binding.btnCash.setOnClickListener {
            viewModel.setPaymentMethod(PosViewModel.PaymentMethod.CASH)
        }
        binding.btnGcash.setOnClickListener {
            viewModel.setPaymentMethod(PosViewModel.PaymentMethod.GCASH)
        }
        binding.btnCard.setOnClickListener {
            viewModel.setPaymentMethod(PosViewModel.PaymentMethod.CARD)
        }
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
            binding.emptyOrderState.visibility = if (hasItems) View.GONE else View.VISIBLE
            binding.rvOrderItems.visibility = if (hasItems) View.VISIBLE else View.GONE
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

        // One-shot checkout success event
        viewModel.checkoutEvent.observe(this) { orderNumber ->
            if (orderNumber != null) {
                Snackbar.make(
                    binding.root,
                    "Order $orderNumber placed successfully ✓",
                    Snackbar.LENGTH_LONG
                ).show()
                viewModel.onCheckoutEventConsumed()
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

    private fun renderSection(section: Section) {
        currentSection = section
        binding.tvTopTitle.text = getString(section.titleRes)
        binding.tvTopSubtitle.text = getString(section.subtitleRes)

        val showPos = section == Section.POS
        val showDashboard = section == Section.DASHBOARD
        val showOrders = section == Section.ORDERS
        val showPlaceholder = !showPos && !showDashboard && !showOrders

        binding.leftPanel.visibility = if (showPos) View.VISIBLE else View.GONE
        binding.rightPanel.visibility = if (showPos) View.VISIBLE else View.GONE
        binding.fabCart.visibility = if (showPos) View.VISIBLE else View.GONE
        binding.dashboardContent.root.visibility = if (showDashboard) View.VISIBLE else View.GONE
        binding.ordersContent.root.visibility = if (showOrders) View.VISIBLE else View.GONE
        binding.placeholderContent.root.visibility = if (showPlaceholder) View.VISIBLE else View.GONE

        if (showPlaceholder) {
            binding.placeholderContent.tvPlaceholderTitle.text = getString(
                R.string.section_ready_title,
                getString(section.labelRes)
            )
        }

        applySidebarAppearance(isSidebarExpanded)
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
        sidebarItems.forEach { item ->
            item.row.gravity = if (expanded) Gravity.CENTER_VERTICAL else Gravity.CENTER
            item.row.updatePaddingRelative(start = horizontalPadding, end = horizontalPadding)
        }

        binding.profileCard.gravity = if (expanded) Gravity.CENTER_VERTICAL else Gravity.CENTER
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
        binding.profileCard.setBackgroundResource(R.drawable.bg_profile_card)
        binding.ivSidebarLogo.imageTintList = ColorStateList.valueOf(white)
        binding.btnToggleSidebar.imageTintList = ColorStateList.valueOf(white)
        binding.tvSidebarTitle.setTextColor(white)
        binding.tvSidebarSubtitle.setTextColor(sidebarMuted)
        binding.tvProfileName.setTextColor(white)
        binding.tvProfileEmail.setTextColor(sidebarMuted)

        sidebarItems.forEach { item ->
            val isSelected = item.section == currentSection
            item.row.setBackgroundResource(if (isSelected) R.drawable.bg_sidebar_item_selected else 0)
            val itemColor = if (isSelected) white else sidebarText
            item.icon.imageTintList = ColorStateList.valueOf(itemColor)
            item.label.setTextColor(itemColor)
            item.label.setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
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
