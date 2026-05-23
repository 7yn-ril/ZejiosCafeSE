// app/src/main/java/com/example/zejioscafese/MainActivity.kt
package com.example.zejioscafese

import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.net.Uri
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialog
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
import com.example.zejioscafese.dashboard.model.AlertLevel
import com.example.zejioscafese.dashboard.model.DashboardAlert
import com.example.zejioscafese.dashboard.model.DashboardPeriod
import com.example.zejioscafese.dashboard.model.DashboardRecentOrder
import com.example.zejioscafese.dashboard.model.DashboardSnapshot
import com.example.zejioscafese.dashboard.presentation.DashboardViewModel
import com.example.zejioscafese.dashboard.ui.DashboardAlertAdapter
import com.example.zejioscafese.dashboard.ui.DashboardInsightAdapter
import com.example.zejioscafese.dashboard.ui.DashboardTopItemAdapter
import com.example.zejioscafese.databinding.ActivityMainBinding
import com.example.zejioscafese.core.local.LocalAppPrefs
import com.example.zejioscafese.core.network.NetworkErrorFormatter
import com.example.zejioscafese.core.notifications.AppNotifications
import com.example.zejioscafese.dashboard.model.InventoryStockNotice
import com.example.zejioscafese.dashboard.model.InventoryStockStatus
import com.example.zejioscafese.orders.data.repository.CheckoutOrderLine
import com.example.zejioscafese.orders.data.repository.CheckoutOrderPayload
import com.example.zejioscafese.orders.data.repository.OrderRepository
import com.example.zejioscafese.orders.model.CafeOrder
import com.example.zejioscafese.orders.model.CafeOrderLine
import com.example.zejioscafese.orders.model.CafeOrderStatus
import com.example.zejioscafese.orders.ui.OrderManagementAdapter
import com.example.zejioscafese.payments.data.PayMongoCheckoutLine
import com.example.zejioscafese.payments.data.PayMongoCheckoutRepository
import com.example.zejioscafese.payments.data.PayMongoCheckoutSession
import com.example.zejioscafese.pos.data.model.Discount
import com.example.zejioscafese.pos.data.model.OrderItem
import com.example.zejioscafese.pos.data.model.Product
import com.example.zejioscafese.pos.data.repository.DiscountRepository
import com.example.zejioscafese.pos.presentation.PosViewModel
import com.example.zejioscafese.pos.ui.CategoryAdapter
import com.example.zejioscafese.pos.ui.OrderItemAdapter
import com.example.zejioscafese.pos.ui.ProductAdapter
import com.example.zejioscafese.pos.ui.MenuBrowseDialogFragment
import com.example.zejioscafese.ui.InventoryFragment
import com.example.zejioscafese.ui.NavigationHost
import com.example.zejioscafese.ui.ReportsFragment
import com.example.zejioscafese.ui.Screen
import com.example.zejioscafese.ui.applyZejiosCafeButtonStyling
import com.example.zejioscafese.ui.showErrorDialog
import com.example.zejioscafese.ui.showInfoDialog
import com.example.zejioscafese.ui.showNoticeDialog
import com.example.zejioscafese.ui.showSuccessDialog
import com.example.zejioscafese.ui.showWarningDialog
import com.example.zejioscafese.ui.showStyledDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MainActivity : AppCompatActivity(), NavigationHost {

    private enum class MetricTone {
        POSITIVE,
        NEGATIVE,
        NEUTRAL
    }

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
        val rootView: View,
        val nameView: TextView,
        val idView: TextView,
        val roleView: TextView,
        val editButton: MaterialButton,
        val actionsButton: MaterialButton
    )

    private data class UserProfileState(
        var name: String,
        var email: String,
        var role: String,
        var phone: String,
        var address: String
    )

    @Serializable
    private data class ReceiptLine(
        val label: String,
        val quantity: Int,
        val lineTotal: Double
    )

    // CHANGE: Payment — receipt now carries the discount label + amount so
    // the receipt dialog and any downstream summary can render the line.
    private data class PendingCheckoutReceipt(
        val customerName: String?,
        val cashReceived: Double,
        val subtotal: Double,
        val total: Double,
        val lines: List<ReceiptLine>,
        val discountLabel: String? = null,
        val discountAmount: Double = 0.0,
        val paymentReference: String? = null
    )

    @Serializable
    private data class PendingPayMongoCheckout(
        val orderNumber: String,
        val sessionId: String,
        val checkoutUrl: String,
        val referenceNumber: String?,
        val status: String?,
        val customerName: String?,
        val subtotal: Double,
        val tax: Double,
        val total: Double,
        val discountLabel: String?,
        val discountAmount: Double,
        val discountId: String?,
        val discountPercent: Double?,
        val orderType: String,
        val items: List<CheckoutOrderLine>,
        val receiptLines: List<ReceiptLine>,
        val createdAtMillis: Long = System.currentTimeMillis()
    ) {
        fun toSession() = PayMongoCheckoutSession(
            id = sessionId,
            checkoutUrl = checkoutUrl,
            referenceNumber = referenceNumber,
            status = status
        )

        fun toReceipt(paymentReference: String?) = PendingCheckoutReceipt(
            customerName = customerName,
            cashReceived = total,
            subtotal = subtotal,
            total = total,
            lines = receiptLines,
            discountLabel = discountLabel,
            discountAmount = discountAmount,
            paymentReference = paymentReference
        )
    }

    // CHANGE: Discounts — replaces the legacy NONE/10/20/50 enum. The
    // checkout dialog renders three radio options (None / Built-in /
    // Other); the Built-in row resolves to either PWD or Senior based
    // on which chip the user selected; Other surfaces a spinner of
    // custom discounts whose date range covers today.
    private data class ResolvedDiscount(
        val discount: Discount?,
        val label: String?,
        val amount: Double
    ) {
        companion object {
            val NONE = ResolvedDiscount(discount = null, label = null, amount = 0.0)
        }
    }

    private val discountRepository = DiscountRepository()

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
    // In-memory tracker for which line items in a PREPARING order have been
    // marked done by staff. Keyed by order id; value is the set of item
    // indices (positions in CafeOrder.orderedItems) that are completed.
    private val orderItemCompletion = mutableMapOf<String, MutableSet<Int>>()
    private val orderRepository = OrderRepository()
    private val payMongoCheckoutRepository = PayMongoCheckoutRepository()
    private var selectedOrderStatus: CafeOrderStatus? = null
    private var orderSearchQuery: String = ""
    private var isOrdersLoading: Boolean = false
    private var lastOrdersLoadedAtMs: Long = 0L
    private var ordersRefreshJob: Job? = null
    private var ordersPage: Int = 0
    private var orderSort: OrderSort = OrderSort.DEFAULT
    // CHANGE: Orders — toggleable filters layered on top of the existing
    // status chips and search box. Both are independent: the user can
    // combine "Preparing" + "GCash" + "Take Out" in one view.
    private var filterGcashOnly: Boolean = false
    private var filterTakeoutOnly: Boolean = false
    private var filterTodayOnly: Boolean = false

    private enum class OrderSort { DEFAULT, DATE_DESC, TOTAL_DESC, TOTAL_ASC, ITEMS_DESC }
    private val staffCards = mutableListOf<StaffCardViews>()
    private var selectedStaffRole: String? = null
    private var staffSearchQuery: String = ""
    private var hasCheckoutItems: Boolean = false
    private var isCheckoutSaving: Boolean = false
    private var pendingCheckoutReceipt: PendingCheckoutReceipt? = null
    private val paymentPersistenceJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private var isPayMongoVerificationRunning: Boolean = false
    private var dashboardSnapshot: DashboardSnapshot = DashboardSnapshot.empty()
    private var selectedDashboardPeriod: DashboardPeriod = DashboardPeriod.DAILY
    private var hasRenderedDashboardChart: Boolean = false

    // Notification de-dupe state. Track which order IDs we've already
    // surfaced as "new order" notifications, and which ingredients we've
    // already pinged for low / out-of-stock so refreshes don't re-spam
    // the system tray every 30 seconds.
    private val notifiedOrderIds = mutableSetOf<String>()
    private val notifiedLowStockIds = mutableSetOf<String>()
    private val notifiedOutOfStockIds = mutableSetOf<String>()
    private var hasSeenInitialOrders: Boolean = false
    private var hasSeenInitialStockSnapshot: Boolean = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result handled implicitly */ }

    private var isSidebarExpanded: Boolean = true
    private var currentSection: Section = Section.POS
    private var isCheckoutExpanded: Boolean = false
    private var sidebarExpandedBeforeCheckout: Boolean? = null
    private var isCategoryExpanded: Boolean = false
    private var checkoutExpandedGuidePercent: Float = 0.70f
    private var checkoutAnimator: ValueAnimator? = null
    private var sidebarAnimator: ValueAnimator? = null
    private val categoryThumbnails = mutableMapOf<String, String?>()

    companion object {
        private const val STATE_CURRENT_SECTION = "current_section"
        private const val STATE_CHECKOUT_EXPANDED = "checkout_expanded"
        private const val STATE_SIDEBAR_EXPANDED = "sidebar_expanded"
        private const val CHECKOUT_COLLAPSED_GUIDE_PERCENT = 1f
        private const val SIDEBAR_LOGO_ASSET_PATH = "other_assets/ZejiosCafeLogo.jpg"
        private const val POS_PAGE_SIZE = 12
        private const val ORDERS_PAGE_SIZE = 10
        private const val ORDERS_REFRESH_INTERVAL_MS = 30_000L
        private const val PAYMENT_PREFS_NAME = "zejios_payments"
        private const val KEY_PENDING_PAYMONGO_CHECKOUT = "pending_paymongo_checkout"
        private const val PAYMONGO_RETURN_SCHEME = "zejioscafe"
        private const val PAYMONGO_RETURN_HOST = "paymongo"
        private const val PAYMONGO_RETURN_PATH = "/checkout-return"
        private const val PAYMONGO_RETURN_VERIFY_DELAY_MS = 1_500L
        private const val TAG = "MainActivity"
    }

    private val sidebarExpandedOnlyViews by lazy {
        listOf<View>(
            binding.sidebarHeaderTextContainer,
            binding.sidebarHeaderDivider,
            binding.sidebarSectionDivider,
            binding.profileTextContainer
        )
    }

    private val sidebarLabelViews by lazy {
        listOf<View>(
            binding.tvSidebarDashboard,
            binding.tvSidebarPos,
            binding.tvSidebarOrders,
            binding.tvSidebarInventory,
            binding.tvSidebarReports,
            binding.tvSidebarStaff
        )
    }

    private val sidebarItems by lazy {
        listOf(
            SidebarItem(Section.DASHBOARD, binding.itemDashboard, binding.ivSidebarDashboard, binding.tvSidebarDashboard),
            SidebarItem(Section.POS, binding.itemPos, binding.ivSidebarPos, binding.tvSidebarPos),
            SidebarItem(Section.ORDERS, binding.itemOrders, binding.ivSidebarOrders, binding.tvSidebarOrders),
            SidebarItem(Section.INVENTORY, binding.itemInventory, binding.ivSidebarInventory, binding.tvSidebarInventory),
            SidebarItem(Section.REPORTS, binding.itemReports, binding.ivSidebarReports, binding.tvSidebarReports),
            SidebarItem(Section.STAFF, binding.itemStaff, binding.ivSidebarStaff, binding.tvSidebarStaff)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        AppNotifications.ensureChannels(this)
        ensureNotificationPermission()

        isSidebarExpanded = savedInstanceState?.getBoolean(STATE_SIDEBAR_EXPANDED)
            ?: shouldDefaultSidebarBeExpanded()
        captureCheckoutExpandedGuidePercent()
        isCheckoutExpanded = savedInstanceState?.getBoolean(STATE_CHECKOUT_EXPANDED) ?: false
        val savedProfile = LocalAppPrefs.loadProfile(this)
        userProfileState = if (savedProfile != null) {
            UserProfileState(
                name = savedProfile.name,
                email = savedProfile.email,
                role = savedProfile.role,
                phone = savedProfile.phone,
                address = savedProfile.address
            )
        } else {
            UserProfileState(
                name = getString(R.string.profile_name),
                email = getString(R.string.profile_email),
                role = getString(R.string.profile_role),
                phone = getString(R.string.profile_phone_value),
                address = getString(R.string.profile_address_value)
            )
        }

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
        handlePayMongoReturn(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePayMongoReturn(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_CURRENT_SECTION, currentSection.ordinal)
        outState.putBoolean(STATE_CHECKOUT_EXPANDED, isCheckoutExpanded)
        outState.putBoolean(STATE_SIDEBAR_EXPANDED, isSidebarExpanded)
    }

    override fun onStart() {
        super.onStart()
        startOrdersAutoRefresh()
    }

    override fun onStop() {
        stopOrdersAutoRefresh()
        super.onStop()
    }

    private fun setupRecyclerViews() {
        categoryAdapter = CategoryAdapter(onCategoryClick = viewModel::selectCategory)
        categoryAdapter.expandedMode = false
        binding.rvCategories.apply {
            adapter = categoryAdapter
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
        }
        updatePosCategoryChipMode(compact = false)
        updatePosCategoryStripPadding(expanded = false)

        productAdapter = ProductAdapter(
            onGroupClick = ::handleProductGroupClick,
            onIncrease = { product -> viewModel.increaseProduct(product) },
            onDecrease = { product -> handleProductDecrease(product) }
        )
        binding.rvProducts.apply {
            adapter = productAdapter
            layoutManager = GridLayoutManager(
                this@MainActivity,
                resources.getInteger(R.integer.product_grid_span_count)
            )
            itemAnimator = null
            if (itemDecorationCount == 0) {
                addItemDecoration(GridSpacingItemDecoration(resources.getDimensionPixelSize(R.dimen.product_grid_spacing)))
            }
        }
        updateProductGridSpanCount()

        orderItemAdapter = OrderItemAdapter(
            onIncreaseClick = viewModel::increaseOrderItem,
            onDecreaseClick = ::handleOrderItemDecrease
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
        setupDashboardActions()
    }

    private fun setupOrders() {
        orderManagementAdapter = OrderManagementAdapter(
            onOrderItemsClick = ::showOrderItemsDialog,
            onOrderActionClick = ::showOrderRowActionsMenu
        )

        binding.ordersContent.rvOrders.apply {
            adapter = orderManagementAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
            itemAnimator = null
            isNestedScrollingEnabled = false
        }

        orders.clear()

        binding.ordersContent.etOrderSearch.doAfterTextChanged { text ->
            orderSearchQuery = text?.toString().orEmpty()
            ordersPage = 0
            applyOrderFilters()
        }

        binding.ordersContent.chipAllOrders.setOnClickListener {
            selectedOrderStatus = null
            ordersPage = 0
            applyOrderFilters()
        }

        binding.ordersContent.chipPreparingOrders.setOnClickListener {
            selectedOrderStatus = CafeOrderStatus.PREPARING
            ordersPage = 0
            applyOrderFilters()
        }

        binding.ordersContent.chipCompletedOrders.setOnClickListener {
            selectedOrderStatus = CafeOrderStatus.COMPLETED
            ordersPage = 0
            applyOrderFilters()
        }

        binding.ordersContent.chipCancelledOrders.setOnClickListener {
            selectedOrderStatus = CafeOrderStatus.CANCELLED
            ordersPage = 0
            applyOrderFilters()
        }

        binding.ordersContent.btnOrdersFilter.setOnClickListener { anchor ->
            showOrdersFilterMenu(anchor)
        }

        binding.ordersContent.btnOrdersPrevPage.setOnClickListener {
            if (ordersPage > 0) {
                ordersPage -= 1
                applyOrderFilters()
            }
        }

        binding.ordersContent.btnOrdersNextPage.setOnClickListener {
            ordersPage += 1
            applyOrderFilters()
        }

        applyOrderFilters()
    }

    /**
     * Per-row action menu wired to the trailing button on every order row.
     * Actions stay frontend-only so the UI feels complete without changing
     * backend write paths.
     */
    private fun showOrderRowActionsMenu(order: CafeOrder, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, getString(R.string.order_action_view_details))
        when (order.status) {
            CafeOrderStatus.PENDING -> {
                popup.menu.add(0, 2, 1, getString(R.string.order_action_mark_preparing))
                popup.menu.add(0, 3, 2, getString(R.string.order_action_mark_completed))
            }
            CafeOrderStatus.PREPARING -> {
                popup.menu.add(0, 3, 1, getString(R.string.order_action_mark_completed))
            }
            CafeOrderStatus.COMPLETED,
            CafeOrderStatus.CANCELLED -> Unit
        }
        popup.menu.add(0, 4, 3, getString(R.string.order_action_print_receipt))
        // Only active orders can be cancelled. Completed orders are final.
        if (order.canBeCancelled()) {
            popup.menu.add(0, 5, 4, getString(R.string.order_action_cancel))
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> showOrderItemsDialog(order)
                2 -> updateOrderStatus(order, CafeOrderStatus.PREPARING)
                3 -> handleCompleteOrderAction(order)
                4 -> showOrderReceiptPreview(order)
                5 -> showCancelOrderDialog(order)
            }
            true
        }
        popup.show()
    }

    private fun handleCompleteOrderAction(order: CafeOrder) {
        val items = order.orderedItems.ifEmpty { listOf(order.itemsSummary) }
        if (order.status == CafeOrderStatus.PREPARING && items.size > 1) {
            showOrderItemsDialog(order)
        } else {
            updateOrderStatus(order, CafeOrderStatus.COMPLETED)
        }
    }

    private fun updateOrderStatus(order: CafeOrder, newStatus: CafeOrderStatus) {
        val index = orders.indexOfFirst { it.id == order.id }
        if (index == -1) {
            return
        }

        val previous = orders[index]
        val previousCompletion = orderItemCompletion[order.id]?.toMutableSet()
        val updated = previous.copy(
            status = newStatus,
            completedItemVariantIds = if (newStatus == CafeOrderStatus.COMPLETED) {
                previous.orderedItemVariantIds.toSet()
            } else {
                previous.completedItemVariantIds
            },
            completedAtMillis = if (newStatus == CafeOrderStatus.COMPLETED) {
                previous.completedAtMillis ?: System.currentTimeMillis()
            } else {
                null
            }
        )
        orders[index] = updated

        // Keep the per-item completion state in sync with status changes so
        // the dialog shows the right state next time it's opened.
        when (newStatus) {
            CafeOrderStatus.COMPLETED -> {
                val items = updated.orderedItems.ifEmpty { listOf(updated.itemsSummary) }
                orderItemCompletion[updated.id] = items.indices.toMutableSet()
            }
            CafeOrderStatus.PENDING,
            CafeOrderStatus.CANCELLED -> orderItemCompletion.remove(updated.id)
            CafeOrderStatus.PREPARING -> Unit
        }

        applyOrderFilters()

        lifecycleScope.launch {
            try {
                val result = orderRepository.updateOrderStatus(order.id, newStatus)
                loadOrdersFromSupabase(
                    showError = true,
                    force = true,
                    orderToKeepVisible = updated
                )
                dashboardViewModel.refreshDashboard(force = true)
                if (newStatus == CafeOrderStatus.COMPLETED) {
                    viewModel.refreshMenu()
                }
                // CHANGE: Partial completion — when some items couldn't be
                // made (insufficient ingredients, missing recipe, low manual
                // stock), the server marked the completable lines done and
                // left the rest pending. Tell the barista which lines need
                // restocking so they know what to do next.
                if (newStatus == CafeOrderStatus.COMPLETED && !result.fullyCompleted) {
                    val summary = result.blockedItems.joinToString("; ") {
                        "${it.name} (${it.reason})"
                    }
                    showWarningDialog(
                        this@MainActivity,
                        getString(
                            R.string.order_completion_partial_message,
                            order.id,
                            result.blockedItems.size,
                            summary
                        )
                    )
                } else {
                    showSuccessDialog(
                        this@MainActivity,
                        getString(
                            R.string.order_status_updated_message,
                            order.id,
                            formatOrderStatus(newStatus)
                        )
                    )
                }
            } catch (exception: Exception) {
                val rollbackIndex = orders.indexOfFirst { it.id == previous.id }
                if (rollbackIndex >= 0) {
                    orders[rollbackIndex] = previous
                } else {
                    orders.add(previous)
                }
                if (previousCompletion == null) {
                    orderItemCompletion.remove(order.id)
                } else {
                    orderItemCompletion[order.id] = previousCompletion
                }
                applyOrderFilters()
                showErrorDialog(
                    this@MainActivity,
                    getString(
                        R.string.order_status_update_failed,
                        exception.message ?: "Please try again."
                    )
                )
            }
        }
    }

    private fun showCancelOrderDialog(order: CafeOrder) {
        if (!order.canBeCancelled()) {
            return
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.order_cancel_dialog_title))
            .setMessage(getString(R.string.order_cancel_dialog_message, order.id, order.customerName))
            .setPositiveButton(getString(R.string.order_cancel_dialog_confirm)) { _, _ ->
                cancelOrder(order)
            }
            .setNegativeButton(getString(R.string.order_cancel_dialog_dismiss), null)
            .showStyledDialog(this)
    }

    private fun cancelOrder(order: CafeOrder) {
        if (!order.canBeCancelled()) {
            return
        }

        val index = orders.indexOfFirst { it.id == order.id }
        if (index == -1) return

        val previous = orders[index]
        val previousCompletion = orderItemCompletion[order.id]?.toMutableSet()
        val updated = previous.copy(
            status = CafeOrderStatus.CANCELLED,
            completedItemVariantIds = emptySet(),
            completedAtMillis = null
        )
        orders[index] = updated
        orderItemCompletion.remove(order.id)
        applyOrderFilters()

        lifecycleScope.launch {
            try {
                orderRepository.updateOrderStatus(order.id, CafeOrderStatus.CANCELLED)
                loadOrdersFromSupabase(
                    showError = true,
                    force = true,
                    orderToKeepVisible = updated
                )
                dashboardViewModel.refreshDashboard(force = true)
                showSuccessDialog(
                    this@MainActivity,
                    getString(R.string.order_cancelled_message, order.id)
                )
            } catch (exception: Exception) {
                val rollbackIndex = orders.indexOfFirst { it.id == previous.id }
                if (rollbackIndex >= 0) {
                    orders[rollbackIndex] = previous
                } else {
                    orders.add(previous)
                }
                if (previousCompletion != null) {
                    orderItemCompletion[order.id] = previousCompletion
                }
                applyOrderFilters()
                showErrorDialog(
                    this@MainActivity,
                    getString(
                        R.string.order_cancel_failed,
                        exception.message ?: "Please try again."
                    )
                )
            }
        }
    }

    private fun CafeOrder.canBeCancelled(): Boolean {
        val isActiveStatus = status == CafeOrderStatus.PENDING || status == CafeOrderStatus.PREPARING
        val hasFinalizedItem = completedItemVariantIds.isNotEmpty() || deductedItemVariantIds.isNotEmpty()
        return isActiveStatus && !hasFinalizedItem
    }

    private fun showOrderReceiptPreview(order: CafeOrder) {
        val subtotal = if (order.subtotal > 0.0) order.subtotal else order.total + order.discountAmount
        val receipt = PendingCheckoutReceipt(
            customerName = order.customerName,
            cashReceived = order.total,
            subtotal = subtotal,
            total = order.total,
            lines = order.toReceiptLines(),
            discountLabel = order.discountLabel,
            discountAmount = order.discountAmount,
            paymentReference = order.paymentReference
        )

        showReceiptDialog(order, receipt)
    }

    private fun formatOrderStatus(status: CafeOrderStatus): String {
        return when (status) {
            CafeOrderStatus.PENDING -> getString(R.string.pending)
            CafeOrderStatus.PREPARING -> getString(R.string.preparing)
            CafeOrderStatus.COMPLETED -> getString(R.string.completed)
            CafeOrderStatus.CANCELLED -> getString(R.string.cancelled)
        }
    }

    private fun CafeOrder.toReceiptLines(): List<ReceiptLine> {
        if (lineItems.isNotEmpty()) {
            return lineItems.map { line ->
                ReceiptLine(
                    label = formatReceiptProductName(line),
                    quantity = line.quantity,
                    lineTotal = line.lineTotal
                )
            }
        }

        val labels = orderedItems.ifEmpty { listOf(itemsSummary) }.filter(String::isNotBlank)
        if (labels.isEmpty()) {
            return emptyList()
        }
        val fallbackLineTotal = if (labels.size > 1) total / labels.size else total
        return labels.map { label ->
            ReceiptLine(
                label = label,
                quantity = 1,
                lineTotal = fallbackLineTotal
            )
        }
    }

    private fun formatReceiptProductName(line: CafeOrderLine): String {
        val isPlainVariant = line.variantName.isBlank() ||
            line.variantName.equals("standard", ignoreCase = true) ||
            line.variantName.equals("combo", ignoreCase = true)
        return if (isPlainVariant) line.productName else "${line.productName} (${line.variantName})"
    }

    private fun formatOrderType(orderType: String): String {
        return when (orderType.trim().lowercase(Locale.US)) {
            "takeout", "take_away", "take away" -> getString(R.string.take_away)
            "delivery" -> getString(R.string.delivery)
            else -> getString(R.string.dine_in)
        }
    }

    private fun formatPaymentMethod(paymentMethod: String): String {
        return when (paymentMethod.trim().lowercase(Locale.US)) {
            "gcash" -> getString(R.string.receipt_paid_via_gcash)
            "maya" -> getString(R.string.maya)
            "paymongo" -> getString(R.string.paymongo)
            else -> getString(R.string.cash)
        }
    }

    // CHANGE: Orders — filter popup gains two checkable toggles (GCash
    // Orders, Take Out Orders) that layer on top of the existing sort
    // and status options. The Reset item also clears these toggles.
    private fun showOrdersFilterMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, getString(R.string.orders_sort_default))
        popup.menu.add(0, 8, 1, getString(R.string.orders_sort_date_desc))
        popup.menu.add(0, 2, 2, getString(R.string.orders_sort_total_desc))
        popup.menu.add(0, 3, 3, getString(R.string.orders_sort_total_asc))
        popup.menu.add(0, 4, 4, getString(R.string.orders_sort_items_desc))
        popup.menu.add(0, 6, 5, getString(R.string.orders_filter_gcash)).apply {
            isCheckable = true
            isChecked = filterGcashOnly
        }
        popup.menu.add(0, 7, 6, getString(R.string.orders_filter_takeout)).apply {
            isCheckable = true
            isChecked = filterTakeoutOnly
        }
        popup.menu.add(0, 9, 7, getString(R.string.orders_filter_today)).apply {
            isCheckable = true
            isChecked = filterTodayOnly
        }
        popup.menu.add(0, 5, 8, getString(R.string.orders_sort_reset))
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> orderSort = OrderSort.DEFAULT
                2 -> orderSort = OrderSort.TOTAL_DESC
                3 -> orderSort = OrderSort.TOTAL_ASC
                4 -> orderSort = OrderSort.ITEMS_DESC
                5 -> {
                    orderSort = OrderSort.DEFAULT
                    selectedOrderStatus = null
                    orderSearchQuery = ""
                    binding.ordersContent.etOrderSearch.setText("")
                    filterGcashOnly = false
                    filterTakeoutOnly = false
                    filterTodayOnly = false
                }
                6 -> filterGcashOnly = !filterGcashOnly
                7 -> filterTakeoutOnly = !filterTakeoutOnly
                8 -> orderSort = OrderSort.DATE_DESC
                9 -> filterTodayOnly = !filterTodayOnly
            }
            ordersPage = 0
            applyOrderFilters()
            true
        }
        popup.show()
    }

    private fun startOrdersAutoRefresh() {
        if (ordersRefreshJob?.isActive == true) {
            return
        }

        ordersRefreshJob = lifecycleScope.launch {
            while (true) {
                delay(ORDERS_REFRESH_INTERVAL_MS)
                loadOrdersFromSupabase(showError = false, force = true)
            }
        }
    }

    private fun stopOrdersAutoRefresh() {
        ordersRefreshJob?.cancel()
        ordersRefreshJob = null
    }

    private fun loadOrdersFromSupabase(
        showError: Boolean = false,
        force: Boolean = false,
        orderToKeepVisible: CafeOrder? = null
    ) {
        if (isOrdersLoading) {
            return
        }

        val hasFreshOrders = orders.isNotEmpty() &&
            System.currentTimeMillis() - lastOrdersLoadedAtMs < ORDERS_REFRESH_INTERVAL_MS
        if (!force && hasFreshOrders) {
            orderToKeepVisible?.let { addOrReplaceOrder(it, reveal = true) } ?: applyOrderFilters()
            return
        }

        lifecycleScope.launch {
            isOrdersLoading = true
            try {
                val fetchedOrders = orderRepository.fetchOrders()
                orders.clear()
                orders.addAll(fetchedOrders)
                val visibleOrder = orderToKeepVisible?.let { savedOrder ->
                    fetchedOrders.firstOrNull { it.id == savedOrder.id } ?: savedOrder
                }
                if (visibleOrder != null && orders.none { it.id == visibleOrder.id }) {
                    orders.add(visibleOrder)
                }
                lastOrdersLoadedAtMs = System.currentTimeMillis()
                handleNewOrderArrivals(orders.toList())
                visibleOrder?.let(::revealOrderInOrders) ?: applyOrderFilters()
            } catch (exception: Exception) {
                if (showError) {
                    showErrorDialog(
                        this@MainActivity,
                        getString(
                            R.string.orders_load_failed,
                            exception.message ?: "Please try again."
                        )
                    )
                }
            } finally {
                isOrdersLoading = false
            }
        }
    }

    private fun handleProductGroupClick(group: com.example.zejioscafese.pos.data.model.ProductGroup) {
        if (currentSection != Section.POS) {
            renderSection(Section.POS)
        }
        if (!group.isOrderable) {
            showProductUnavailableDialog(group)
            return
        }
        val singleVariant = group.singleVariant
        if (singleVariant != null) {
            viewModel.increaseProduct(singleVariant)
            setCheckoutExpanded(expanded = true, animate = true)
        } else {
            showSizePickerDialog(group)
        }
    }

    private fun showProductUnavailableDialog(group: com.example.zejioscafese.pos.data.model.ProductGroup) {
        val reason = group.unavailableReason
            ?: getString(R.string.product_unavailable_fallback)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.product_unavailable_title))
            .setMessage(
                getString(
                    R.string.product_unavailable_message,
                    group.displayName,
                    reason
                )
            )
            .setPositiveButton(android.R.string.ok, null)
            .showStyledDialog(this)
    }

    private fun showSizePickerDialog(group: com.example.zejioscafese.pos.data.model.ProductGroup) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_size_picker, null, false)
        val tvName = dialogView.findViewById<TextView>(R.id.tvSizePickerProductName)
        val ivImage = dialogView.findViewById<com.google.android.material.imageview.ShapeableImageView>(
            R.id.ivSizePickerImage
        )
        val container = dialogView.findViewById<LinearLayout>(R.id.sizePickerVariantsContainer)

        tvName.text = group.displayName
        com.example.zejioscafese.pos.ui.RemoteImageLoader.load(ivImage, group.imageUrl, group.imageResId)

        fun renderVariants() {
            container.removeAllViews()
            val quantities = viewModel.orderQuantities.value.orEmpty()
            group.variants.forEach { variant ->
                val row = layoutInflater.inflate(
                    R.layout.item_size_picker_variant,
                    container,
                    false
                )
                val tvVariantName = row.findViewById<TextView>(R.id.tvSizePickerVariantName)
                val tvPrice = row.findViewById<TextView>(R.id.tvSizePickerVariantPrice)
                val tvStock = row.findViewById<TextView>(R.id.tvSizePickerVariantStock)
                val btnAdd = row.findViewById<MaterialButton>(R.id.btnSizePickerAdd)
                val stepper = row.findViewById<LinearLayout>(R.id.sizePickerStepper)
                val btnDec = row.findViewById<MaterialButton>(R.id.btnSizePickerDecrease)
                val btnInc = row.findViewById<MaterialButton>(R.id.btnSizePickerIncrease)
                val tvQty = row.findViewById<TextView>(R.id.tvSizePickerQuantity)

                val variantDisplay = variant.sourceVariantName?.takeIf { it.isNotBlank() }
                    ?: variant.name
                tvVariantName.text = variantDisplay
                tvPrice.text = getString(R.string.currency_format, variant.price)

                val isOut = variant.stockLeft <= 0
                tvStock.text = if (isOut) {
                    variant.unavailableReason ?: getString(R.string.size_picker_out_of_stock)
                } else {
                    getString(R.string.stock_left_format, variant.stockLeft)
                }

                val qty = quantities[variant.id] ?: 0
                val inCart = qty > 0
                btnAdd.visibility = if (inCart) View.GONE else View.VISIBLE
                stepper.visibility = if (inCart) View.VISIBLE else View.GONE
                tvQty.text = qty.toString()

                btnAdd.isEnabled = !isOut
                btnInc.isEnabled = !isOut || qty > 0

                btnAdd.setOnClickListener {
                    viewModel.increaseProduct(variant)
                    renderVariants()
                }
                btnInc.setOnClickListener {
                    viewModel.increaseProduct(variant)
                    renderVariants()
                }
                btnDec.setOnClickListener {
                    if (qty <= 1) {
                        viewModel.removeProduct(variant.id)
                    } else {
                        viewModel.decreaseProduct(variant)
                    }
                    renderVariants()
                }

                container.addView(row)
            }
        }

        renderVariants()

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.size_picker_done)) { d, _ ->
                d.dismiss()
                setCheckoutExpanded(expanded = true, animate = true)
            }
            .showStyledDialog(this)
    }

    private fun handleProductDecrease(product: Product) {
        val currentQty = viewModel.orderQuantities.value?.get(product.id) ?: 0
        if (currentQty <= 1) {
            showRemoveItemConfirmation(product.name) {
                viewModel.removeProduct(product.id)
            }
        } else {
            viewModel.decreaseProduct(product)
        }
    }

    private fun handleOrderItemDecrease(item: com.example.zejioscafese.pos.data.model.OrderItem) {
        if (item.quantity <= 1) {
            showRemoveItemConfirmation(item.product.name) {
                viewModel.removeProduct(item.product.id)
            }
        } else {
            viewModel.decreaseOrderItem(item)
        }
    }

    private fun showRemoveItemConfirmation(productName: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.remove_item_title))
            .setMessage(getString(R.string.remove_item_message, productName))
            .setPositiveButton(getString(R.string.remove_item_confirm)) { _, _ -> onConfirm() }
            .setNegativeButton(getString(R.string.remove_item_cancel), null)
            .showStyledDialog(this)
    }

    private fun toggleCategoryExpansion() {
        isCategoryExpanded = !isCategoryExpanded
        val rv = binding.rvCategories
        categoryAdapter.expandedMode = isCategoryExpanded
        if (isCategoryExpanded) {
            val isTablet = resources.configuration.smallestScreenWidthDp >= 600
            val spanCount = if (isTablet) 4 else 3
            rv.layoutManager = GridLayoutManager(this, spanCount)
            rv.updateLayoutParams<ViewGroup.LayoutParams> {
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
            binding.btnBrowseMenu.rotation = 180f
        } else {
            rv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            rv.updateLayoutParams<ViewGroup.LayoutParams> {
                height = resources.getDimensionPixelSize(R.dimen.category_tab_height)
            }
            binding.btnBrowseMenu.rotation = 0f
        }
    }

    private fun applyOrderTypeSelection(type: PosViewModel.OrderType) {
        // Delivery has been removed from the UI; the ViewModel still exposes
        // the enum, but the cart only offers Dine In and Take Away.
        val buttons = listOf(
            binding.btnOrderTypeDineIn to PosViewModel.OrderType.DINE_IN,
            binding.btnOrderTypeTakeAway to PosViewModel.OrderType.TAKE_AWAY
        )
        buttons.forEach { (view, value) ->
            val isSelected = value == type
            view.setBackgroundResource(
                if (isSelected) R.drawable.bg_segment_selected else R.drawable.bg_segment_unselected
            )
            view.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (isSelected) R.color.white else R.color.pos_text_primary
                )
            )
        }
    }

    private fun addOrReplaceOrder(order: CafeOrder, reveal: Boolean = false) {
        // Append so the new order joins the end of the queue. The list is
        // ordered oldest -> newest so that the staff sees the longest-waiting
        // customer at the top of the Orders / Preparing screens.
        orders.removeAll { it.id == order.id }
        orders.add(order)
        handleNewOrderArrivals(orders.toList())
        if (reveal) {
            revealOrderInOrders(order)
        } else {
            applyOrderFilters()
        }
    }

    private fun revealOrderInOrders(order: CafeOrder) {
        selectedOrderStatus = order.status
        orderSearchQuery = ""
        if (binding.ordersContent.etOrderSearch.text?.isNotEmpty() == true) {
            binding.ordersContent.etOrderSearch.setText("")
        }

        val visibleOrders = orders.filter { it.status == order.status }
        val sortedOrders = when (orderSort) {
            OrderSort.DEFAULT -> if (order.status == CafeOrderStatus.COMPLETED) {
                visibleOrders.sortedByDescending { it.completedSortMillis() }
            } else {
                visibleOrders
            }
            OrderSort.DATE_DESC -> visibleOrders.sortedByDescending { it.createdAtMillis }
            OrderSort.TOTAL_DESC -> visibleOrders.sortedByDescending { it.total }
            OrderSort.TOTAL_ASC -> visibleOrders.sortedBy { it.total }
            OrderSort.ITEMS_DESC -> visibleOrders.sortedByDescending { it.itemCount }
        }
        val orderIndex = sortedOrders.indexOfFirst { it.id == order.id }
        ordersPage = if (orderIndex >= 0) {
            orderIndex / ORDERS_PAGE_SIZE
        } else {
            0
        }
        applyOrderFilters()
    }

    private fun showOrderItemsDialog(order: CafeOrder) {
        val itemsToShow = order.orderedItems.ifEmpty { listOf(order.itemsSummary) }
        val isInteractive = order.status == CafeOrderStatus.PREPARING
        // CHANGE: Partial completion — split items into "Completed" (server
        // has already deducted their ingredients, locked-in) and "In Progress"
        // (still pending). Completed items render as a static check; in-
        // progress items keep the editable checkbox so the barista can mark
        // them as done before tapping "Mark as completed" again.
        val deductedIndexes = order.orderedItemVariantIds
            .mapIndexedNotNull { index, variantId ->
                index.takeIf { variantId in order.deductedItemVariantIds }
            }
            .toSet()
        val completedIndexesFromDatabase = order.orderedItemVariantIds
            .mapIndexedNotNull { index, variantId ->
                index.takeIf { variantId in order.completedItemVariantIds }
            }
            .toMutableSet()
        val savedCompletedSet = orderItemCompletion[order.id]
            ?: if (order.status == CafeOrderStatus.COMPLETED) {
                itemsToShow.indices.toMutableSet()
            } else {
                completedIndexesFromDatabase
            }
        // Deducted items are always considered completed (server-locked).
        val workingCompletedSet = (savedCompletedSet + deductedIndexes).toMutableSet()
        // Snapshot of everything already marked completed when the dialog opens.
        // These render in the Completed section and cannot be unchecked.
        val lockedCompletedIndexes = workingCompletedSet.toSet()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 16.dp(), 20.dp(), 8.dp())
        }

        val progressLabel = createDialogText(
            text = "",
            textSizeSp = 13f,
            typeface = Typeface.DEFAULT_BOLD,
            textColorRes = R.color.pos_text_secondary
        )
        container.addView(progressLabel)

        val checkBoxes = mutableMapOf<Int, CheckBox>()

        fun refreshProgressLabel() {
            val prepared = workingCompletedSet.size
            val total = itemsToShow.size
            progressLabel.text = getString(R.string.order_item_progress_format, prepared, total)
        }

        // ── Completed section (read-only) ─────────────────────────────────
        // Items here are either server-locked (ingredients deducted) or were
        // previously saved as completed. Either way they can't be unchecked.
        if (lockedCompletedIndexes.isNotEmpty()) {
            container.addView(
                createDialogText(
                    text = getString(R.string.order_items_section_completed, lockedCompletedIndexes.size),
                    textSizeSp = 12f,
                    typeface = Typeface.DEFAULT_BOLD,
                    textColorRes = R.color.pos_secondary,
                    topMarginDp = 12
                )
            )
            lockedCompletedIndexes.sorted().forEach { index ->
                val label = itemsToShow.getOrNull(index) ?: return@forEach
                val row = TextView(this).apply {
                    text = "✓  $label"
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pos_secondary))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 6.dp() }
                }
                container.addView(row)
            }
        }

        // ── In Progress section (editable checkboxes) ────────────────────
        val inProgressIndexes = itemsToShow.indices.filterNot { it in lockedCompletedIndexes }
        if (inProgressIndexes.isNotEmpty()) {
            container.addView(
                createDialogText(
                    text = getString(R.string.order_items_section_in_progress, inProgressIndexes.size),
                    textSizeSp = 12f,
                    typeface = Typeface.DEFAULT_BOLD,
                    textColorRes = R.color.pos_primary,
                    topMarginDp = 12
                )
            )
            inProgressIndexes.forEach { index ->
                val label = itemsToShow[index]
                val checkBox = CheckBox(this).apply {
                    text = label
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pos_text_primary))
                    isChecked = workingCompletedSet.contains(index)
                    isEnabled = isInteractive
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 6.dp() }
                }
                checkBoxes[index] = checkBox
                container.addView(checkBox)
            }
        }
        refreshProgressLabel()

        val scrollView = ScrollView(this).apply {
            addView(container)
        }

        val builder = AlertDialog.Builder(this)
            .setTitle(getString(R.string.order_items_dialog_title, order.id))
            .setView(scrollView)
            .setNegativeButton(android.R.string.cancel, null)

        if (isInteractive) {
            builder.setPositiveButton(R.string.order_action_save_item_progress, null)
        } else {
            builder.setPositiveButton(android.R.string.ok, null)
        }

        val dialog = builder.showStyledDialog(this)

        if (!isInteractive) {
            return
        }

        val primaryButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

        fun refreshPrimaryButton() {
            val allChecked = workingCompletedSet.size == itemsToShow.size && itemsToShow.isNotEmpty()
            primaryButton.text = getString(
                if (allChecked) {
                    R.string.order_action_mark_completed
                } else {
                    R.string.order_action_save_item_progress
                }
            )
            primaryButton.isEnabled = itemsToShow.isNotEmpty()
        }
        refreshPrimaryButton()

        checkBoxes.forEach { (index, checkBox) ->
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    workingCompletedSet.add(index)
                } else {
                    workingCompletedSet.remove(index)
                }
                refreshProgressLabel()
                refreshPrimaryButton()
            }
        }

        primaryButton.setOnClickListener {
            val previousCompletion = orderItemCompletion[order.id]?.toMutableSet()
            val completedVariantIds = workingCompletedSet
                .mapNotNull { index -> order.orderedItemVariantIds.getOrNull(index) }
                .toSet()
            val updatedOrder = order.copy(completedItemVariantIds = completedVariantIds)
            orderItemCompletion[order.id] = workingCompletedSet
            applyOrderFilters()

            lifecycleScope.launch {
                try {
                    if (order.orderedItemVariantIds.isNotEmpty()) {
                        orderRepository.updateOrderItemCompletion(order.id, completedVariantIds)
                    }

                    if (workingCompletedSet.size == itemsToShow.size && itemsToShow.isNotEmpty()) {
                        updateOrderStatus(order, CafeOrderStatus.COMPLETED)
                    } else {
                        loadOrdersFromSupabase(
                            showError = true,
                            force = true,
                            orderToKeepVisible = updatedOrder
                        )
                        showSuccessDialog(
                            this@MainActivity,
                            getString(R.string.order_item_progress_saved_message, order.id)
                        )
                    }
                } catch (exception: Exception) {
                    if (previousCompletion == null) {
                        orderItemCompletion.remove(order.id)
                    } else {
                        orderItemCompletion[order.id] = previousCompletion
                    }
                    applyOrderFilters()
                    showErrorDialog(
                        this@MainActivity,
                        getString(
                            R.string.order_item_progress_save_failed,
                            exception.message ?: "Please try again."
                        )
                    )
                }
            }
            dialog.dismiss()
        }
    }

    private fun showCheckoutReviewDialog() {
        val orderItems = viewModel.orderItems.value.orEmpty()
        if (orderItems.isEmpty()) {
            showInfoDialog(this, getString(R.string.checkout_requires_items))
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
        content.addView(
            createDialogText(
                text = formatCurrency(subtotal),
                textSizeSp = 16f
            )
        )

        // Payment method selector (moved from cart to modal)
        content.addView(createSectionLabel(getString(R.string.payment_method)))
        val paymentMethodGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 6.dp() }
        }
        val rbCash = RadioButton(this).apply {
            id = View.generateViewId()
            text = getString(R.string.cash)
            isChecked = (viewModel.selectedPaymentMethod.value ?: PosViewModel.PaymentMethod.CASH) == PosViewModel.PaymentMethod.CASH
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pos_text_primary))
            textSize = 13f
        }
        val rbGcash = RadioButton(this).apply {
            id = View.generateViewId()
            text = getString(R.string.gcash)
            isChecked = (viewModel.selectedPaymentMethod.value ?: PosViewModel.PaymentMethod.CASH) == PosViewModel.PaymentMethod.GCASH
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pos_text_primary))
            textSize = 13f
        }
        val rbPaymongo = RadioButton(this).apply {
            id = View.generateViewId()
            text = getString(R.string.paymongo_checkout_method)
            isChecked = (viewModel.selectedPaymentMethod.value ?: PosViewModel.PaymentMethod.CASH) == PosViewModel.PaymentMethod.PAYMONGO
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pos_text_primary))
            textSize = 13f
        }
        paymentMethodGroup.addView(rbCash)
        paymentMethodGroup.addView(rbGcash)
        paymentMethodGroup.addView(rbPaymongo)
        content.addView(paymentMethodGroup)

        val customerNameInput = createDialogInput(
            hint = getString(R.string.checkout_customer_name_hint),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        )
        content.addView(createSectionLabel(getString(R.string.order_field_customer_name)))
        content.addView(customerNameInput)

        content.addView(createSectionLabel(getString(R.string.discount)))

        // CHANGE: Discounts — snapshot the VM's available discounts when the
        // dialog opens, then partition into built-ins (PWD/Senior) vs custom.
        // The mutable copy is replaced whenever the manager creates a new
        // custom discount via the "+ New" row at the bottom of the spinner.
        val availableDiscounts = viewModel.availableDiscounts.value.orEmpty().toMutableList()
        val pwdDiscount = availableDiscounts.firstOrNull { it.id == Discount.PWD_ID }
        val seniorDiscount = availableDiscounts.firstOrNull { it.id == Discount.SENIOR_ID }
        val customDiscountsRef = arrayOf(
            availableDiscounts.filterNot { it.isBuiltIn }.toMutableList()
        )

        val discountTypeGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 6.dp() }
        }
        fun makeDiscountRadio(label: String, enabled: Boolean = true) = RadioButton(this).apply {
            id = View.generateViewId()
            text = label
            isEnabled = enabled
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pos_text_primary))
            textSize = 13f
            layoutParams = RadioGroup.LayoutParams(0, RadioGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val rbDiscountNone = makeDiscountRadio(getString(R.string.discount_none))
        val rbDiscountPwd = makeDiscountRadio(
            label = pwdDiscount?.displayLabel() ?: getString(R.string.discount_pwd_default),
            enabled = pwdDiscount != null
        )
        val rbDiscountSenior = makeDiscountRadio(
            label = seniorDiscount?.displayLabel() ?: getString(R.string.discount_senior_default),
            enabled = seniorDiscount != null
        )
        val rbDiscountOther = makeDiscountRadio(getString(R.string.discount_other))
        rbDiscountNone.isChecked = true
        discountTypeGroup.addView(rbDiscountNone)
        discountTypeGroup.addView(rbDiscountPwd)
        discountTypeGroup.addView(rbDiscountSenior)
        discountTypeGroup.addView(rbDiscountOther)
        content.addView(discountTypeGroup)

        // Container for the "Other" dropdown + "New discount" button. Hidden
        // unless the Other radio is selected.
        val otherDiscountContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 6.dp() }
            visibility = View.GONE
        }
        val customDiscountSpinner = Spinner(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val customDiscountEmptyView = createDialogText(
            text = getString(R.string.discount_no_custom_available),
            textSizeSp = 12f,
            textColorRes = R.color.pos_text_secondary,
            topMarginDp = 4
        ).apply { visibility = View.GONE }
        val newDiscountButton = com.google.android.material.button.MaterialButton(this).apply {
            text = getString(R.string.discount_create_new)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                36.dp()
            ).apply { topMargin = 6.dp() }
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, android.R.color.transparent))
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pos_primary))
            strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.pos_primary))
            strokeWidth = 1.dp()
            cornerRadius = 10.dp()
            textSize = 12f
        }
        otherDiscountContainer.addView(customDiscountSpinner)
        otherDiscountContainer.addView(customDiscountEmptyView)
        otherDiscountContainer.addView(newDiscountButton)
        content.addView(otherDiscountContainer)

        fun rebuildCustomDiscountSpinner() {
            val custom = customDiscountsRef[0]
            if (custom.isEmpty()) {
                customDiscountSpinner.visibility = View.GONE
                customDiscountEmptyView.visibility = View.VISIBLE
                customDiscountSpinner.adapter = null
            } else {
                customDiscountSpinner.visibility = View.VISIBLE
                customDiscountEmptyView.visibility = View.GONE
                customDiscountSpinner.adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_spinner_dropdown_item,
                    custom.map(Discount::displayLabel)
                )
            }
        }
        rebuildCustomDiscountSpinner()

        val discountSummaryView = createDialogText(
            text = "",
            textSizeSp = 13f,
            textColorRes = R.color.pos_secondary,
            topMarginDp = 2
        )
        discountSummaryView.visibility = View.GONE
        content.addView(discountSummaryView)

        content.addView(createSectionLabel(getString(R.string.total)))
        val checkoutTotalView = createDialogText(
            text = formatCurrency(total),
            textSizeSp = 18f,
            typeface = Typeface.DEFAULT_BOLD,
            textColorRes = R.color.pos_primary
        )
        content.addView(checkoutTotalView)

        val paymentInput = createDialogInput(
            hint = getString(R.string.checkout_cash_received_hint),
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        )
        val paymentTenderLabel = createSectionLabel(getString(R.string.cash))
        content.addView(paymentTenderLabel)
        content.addView(paymentInput)

        val paymentHelper = createDialogText(
            text = getString(R.string.checkout_change_due_pending),
            textSizeSp = 13f,
            textColorRes = R.color.pos_text_secondary,
            topMarginDp = 6
        )
        content.addView(paymentHelper)

        // Create scrollable content
        val scrollView = ScrollView(this).apply {
            addView(content)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0
            ).apply { weight = 1f }
        }

        // Create button container (fixed at bottom)
        val buttonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16.dp() }
            setPadding(16.dp(), 8.dp(), 16.dp(), 16.dp())
        }

        val cancelButton = com.google.android.material.button.MaterialButton(this).apply {
            text = getString(android.R.string.cancel)
            layoutParams = LinearLayout.LayoutParams(0, 44.dp(), 1f)
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, android.R.color.transparent))
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pos_primary))
            strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.pos_primary))
            strokeWidth = 2.dp()
            cornerRadius = 12.dp()
        }

        val confirmButton = com.google.android.material.button.MaterialButton(this).apply {
            text = getString(R.string.checkout_confirm_payment)
            layoutParams = LinearLayout.LayoutParams(0, 44.dp(), 1.4f).apply { marginStart = 10.dp() }
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.pos_secondary))
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.white))
            cornerRadius = 12.dp()
        }

        buttonContainer.addView(cancelButton)
        buttonContainer.addView(confirmButton)

        // Create main container with scrollView + buttons
        val mainContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 20.dp(), 0, 0)
            addView(scrollView)
            addView(buttonContainer)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.checkout_review_title))
            .setView(mainContainer)
            .create()

        fun resolveDiscount(): ResolvedDiscount {
            val selected: Discount = when (discountTypeGroup.checkedRadioButtonId) {
                rbDiscountPwd.id -> pwdDiscount
                rbDiscountSenior.id -> seniorDiscount
                rbDiscountOther.id -> {
                    val pos = customDiscountSpinner.selectedItemPosition
                    customDiscountsRef[0].getOrNull(pos)
                }
                else -> null
            } ?: return ResolvedDiscount.NONE

            val amount = selected.amountFor(total)
            if (amount <= 0.0) return ResolvedDiscount.NONE
            return ResolvedDiscount(
                discount = selected,
                label = selected.displayLabel(),
                amount = amount
            )
        }

        fun syncOtherContainerVisibility() {
            otherDiscountContainer.visibility = if (
                discountTypeGroup.checkedRadioButtonId == rbDiscountOther.id
            ) View.VISIBLE else View.GONE
        }

        fun selectedPaymentMethod(): PosViewModel.PaymentMethod {
            return when (paymentMethodGroup.checkedRadioButtonId) {
                rbGcash.id -> PosViewModel.PaymentMethod.GCASH
                rbPaymongo.id -> PosViewModel.PaymentMethod.PAYMONGO
                else -> PosViewModel.PaymentMethod.CASH
            }
        }

        fun refreshPaymentState() {
            val discount = resolveDiscount()
            val finalTotal = total - discount.amount
            val method = selectedPaymentMethod()
            val needsCashTender = method != PosViewModel.PaymentMethod.PAYMONGO
            val cashReceived = if (needsCashTender) {
                paymentInput.text?.toString().orEmpty().toCashAmount()
            } else {
                finalTotal
            }
            val change = cashReceived?.minus(finalTotal)
            val isValid = if (needsCashTender) {
                cashReceived != null && change != null && change >= 0
            } else {
                finalTotal >= 0
            }

            // Keep the ViewModel in sync so discountId/percent ride along to
            // the saved order. Calling setDiscount on every change is cheap
            // (it only updates LiveData) but ensures checkout() sees the
            // right metadata when the confirm button is tapped.
            viewModel.setDiscount(discount.discount)

            syncOtherContainerVisibility()
            checkoutTotalView.text = formatCurrency(finalTotal)
            discountSummaryView.visibility = if (discount.amount > 0) View.VISIBLE else View.GONE
            discountSummaryView.text = if (discount.amount > 0) {
                "${discount.label}: -${formatCurrency(discount.amount)}"
            } else {
                ""
            }
            paymentTenderLabel.visibility = if (needsCashTender) View.VISIBLE else View.GONE
            paymentInput.visibility = if (needsCashTender) View.VISIBLE else View.GONE
            confirmButton.text = if (method == PosViewModel.PaymentMethod.PAYMONGO) {
                getString(R.string.checkout_continue_paymongo)
            } else {
                getString(R.string.checkout_confirm_payment)
            }
            confirmButton.isEnabled = isValid && !isCheckoutSaving
            paymentHelper.text = if (needsCashTender) {
                when {
                    cashReceived == null -> getString(R.string.checkout_change_due_pending)
                    change == null || change < 0 -> getString(R.string.checkout_cash_required)
                    else -> getString(R.string.checkout_change_due, formatCurrency(change))
                }
            } else {
                getString(R.string.checkout_paymongo_pending)
            }
        }

        // CHANGE: Discounts — must listen on the RadioGroup (fires AFTER the
        // group's checkedRadioButtonId updates) instead of the individual
        // RadioButtons (which fire BEFORE the group state changes, causing
        // resolveDiscount to read the previous selection).
        discountTypeGroup.setOnCheckedChangeListener { _, _ -> refreshPaymentState() }
        paymentMethodGroup.setOnCheckedChangeListener { _, _ -> refreshPaymentState() }
        customDiscountSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (discountTypeGroup.checkedRadioButtonId == rbDiscountOther.id) {
                    refreshPaymentState()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        newDiscountButton.setOnClickListener {
            showCreateDiscountDialog { created ->
                // Append + refresh + auto-select the new discount.
                customDiscountsRef[0] = (customDiscountsRef[0] + created).toMutableList()
                viewModel.onDiscountCreated(created)
                rebuildCustomDiscountSpinner()
                discountTypeGroup.check(rbDiscountOther.id)
                customDiscountSpinner.setSelection(customDiscountsRef[0].lastIndex)
                refreshPaymentState()
            }
        }
        paymentInput.doAfterTextChanged { refreshPaymentState() }
        refreshPaymentState()

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        confirmButton.setOnClickListener {
            val discount = resolveDiscount()
            val finalTotal = total - discount.amount
            val selectedMethod = selectedPaymentMethod()

            fun savePaidCheckout(cashReceived: Double, paymentReference: String? = null) {
                viewModel.setPaymentMethod(selectedMethod)

                pendingCheckoutReceipt = PendingCheckoutReceipt(
                    customerName = customerNameInput.text?.toString()?.trim()?.takeIf(String::isNotBlank),
                    cashReceived = cashReceived,
                    subtotal = subtotal,
                    total = finalTotal,
                    lines = receiptLines,
                    discountLabel = discount.label,
                    discountAmount = discount.amount,
                    paymentReference = paymentReference
                )

                dialog.dismiss()
                viewModel.checkout(
                    customerName = customerNameInput.text?.toString(),
                    discountLabel = discount.label,
                    discountAmount = discount.amount,
                    finalTotal = finalTotal,
                    paymentReference = paymentReference,
                    paymentProvider = if (selectedMethod == PosViewModel.PaymentMethod.PAYMONGO) "paymongo" else null,
                    paymentStatus = if (selectedMethod == PosViewModel.PaymentMethod.PAYMONGO) "paid" else null
                )
            }

            if (selectedMethod == PosViewModel.PaymentMethod.PAYMONGO) {
                confirmButton.isEnabled = false
                lifecycleScope.launch {
                    try {
                        val orderNumber = viewModel.orderNumber.value.orEmpty()
                        val checkoutLines = viewModel.orderItems.value.orEmpty().toCheckoutOrderLines()
                        val checkoutSession = payMongoCheckoutRepository.createCheckoutSession(
                            orderNumber = orderNumber,
                            customerName = customerNameInput.text?.toString()?.trim(),
                            amount = finalTotal,
                            lines = receiptLines.map { line ->
                                PayMongoCheckoutLine(
                                    name = line.label,
                                    quantity = line.quantity,
                                    lineTotal = line.lineTotal
                                )
                            }
                        )
                        val pending = PendingPayMongoCheckout(
                            orderNumber = orderNumber,
                            sessionId = checkoutSession.id,
                            checkoutUrl = checkoutSession.checkoutUrl,
                            referenceNumber = checkoutSession.referenceNumber,
                            status = checkoutSession.status,
                            customerName = customerNameInput.text?.toString()?.trim()?.takeIf(String::isNotBlank),
                            subtotal = subtotal,
                            tax = viewModel.tax.value ?: 0.0,
                            total = finalTotal,
                            discountLabel = discount.label,
                            discountAmount = discount.amount,
                            discountId = discount.discount?.id,
                            discountPercent = discount.discount?.percent,
                            orderType = viewModel.selectedOrderType.value.toDatabaseOrderType(),
                            items = checkoutLines,
                            receiptLines = receiptLines
                        )
                        savePendingPayMongoCheckout(pending)
                        dialog.dismiss()
                        confirmButton.isEnabled = !isCheckoutSaving
                        showPayMongoCheckoutDialog(pending)
                    } catch (exception: Exception) {
                        confirmButton.isEnabled = !isCheckoutSaving
                        showErrorDialog(
                            this@MainActivity,
                            getString(
                                R.string.paymongo_checkout_create_failed,
                                NetworkErrorFormatter.toUserMessage(
                                    exception = exception,
                                    fallbackMessage = getString(R.string.try_again)
                                )
                            )
                        )
                    }
                }
                return@setOnClickListener
            }

            val cashReceived = paymentInput.text?.toString().orEmpty().toCashAmount()
            if (cashReceived == null || cashReceived < finalTotal) {
                refreshPaymentState()
                return@setOnClickListener
            }
            savePaidCheckout(cashReceived = cashReceived)
        }

        dialog.show()
    }

    private fun showPayMongoCheckoutDialog(pending: PendingPayMongoCheckout) {
        val session = pending.toSession()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8.dp(), 8.dp(), 8.dp(), 0)
        }

        content.addView(
            createDialogText(
                text = getString(R.string.paymongo_checkout_message),
                textSizeSp = 13f,
                textColorRes = R.color.pos_text_secondary
            )
        )
        content.addView(
            createDialogText(
                text = getString(R.string.paymongo_amount_format, formatCurrency(pending.total)),
                textSizeSp = 14f,
                typeface = Typeface.DEFAULT_BOLD,
                topMarginDp = 12
            )
        )
        content.addView(
            createDialogText(
                text = getString(R.string.paymongo_session_format, session.id),
                textSizeSp = 13f,
                typeface = Typeface.MONOSPACE,
                topMarginDp = 4
            )
        )
        session.status?.let { status ->
            content.addView(
                createDialogText(
                    text = getString(R.string.paymongo_status_format, status),
                    textSizeSp = 13f,
                    textColorRes = R.color.pos_text_secondary,
                    topMarginDp = 4
                )
            )
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.paymongo_checkout_title)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.paymongo_open_checkout, null)
            .setNeutralButton(R.string.paymongo_verify_payment, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(session.checkoutUrl)))
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { verifyButton ->
                verifyButton.isEnabled = false
                lifecycleScope.launch {
                    val saved = verifyAndSavePayMongoCheckout(
                        pending = pending,
                        delayBeforeVerify = false,
                        showPendingDialog = false
                    )
                    if (saved) {
                        dialog.dismiss()
                    } else {
                        verifyButton.isEnabled = true
                    }
                }
            }
        }
        dialog.show()
    }

    private fun handlePayMongoReturn(intent: Intent?) {
        val uri = intent?.data ?: return
        if (!uri.isPayMongoReturnUri()) return

        val pending = loadPendingPayMongoCheckout()

        if (pending == null) {
            showInfoDialog(this, getString(R.string.paymongo_return_missing))
            return
        }

        val result = uri.getQueryParameter("result").orEmpty()
        if (result.equals("cancel", ignoreCase = true)) {
            showInfoDialog(this, getString(R.string.paymongo_payment_cancelled))
            showPayMongoCheckoutDialog(pending)
            return
        }

        lifecycleScope.launch {
            verifyAndSavePayMongoCheckout(
                pending = pending,
                delayBeforeVerify = true,
                showPendingDialog = true
            )
        }
    }

    private suspend fun verifyAndSavePayMongoCheckout(
        pending: PendingPayMongoCheckout,
        delayBeforeVerify: Boolean,
        showPendingDialog: Boolean
    ): Boolean {
        if (isPayMongoVerificationRunning) return false
        isPayMongoVerificationRunning = true
        updateCheckoutButtonState()

        return try {
            if (delayBeforeVerify) {
                delay(PAYMONGO_RETURN_VERIFY_DELAY_MS)
            }

            val status = try {
                payMongoCheckoutRepository.retrieveCheckoutSession(pending.sessionId)
            } catch (exception: Exception) {
                Log.e(TAG, "PayMongo verification request failed.", exception)
                throw exception
            }
            if (!status.isPaid) {
                showInfoDialog(
                    this,
                    getString(
                        R.string.paymongo_payment_not_paid,
                        status.status ?: getString(R.string.paymongo_status_unknown)
                    )
                )
                if (showPendingDialog) {
                    showPayMongoCheckoutDialog(pending.copy(status = status.status))
                }
                false
            } else {
                val paymentReference = status.paymentReference
                    ?: pending.referenceNumber
                    ?: status.id
                val savedOrder = try {
                    orderRepository.saveCheckoutOrder(
                        payload = pending.toCheckoutPayload(paymentReference),
                        suggestedOrderNumber = pending.orderNumber
                    )
                } catch (exception: Exception) {
                    Log.e(TAG, "PayMongo payment verified, but saving the paid order failed.", exception)
                    throw IllegalStateException(
                        getString(
                            R.string.paymongo_order_save_failed,
                            NetworkErrorFormatter.toUserMessage(
                                exception = exception,
                                fallbackMessage = getString(R.string.try_again)
                            )
                        ),
                        exception
                    )
                }
                finishPayMongoCheckout(savedOrder, pending, paymentReference)
                true
            }
        } catch (exception: Exception) {
            Log.e(TAG, "PayMongo checkout verification flow failed.", exception)
            showErrorDialog(
                this,
                getString(
                    R.string.paymongo_verify_failed,
                    NetworkErrorFormatter.toUserMessage(
                        exception = exception,
                        fallbackMessage = getString(R.string.try_again)
                    )
                )
            )
            if (showPendingDialog) {
                showPayMongoCheckoutDialog(pending)
            }
            false
        } finally {
            isPayMongoVerificationRunning = false
            updateCheckoutButtonState()
        }
    }

    private fun finishPayMongoCheckout(
        savedOrder: CafeOrder,
        pending: PendingPayMongoCheckout,
        paymentReference: String
    ) {
        clearPendingPayMongoCheckout()
        viewModel.completeExternalCheckout(savedOrder.id)
        addOrReplaceOrder(savedOrder, reveal = true)
        loadOrdersFromSupabase(
            showError = false,
            force = true,
            orderToKeepVisible = savedOrder
        )
        viewModel.refreshMenu()
        dashboardViewModel.refreshDashboard(force = true)
        showReceiptDialog(savedOrder, pending.toReceipt(paymentReference))
        setCheckoutExpanded(expanded = false, animate = true)
    }

    private fun PendingPayMongoCheckout.toCheckoutPayload(paymentReference: String): CheckoutOrderPayload {
        return CheckoutOrderPayload(
            customerName = customerName,
            subtotal = subtotal,
            tax = tax,
            total = total,
            discountLabel = discountLabel,
            discountAmount = discountAmount,
            discountId = discountId,
            discountPercent = discountPercent,
            paymentMethod = "paymongo",
            paymentProvider = "paymongo",
            paymentStatus = "paid",
            paymentReference = paymentReference,
            status = "preparing",
            items = items,
            orderType = orderType
        )
    }

    private fun savePendingPayMongoCheckout(pending: PendingPayMongoCheckout) {
        getSharedPreferences(PAYMENT_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_PAYMONGO_CHECKOUT, paymentPersistenceJson.encodeToString(pending))
            .apply()
    }

    private fun loadPendingPayMongoCheckout(): PendingPayMongoCheckout? {
        val prefs = getSharedPreferences(PAYMENT_PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PENDING_PAYMONGO_CHECKOUT, null) ?: return null
        return runCatching {
            paymentPersistenceJson.decodeFromString<PendingPayMongoCheckout>(raw)
        }.getOrElse {
            prefs.edit().remove(KEY_PENDING_PAYMONGO_CHECKOUT).apply()
            null
        }
    }

    private fun clearPendingPayMongoCheckout() {
        getSharedPreferences(PAYMENT_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PENDING_PAYMONGO_CHECKOUT)
            .apply()
    }

    private fun Uri.isPayMongoReturnUri(): Boolean {
        return scheme.equals(PAYMONGO_RETURN_SCHEME, ignoreCase = true) &&
            host.equals(PAYMONGO_RETURN_HOST, ignoreCase = true) &&
            path.equals(PAYMONGO_RETURN_PATH, ignoreCase = true)
    }

    private fun List<OrderItem>.toCheckoutOrderLines(): List<CheckoutOrderLine> {
        return map { item ->
            val product = item.product
            CheckoutOrderLine(
                productVariantId = product.id,
                sourceProductId = product.sourceProductId
                    ?: throw IllegalStateException("Missing product ID for ${product.name}. Refresh the menu and try again."),
                sourceProductName = product.sourceProductName ?: product.name,
                sourceVariantName = product.sourceVariantName ?: "Standard",
                unitPrice = product.price,
                quantity = item.quantity
            )
        }
    }

    private fun PosViewModel.OrderType?.toDatabaseOrderType(): String {
        return when (this ?: PosViewModel.OrderType.DINE_IN) {
            PosViewModel.OrderType.DINE_IN -> "dine_in"
            PosViewModel.OrderType.TAKE_AWAY -> "takeout"
            PosViewModel.OrderType.DELIVERY -> "delivery"
        }
    }

    private fun showReceiptDialog(order: CafeOrder, receipt: PendingCheckoutReceipt) {
        val customerName = receipt.customerName?.takeIf(String::isNotBlank)
            ?: order.customerName.ifBlank { getString(R.string.receipt_walk_in_customer) }
        val change = (receipt.cashReceived - receipt.total).coerceAtLeast(0.0)

        val receiptBinding = com.example.zejioscafese.databinding.DialogReceiptBinding.inflate(layoutInflater)
        receiptBinding.tvReceiptOrderId.text = order.id
        receiptBinding.tvReceiptTime.text = order.timeLabel
        receiptBinding.tvReceiptCustomer.text = customerName
        receiptBinding.tvReceiptOrderType.text = formatOrderType(order.orderType)
        receiptBinding.tvReceiptPayment.text = formatPaymentMethod(order.paymentMethod)
        val paymentReference = receipt.paymentReference
            ?: order.paymentReference?.trim()?.takeIf(String::isNotBlank)
        if (paymentReference.isNullOrBlank()) {
            receiptBinding.receiptPaymentReferenceRow.visibility = View.GONE
        } else {
            receiptBinding.receiptPaymentReferenceRow.visibility = View.VISIBLE
            receiptBinding.tvReceiptPaymentReference.text = paymentReference
        }
        receiptBinding.tvReceiptSubtotal.text = formatCurrency(receipt.subtotal)
        // CHANGE: Payment — surface the resolved discount on the receipt
        // when one was applied; otherwise the row stays collapsed.
        if (receipt.discountAmount > 0 && !receipt.discountLabel.isNullOrBlank()) {
            receiptBinding.receiptDiscountRow.visibility = View.VISIBLE
            receiptBinding.tvReceiptDiscountLabel.text = receipt.discountLabel
            receiptBinding.tvReceiptDiscountAmount.text = "-${formatCurrency(receipt.discountAmount)}"
        } else {
            receiptBinding.receiptDiscountRow.visibility = View.GONE
        }
        receiptBinding.tvReceiptTotal.text = formatCurrency(receipt.total)
        val isCashPayment = order.paymentMethod.equals("cash", ignoreCase = true)
        receiptBinding.tvReceiptTenderLabel.text = if (isCashPayment) {
            getString(R.string.receipt_cash_received_label)
        } else {
            getString(R.string.receipt_amount_paid_label)
        }
        receiptBinding.tvReceiptChangeLabel.text = if (isCashPayment) {
            getString(R.string.receipt_change_label)
        } else {
            getString(R.string.receipt_balance_label)
        }
        receiptBinding.tvReceiptCashReceived.text = formatCurrency(receipt.cashReceived)
        receiptBinding.tvReceiptChange.text = formatCurrency(if (isCashPayment) change else 0.0)

        receiptBinding.receiptItemsContainer.removeAllViews()
        receipt.lines.forEachIndexed { index, line ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = if (index == 0) 4.dp() else 3.dp() }
            }
            val nameView = TextView(this).apply {
                text = line.label
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pos_text_primary))
                textSize = 11f
                setTypeface(typeface, Typeface.NORMAL)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val qtyView = TextView(this).apply {
                text = "×${line.quantity}"
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pos_text_secondary))
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(36.dp(), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            val priceView = TextView(this).apply {
                text = formatCurrency(line.lineTotal)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pos_text_primary))
                textSize = 11f
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(88.dp(), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            row.addView(nameView)
            row.addView(qtyView)
            row.addView(priceView)
            receiptBinding.receiptItemsContainer.addView(row)
        }

        // Use AppCompatDialog (not MaterialAlertDialogBuilder) so the
        // receipt sheet isn't wrapped in Material's parentPanel/customPanel,
        // which adds asymmetric internal padding and pushes the dialog
        // off-center on tablet layouts.
        val receiptDialog = AppCompatDialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setContentView(receiptBinding.root)
            setCancelable(true)
        }
        receiptDialog.window?.apply {
            setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            )
            val params = attributes
            params.gravity = android.view.Gravity.CENTER
            params.x = 0
            params.y = 0
            params.width = android.view.WindowManager.LayoutParams.WRAP_CONTENT
            params.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT
            attributes = params
        }
        receiptBinding.btnCloseReceipt.setOnClickListener { receiptDialog.dismiss() }
        receiptDialog.show()
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

    // CHANGE: Payment — used by the discount selector and receipt to
    // render percentages cleanly: integer percents drop the decimal,
    // fractional percents keep one place.
    private fun formatPercentLabel(percent: Double): String {
        return if (percent % 1.0 == 0.0) "${percent.toInt()}%"
        else String.format(Locale.getDefault(), "%.1f%%", percent)
    }

    private fun showCreateDiscountDialog(onCreated: (Discount) -> Unit) {
        val nameInput = createDialogInput(
            hint = getString(R.string.discount_create_name_hint),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        )
        val percentInput = createDialogInput(
            hint = getString(R.string.discount_create_percent_hint),
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        )

        val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
        val startDateState = arrayOf<LocalDate?>(null)
        val endDateState = arrayOf<LocalDate?>(null)

        fun renderDateButton(button: com.google.android.material.button.MaterialButton, date: LocalDate?, placeholder: String) {
            button.text = date?.format(dateFormatter) ?: placeholder
        }

        val startDateButton = com.google.android.material.button.MaterialButton(this).apply {
            text = getString(R.string.discount_create_start_placeholder)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                40.dp()
            ).apply { topMargin = 6.dp() }
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, android.R.color.transparent))
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pos_text_primary))
            strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.pos_border))
            strokeWidth = 1.dp()
            cornerRadius = 10.dp()
        }
        val endDateButton = com.google.android.material.button.MaterialButton(this).apply {
            text = getString(R.string.discount_create_end_placeholder)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                40.dp()
            ).apply { topMargin = 6.dp() }
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, android.R.color.transparent))
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pos_text_primary))
            strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.pos_border))
            strokeWidth = 1.dp()
            cornerRadius = 10.dp()
        }
        fun openDatePicker(initial: LocalDate?, onPicked: (LocalDate) -> Unit) {
            val seed = initial ?: LocalDate.now()
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    onPicked(LocalDate.of(year, month + 1, dayOfMonth))
                },
                seed.year,
                seed.monthValue - 1,
                seed.dayOfMonth
            ).show()
        }
        startDateButton.setOnClickListener {
            openDatePicker(startDateState[0]) { picked ->
                startDateState[0] = picked
                renderDateButton(startDateButton, picked, getString(R.string.discount_create_start_placeholder))
            }
        }
        endDateButton.setOnClickListener {
            openDatePicker(endDateState[0]) { picked ->
                endDateState[0] = picked
                renderDateButton(endDateButton, picked, getString(R.string.discount_create_end_placeholder))
            }
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp(), 18.dp(), 24.dp(), 8.dp())
            addView(createSectionLabel(getString(R.string.discount_create_name_label)))
            addView(nameInput)
            addView(createSectionLabel(getString(R.string.discount_create_percent_label)))
            addView(percentInput)
            addView(createSectionLabel(getString(R.string.discount_create_start_label)))
            addView(startDateButton)
            addView(createSectionLabel(getString(R.string.discount_create_end_label)))
            addView(endDateButton)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.discount_create_title)
            .setView(ScrollView(this).apply { addView(container) })
            .setPositiveButton(R.string.discount_create_save, null)
            .setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text?.toString()?.trim().orEmpty()
                val percent = percentInput.text?.toString()?.toDoubleOrNull()
                if (name.isEmpty()) {
                    nameInput.error = getString(R.string.discount_create_name_required)
                    return@setOnClickListener
                }
                if (percent == null || percent <= 0.0 || percent > 100.0) {
                    percentInput.error = getString(R.string.discount_create_percent_invalid)
                    return@setOnClickListener
                }
                val startDate = startDateState[0]
                val endDate = endDateState[0]
                if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
                    showWarningDialog(this, getString(R.string.discount_create_date_range_invalid))
                    return@setOnClickListener
                }

                val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                saveButton.isEnabled = false

                lifecycleScope.launch {
                    val existingIds = (viewModel.availableDiscounts.value.orEmpty().map(Discount::id))
                    val result = runCatching {
                        discountRepository.createCustomDiscount(
                            name = name,
                            percent = percent,
                            startDate = startDate,
                            endDate = endDate,
                            existingIds = existingIds
                        )
                    }
                    saveButton.isEnabled = true
                    result.onSuccess { created ->
                        dialog.dismiss()
                        onCreated(created)
                    }.onFailure { exception ->
                        showErrorDialog(
                            this@MainActivity,
                            NetworkErrorFormatter.toUserMessage(
                                exception = exception,
                                fallbackMessage = getString(R.string.discount_create_failed)
                            )
                        )
                    }
                }
            }
        }
        dialog.show()
    }

    private fun applyOrderFilters() {
        val normalizedQuery = orderSearchQuery.trim().lowercase(Locale.getDefault())

        // CHANGE: Orders — compute today's epoch boundaries once so the
        // "Today only" filter doesn't recompute for every order in the list.
        val zoneId = ZoneId.systemDefault()
        val todayStartMillis = LocalDate.now()
            .atStartOfDay(zoneId)
            .toInstant().toEpochMilli()
        val tomorrowStartMillis = LocalDate.now()
            .plusDays(1)
            .atStartOfDay(zoneId)
            .toInstant().toEpochMilli()

        val filteredOrders = orders.filter { order ->
            val matchesStatus = selectedOrderStatus == null || order.status == selectedOrderStatus
            val matchesQuery = normalizedQuery.isBlank() || listOf(
                order.id,
                order.customerName,
                order.itemsSummary,
                order.orderedItems.joinToString(" ")
            ).joinToString(" ").lowercase(Locale.getDefault()).contains(normalizedQuery)
            // CHANGE: Orders — apply the GCash / Take Out / Today toggles in
            // addition to the existing status + search filters.
            val matchesGcash = !filterGcashOnly || order.isGcash
            val matchesTakeout = !filterTakeoutOnly || order.isTakeout
            val matchesToday = !filterTodayOnly ||
                (order.createdAtMillis in todayStartMillis until tomorrowStartMillis)
            matchesStatus && matchesQuery && matchesGcash && matchesTakeout && matchesToday
        }

        val sortedOrders = when (orderSort) {
            OrderSort.DEFAULT -> when (selectedOrderStatus) {
                null -> filteredOrders.sortedByDescending { it.createdAtMillis }
                CafeOrderStatus.COMPLETED -> filteredOrders.sortedByDescending { it.completedSortMillis() }
                else -> filteredOrders
            }
            OrderSort.DATE_DESC -> filteredOrders.sortedByDescending { it.createdAtMillis }
            OrderSort.TOTAL_DESC -> filteredOrders.sortedByDescending { it.total }
            OrderSort.TOTAL_ASC -> filteredOrders.sortedBy { it.total }
            OrderSort.ITEMS_DESC -> filteredOrders.sortedByDescending { it.itemCount }
        }

        val totalPages = if (sortedOrders.isEmpty()) 1
            else (sortedOrders.size + ORDERS_PAGE_SIZE - 1) / ORDERS_PAGE_SIZE
        if (ordersPage >= totalPages) ordersPage = totalPages - 1
        if (ordersPage < 0) ordersPage = 0

        val fromIndex = ordersPage * ORDERS_PAGE_SIZE
        val toIndex = minOf(fromIndex + ORDERS_PAGE_SIZE, sortedOrders.size)
        val pageItems = if (sortedOrders.isEmpty()) emptyList()
            else sortedOrders.subList(fromIndex, toIndex)

        orderManagementAdapter.submitList(pageItems.toList())

        val start = if (sortedOrders.isEmpty()) 0 else fromIndex + 1
        binding.ordersContent.tvOrdersShowing.text = getString(
            R.string.showing_orders_range,
            start,
            toIndex,
            sortedOrders.size
        )

        binding.ordersContent.tvOrdersPageInfo.text = getString(
            R.string.pagination_page_status,
            ordersPage + 1,
            totalPages
        )

        binding.ordersContent.btnOrdersPrevPage.isEnabled = ordersPage > 0
        binding.ordersContent.btnOrdersNextPage.isEnabled = ordersPage + 1 < totalPages
        binding.ordersContent.btnOrdersPrevPage.alpha =
            if (binding.ordersContent.btnOrdersPrevPage.isEnabled) 1.0f else 0.45f
        binding.ordersContent.btnOrdersNextPage.alpha =
            if (binding.ordersContent.btnOrdersNextPage.isEnabled) 1.0f else 0.45f

        updateOrderStatusCounts()
        updateOrderStatusChipStyles()
        updateOrderSalesMetrics()
        refreshNotificationBadges()
    }

    /**
     * Refreshes the three sales-tracking KPI tiles at the top of the
     * Orders page. Only completed orders count as sales, matching the
     * dashboard/reporting totals and keeping cancellable preparing
     * orders out of revenue. Totals are computed from the full `orders`
     * list (not the filtered subset) so the KPIs reflect the whole
     * period regardless of which status/search filter is on.
     */
    private fun updateOrderSalesMetrics() {
        val countable = orders.filter { it.status == CafeOrderStatus.COMPLETED }
        val totalSales = countable.sumOf { it.total }
        val gcashSales = countable.filter { it.isGcash }.sumOf { it.total }
        val cashSales = countable
            .filter { it.paymentMethod.equals("cash", ignoreCase = true) }
            .sumOf { it.total }

        binding.ordersContent.tvOrdersTotalSalesValue.text = formatCurrency(totalSales)
        binding.ordersContent.tvOrdersCashSalesValue.text = formatCurrency(cashSales)
        binding.ordersContent.tvOrdersGcashSalesValue.text = formatCurrency(gcashSales)
    }

    private fun CafeOrder.completedSortMillis(): Long {
        return completedAtMillis ?: createdAtMillis
    }

    private fun updateOrderStatusCounts() {
        binding.ordersContent.tvAllOrdersCount.text = orders.size.toString()
        binding.ordersContent.tvPreparingOrdersCount.text =
            orders.count { it.status == CafeOrderStatus.PREPARING }.toString()
        binding.ordersContent.tvCompletedOrdersCount.text =
            orders.count { it.status == CafeOrderStatus.COMPLETED }.toString()
        binding.ordersContent.tvCancelledOrdersCount.text =
            orders.count { it.status == CafeOrderStatus.CANCELLED }.toString()
    }

    private fun updateOrderStatusChipStyles() {
        setOrderChipState(
            chip = binding.ordersContent.chipAllOrders,
            iconView = binding.ordersContent.ivAllOrdersIcon,
            labelView = binding.ordersContent.tvAllOrdersLabel,
            countView = binding.ordersContent.tvAllOrdersCount,
            selected = selectedOrderStatus == null,
            accentTextColorRes = R.color.pos_primary
        )

        setOrderChipState(
            chip = binding.ordersContent.chipPreparingOrders,
            iconView = binding.ordersContent.ivPreparingOrdersIcon,
            labelView = binding.ordersContent.tvPreparingOrdersLabel,
            countView = binding.ordersContent.tvPreparingOrdersCount,
            selected = selectedOrderStatus == CafeOrderStatus.PREPARING,
            accentTextColorRes = R.color.pos_primary_soft
        )

        setOrderChipState(
            chip = binding.ordersContent.chipCompletedOrders,
            iconView = binding.ordersContent.ivCompletedOrdersIcon,
            labelView = binding.ordersContent.tvCompletedOrdersLabel,
            countView = binding.ordersContent.tvCompletedOrdersCount,
            selected = selectedOrderStatus == CafeOrderStatus.COMPLETED,
            accentTextColorRes = R.color.pos_secondary
        )

        setOrderChipState(
            chip = binding.ordersContent.chipCancelledOrders,
            iconView = binding.ordersContent.ivCancelledOrdersIcon,
            labelView = binding.ordersContent.tvCancelledOrdersLabel,
            countView = binding.ordersContent.tvCancelledOrdersCount,
            selected = selectedOrderStatus == CafeOrderStatus.CANCELLED,
            accentTextColorRes = R.color.pos_badge
        )
    }

    private fun setOrderChipState(
        chip: LinearLayout,
        iconView: ImageView,
        labelView: TextView,
        countView: TextView,
        selected: Boolean,
        accentTextColorRes: Int
    ) {
        chip.setBackgroundResource(
            if (selected) R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected
        )
        iconView.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, if (selected) R.color.white else accentTextColorRes)
        )
        labelView.setTextColor(
            ContextCompat.getColor(this, if (selected) R.color.white else R.color.pos_text_primary)
        )
        countView.setBackgroundResource(
            if (selected) R.drawable.bg_order_status_count_selected else R.drawable.bg_page_button
        )
        countView.setTextColor(
            ContextCompat.getColor(this, if (selected) R.color.pos_primary else accentTextColorRes)
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
                showInfoDialog(this, getString(R.string.order_no_products_available))
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
                    showWarningDialog(this, getString(R.string.order_select_products_required))
                    return@setOnClickListener
                }

                val totalAmount = chosenProducts.sumOf { it.price }
                val parsedStatus = parseOrderStatus(etStatus.text.toString())
                val checkoutLines = try {
                    chosenProducts.map { product ->
                        CheckoutOrderLine(
                            productVariantId = product.id,
                            sourceProductId = product.sourceProductId
                                ?: throw IllegalStateException("Missing product ID for ${product.name}. Refresh the menu and try again."),
                            sourceProductName = product.sourceProductName ?: product.name,
                            sourceVariantName = product.sourceVariantName ?: "Standard",
                            unitPrice = product.price,
                            quantity = 1
                        )
                    }
                } catch (exception: Exception) {
                    showErrorDialog(
                        this@MainActivity,
                        exception.message ?: getString(R.string.order_create_failed, getString(R.string.try_again))
                    )
                    return@setOnClickListener
                }

                val createButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                createButton.isEnabled = false
                lifecycleScope.launch {
                    try {
                        val savedOrder = orderRepository.saveCheckoutOrder(
                            payload = CheckoutOrderPayload(
                                customerName = customerName,
                                subtotal = totalAmount,
                                tax = 0.0,
                                total = totalAmount,
                                paymentMethod = (viewModel.selectedPaymentMethod.value ?: PosViewModel.PaymentMethod.CASH)
                                    .name
                                    .lowercase(Locale.US),
                                status = parsedStatus.toDatabaseValue(),
                                items = checkoutLines,
                                orderType = (viewModel.selectedOrderType.value ?: PosViewModel.OrderType.DINE_IN).toDatabaseValue()
                            ),
                            suggestedOrderNumber = viewModel.orderNumber.value
                        )
                        addOrReplaceOrder(savedOrder, reveal = true)
                        dashboardViewModel.refreshDashboard(force = true)
                        viewModel.refreshMenu()
                        showSuccessDialog(
                            this@MainActivity,
                            getString(R.string.order_created_message, savedOrder.id)
                        )
                        dialog.dismiss()
                    } catch (exception: Exception) {
                        createButton.isEnabled = true
                        showErrorDialog(
                            this@MainActivity,
                            getString(
                                R.string.order_create_failed,
                                NetworkErrorFormatter.toUserMessage(
                                    exception = exception,
                                    fallbackMessage = getString(R.string.try_again)
                                )
                            )
                        )
                    }
                }
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
            .showStyledDialog(this)
    }

    private fun parseOrderStatus(value: String): CafeOrderStatus {
        val normalized = value.trim().lowercase(Locale.getDefault())
        return when {
            normalized.startsWith("prep") -> CafeOrderStatus.PREPARING
            normalized.startsWith("comp") || normalized.startsWith("done") -> CafeOrderStatus.COMPLETED
            normalized.startsWith("cancel") || normalized.startsWith("void") -> CafeOrderStatus.CANCELLED
            else -> CafeOrderStatus.PENDING
        }
    }

    private fun CafeOrderStatus.toDatabaseValue(): String {
        return when (this) {
            CafeOrderStatus.PENDING -> "pending"
            CafeOrderStatus.PREPARING -> "preparing"
            CafeOrderStatus.COMPLETED -> "completed"
            CafeOrderStatus.CANCELLED -> "cancelled"
        }
    }

    private fun PosViewModel.OrderType.toDatabaseValue(): String {
        return when (this) {
            PosViewModel.OrderType.DINE_IN -> "dine_in"
            PosViewModel.OrderType.TAKE_AWAY -> "takeout"
            PosViewModel.OrderType.DELIVERY -> "delivery"
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
        bindDashboardRecentOrders(snapshot.recentOrders)
        updateDashboardChart(selectedDashboardPeriod, animate = false)
        refreshNotificationBadges()
    }

    private fun bindDashboardRecentOrders(recentOrders: List<DashboardRecentOrder>) {
        val dashboardRoot = binding.dashboardContent.root
        val container = dashboardRoot.findViewById<LinearLayout>(R.id.recentOrdersContainer) ?: return
        val emptyHint = dashboardRoot.findViewById<TextView>(R.id.tvRecentOrdersEmptyHint)

        container.removeAllViews()

        if (recentOrders.isEmpty()) {
            emptyHint?.visibility = View.VISIBLE
            container.visibility = View.GONE
            return
        }

        emptyHint?.visibility = View.GONE
        container.visibility = View.VISIBLE

        recentOrders.forEachIndexed { index, order ->
            val row = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
            }

            row.addView(buildRecentOrderCell(order.orderNumber, weight = 1f, R.color.pos_text_primary, bold = true))
            row.addView(buildRecentOrderCell(order.customerName, weight = 1f, R.color.pos_text_primary))
            row.addView(buildRecentOrderItemsCell(order))
            row.addView(buildRecentOrderCell(formatCurrency(order.total), weight = 0.8f, R.color.pos_text_primary, bold = true))
            val statusColor = when (order.status.lowercase(Locale.US)) {
                "completed" -> R.color.pos_secondary
                "preparing" -> R.color.pos_info
                "pending" -> R.color.pos_warning
                else -> R.color.pos_text_secondary
            }
            row.addView(buildRecentOrderCell(order.status, weight = 0.7f, statusColor, bold = true))

            container.addView(row)

            if (index < recentOrders.lastIndex) {
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1.dp()
                    )
                    setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.pos_border))
                }
                container.addView(divider)
            }
        }
    }

    private fun buildRecentOrderCell(
        text: String,
        weight: Float,
        colorRes: Int,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@MainActivity, colorRes))
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                weight
            )
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
    }

    private fun buildRecentOrderItemsCell(order: DashboardRecentOrder): TextView {
        val label = when {
            order.items.isEmpty() -> order.itemsPreview
            order.items.size == 1 -> {
                val item = order.items.first()
                val qtyText = if (item.quantity > 1) "${item.quantity}× " else ""
                "$qtyText${formatRecentItemName(item)}"
            }
            else -> getString(R.string.dashboard_recent_order_items_link, order.items.size)
        }
        val isInteractive = order.items.isNotEmpty()
        return TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (isInteractive) R.color.pos_primary else R.color.pos_text_secondary
                )
            )
            if (isInteractive) {
                setTypeface(typeface, Typeface.BOLD)
                paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
                isClickable = true
                isFocusable = true
                setOnClickListener { showRecentOrderItemsDialog(order) }
                val outValue = android.util.TypedValue()
                context.theme.resolveAttribute(
                    android.R.attr.selectableItemBackgroundBorderless,
                    outValue,
                    true
                )
                setBackgroundResource(outValue.resourceId)
            }
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                0.7f
            )
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
    }

    private fun formatRecentItemName(item: com.example.zejioscafese.dashboard.model.DashboardRecentOrderItem): String {
        val isPlainVariant = item.variantName.isBlank() ||
            item.variantName.equals("standard", ignoreCase = true) ||
            item.variantName.equals("combo", ignoreCase = true)
        return if (isPlainVariant) item.productName else "${item.productName} (${item.variantName})"
    }

    private fun showRecentOrderItemsDialog(order: DashboardRecentOrder) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24), dpToPx(12), dpToPx(24), dpToPx(4))
        }

        if (order.customerName.isNotBlank()) {
            val customerView = TextView(this).apply {
                text = getString(R.string.dashboard_recent_order_items_customer, order.customerName)
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pos_text_secondary))
                setPadding(0, 0, 0, dpToPx(12))
            }
            container.addView(customerView)
        }

        order.items.forEachIndexed { index, item ->
            if (index > 0) {
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1.dp()
                    )
                    setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.pos_border))
                }
                container.addView(divider)
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(10), 0, dpToPx(10))
            }
            val qtyChip = TextView(this).apply {
                text = getString(R.string.dashboard_recent_order_qty_chip, item.quantity)
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pos_secondary))
                setBackgroundResource(R.drawable.bg_hint_chip)
                setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4))
                minWidth = dpToPx(44)
                gravity = Gravity.CENTER
            }
            row.addView(qtyChip)

            val nameView = TextView(this).apply {
                text = formatRecentItemName(item)
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pos_text_primary))
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply { marginStart = dpToPx(12) }
            }
            row.addView(nameView)

            val priceView = TextView(this).apply {
                text = formatCurrency(item.lineTotal)
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pos_text_primary))
            }
            row.addView(priceView)

            container.addView(row)
        }

        val totalDivider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1.dp()
            ).apply { topMargin = dpToPx(6) }
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.pos_border))
        }
        container.addView(totalDivider)

        if (order.discountAmount > 0.0) {
            addRecentOrderAmountRow(
                container = container,
                label = getString(R.string.subtotal),
                amount = formatCurrency(order.subtotal),
                valueColorRes = R.color.pos_text_primary,
                valueTextSize = 14f,
                valueBold = false
            )
            addRecentOrderAmountRow(
                container = container,
                label = order.discountLabel?.takeIf(String::isNotBlank) ?: getString(R.string.discount),
                amount = "-${formatCurrency(order.discountAmount)}",
                labelColorRes = R.color.pos_secondary,
                valueColorRes = R.color.pos_secondary,
                valueTextSize = 14f,
                topPaddingDp = 2,
                bottomPaddingDp = 2
            )
            addRecentOrderAmountRow(
                container = container,
                label = getString(R.string.total_label),
                amount = formatCurrency(order.total),
                topPaddingDp = 8
            )
        } else {
            addRecentOrderAmountRow(
                container = container,
                label = getString(R.string.total_label),
                amount = formatCurrency(order.total)
            )
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.dashboard_recent_order_items_title, order.orderNumber))
            .setView(container)
            .setPositiveButton(android.R.string.ok, null)
            .showStyledDialog(this)
    }

    private fun addRecentOrderAmountRow(
        container: LinearLayout,
        label: CharSequence,
        amount: CharSequence,
        labelColorRes: Int = R.color.pos_text_secondary,
        valueColorRes: Int = R.color.pos_primary,
        valueTextSize: Float = 16f,
        valueBold: Boolean = true,
        topPaddingDp: Int = 12,
        bottomPaddingDp: Int = 4
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpToPx(topPaddingDp), 0, dpToPx(bottomPaddingDp))
        }
        val labelView = TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@MainActivity, labelColorRes))
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        val amountView = TextView(this).apply {
            text = amount
            textSize = valueTextSize
            if (valueBold) setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@MainActivity, valueColorRes))
        }
        row.addView(labelView)
        row.addView(amountView)
        container.addView(row)
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
            dashboardRoot.findViewById(R.id.tvMetricProfitValue),
            dashboardRoot.findViewById(R.id.tvMetricProfitDelta),
            dashboardRoot.findViewById(R.id.ivMetricProfitIcon),
            snapshot.profitMetric.value,
            snapshot.profitMetric.delta,
            positive = snapshot.profitMetric.positive,
            monetary = true
        )
        bindMetric(
            dashboardRoot.findViewById(R.id.tvMetricSalesValue),
            dashboardRoot.findViewById(R.id.tvMetricSalesDelta),
            dashboardRoot.findViewById(R.id.ivMetricSalesIcon),
            snapshot.salesMetric.value,
            snapshot.salesMetric.delta,
            positive = snapshot.salesMetric.positive,
            monetary = true
        )
        bindMetric(
            dashboardRoot.findViewById(R.id.tvMetricOrdersValue),
            dashboardRoot.findViewById(R.id.tvMetricOrdersDelta),
            dashboardRoot.findViewById(R.id.ivMetricOrdersIcon),
            snapshot.ordersMetric.value,
            snapshot.ordersMetric.delta,
            positive = snapshot.ordersMetric.positive
        )
        bindMetric(
            dashboardRoot.findViewById(R.id.tvMetricLowStockValue),
            dashboardRoot.findViewById(R.id.tvMetricLowStockDelta),
            dashboardRoot.findViewById(R.id.ivMetricLowStockIcon),
            snapshot.lowStockMetric.value,
            snapshot.lowStockMetric.delta,
            positive = snapshot.lowStockMetric.positive,
            allowPositiveTone = false
        )
    }

    private fun bindMetric(
        valueView: TextView?,
        deltaView: TextView?,
        iconView: ImageView?,
        value: String,
        delta: String,
        positive: Boolean,
        monetary: Boolean = false,
        allowPositiveTone: Boolean = true
    ) {
        if (valueView == null || deltaView == null) return
        val tone = resolveMetricTone(value, delta, positive, allowPositiveTone)
        val metricColor = ContextCompat.getColor(
            this,
            when (tone) {
                MetricTone.POSITIVE -> R.color.pos_secondary
                MetricTone.NEGATIVE -> R.color.pos_warning
                MetricTone.NEUTRAL -> R.color.pos_primary
            }
        )

        valueView.text = normalizeDashboardCurrency(value, monetary)
        valueView.setTextColor(metricColor)
        deltaView.text = normalizeDashboardCurrency(delta, monetary)
        deltaView.setTextColor(metricColor)
        iconView?.imageTintList = ColorStateList.valueOf(metricColor)
    }

    private fun resolveMetricTone(
        value: String,
        delta: String,
        positive: Boolean,
        allowPositiveTone: Boolean
    ): MetricTone {
        val normalizedDelta = delta.trim().lowercase(Locale.US)
        val normalizedValue = value.trim().lowercase(Locale.US)
        if (allowPositiveTone && positive && (normalizedDelta.startsWith("all ") || normalizedDelta.contains("above minimum stock"))) {
            return MetricTone.POSITIVE
        }
        val isNeutral = normalizedDelta.startsWith("no ")
            || normalizedDelta.startsWith("no change")
            || normalizedValue == "0"
            || normalizedValue == "php 0.00"
            || normalizedValue == "\u20b10.00"

        return when {
            isNeutral -> MetricTone.NEUTRAL
            positive && allowPositiveTone -> MetricTone.POSITIVE
            else -> MetricTone.NEGATIVE
        }
    }

    private fun normalizeDashboardCurrency(value: String, monetary: Boolean): String {
        if (!monetary) {
            return value
        }
        return value.replaceFirst("PHP ", "\u20B1")
    }

    private fun setupDashboardChart() {
        binding.dashboardContent.chartSalesPerformance.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(false)
            setScaleEnabled(false)
            setPinchZoom(false)
            setNoDataText("")
            setViewPortOffsets(22f, 16f, 18f, 34f)
            axisRight.isEnabled = false
            axisLeft.apply {
                axisMinimum = 0f
                setLabelCount(4, true)
                textColor = ContextCompat.getColor(this@MainActivity, R.color.pos_text_secondary)
                textSize = 11f
                gridColor = ContextCompat.getColor(this@MainActivity, R.color.pos_border)
                setDrawAxisLine(false)
            }
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                textColor = ContextCompat.getColor(this@MainActivity, R.color.pos_text_secondary)
                textSize = 11f
                yOffset = 8f
                gridColor = ContextCompat.getColor(this@MainActivity, R.color.pos_border)
                setDrawAxisLine(false)
                setDrawGridLines(false)
            }
        }

        updateDashboardChart(selectedDashboardPeriod, animate = false)
    }

    private fun setupDashboardToggle() {
        val periods = DashboardPeriod.entries.toTypedArray()
        val labels = periods.map { getString(it.labelRes) }
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            labels
        )
        binding.dashboardContent.dropdownDashboardChartPeriod.setAdapter(adapter)
        binding.dashboardContent.dropdownDashboardChartPeriod.setText(
            getString(selectedDashboardPeriod.labelRes),
            false
        )
        binding.dashboardContent.dropdownDashboardChartPeriod.setOnItemClickListener { _, _, position, _ ->
            selectedDashboardPeriod = periods[position]
            updateDashboardChart(selectedDashboardPeriod, animate = true)
        }
    }

    private fun updateDashboardChart(period: DashboardPeriod, animate: Boolean = false) {
        val points = dashboardSnapshot.charts[period].orEmpty()
        val entries = points.mapIndexed { index, point -> Entry(index.toFloat(), point.sales) }
        val labels = points.map { it.label }

        val lineColor = ContextCompat.getColor(this, R.color.pos_primary)
        val fillShade = ContextCompat.getColor(this, R.color.pos_chart_fill)

        val dataSet = LineDataSet(entries, getString(R.string.revenue)).apply {
            color = lineColor
            lineWidth = 2.5f
            setDrawCircles(false)
            setDrawValues(false)
            setDrawFilled(true)
            fillColor = fillShade
            fillAlpha = 88
            setDrawHorizontalHighlightIndicator(false)
            setDrawVerticalHighlightIndicator(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        binding.dashboardContent.chartSalesPerformance.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        binding.dashboardContent.chartSalesPerformance.data = LineData(dataSet)
        if (animate || !hasRenderedDashboardChart) {
            binding.dashboardContent.chartSalesPerformance.animateX(280)
            hasRenderedDashboardChart = true
        }
        binding.dashboardContent.chartSalesPerformance.invalidate()
    }

    private fun setupSidebar() {
        binding.btnToggleSidebar.setOnClickListener {
            toggleSidebar(animate = true)
        }
        binding.sidebarHeader.setOnClickListener {
            toggleSidebar(animate = true)
        }
        binding.sidebarHeaderTapArea.setOnClickListener {
            toggleSidebar(animate = true)
        }
        binding.ivSidebarLogo.setOnClickListener {
            toggleSidebar(animate = true)
        }
        binding.sidebarHeaderTextContainer.setOnClickListener {
            toggleSidebar(animate = true)
        }

        sidebarItems.forEach { item ->
            val label = getString(item.section.labelRes)
            item.row.contentDescription = label
            item.row.setOnClickListener { renderSection(item.section) }
        }

        val profileLabel = getString(R.string.profile)
        binding.profileCard.contentDescription = profileLabel
    }

    private fun setupDashboardActions() {
        listOf(
            binding.dashboardContent.btnQuickNewOrder to Section.POS,
            binding.dashboardContent.btnQuickInventory to Section.INVENTORY,
            binding.dashboardContent.btnQuickStaff to Section.STAFF,
            binding.dashboardContent.btnQuickReports to Section.REPORTS
        ).forEach { (button, section) ->
            button.setOnClickListener {
                renderSection(section)
            }
        }

        binding.dashboardContent.root.findViewById<View>(R.id.cardMetricLowStock)?.setOnClickListener {
            showDashboardLowStockDialog()
        }

        binding.dashboardContent.cardNeedsAttention.setOnClickListener {
            navigateToDashboardAttentionTarget()
        }
    }

    private fun navigateToDashboardAttentionTarget() {
        val priorityAlert = dashboardSnapshot.alerts.firstOrNull { it.level == AlertLevel.CRITICAL }
            ?: dashboardSnapshot.alerts.firstOrNull()

        if (priorityAlert == null) {
            showInfoDialog(this, getString(R.string.dashboard_focus_no_pending))
            return
        }

        renderSection(resolveDashboardAlertSection(priorityAlert))
    }

    private fun resolveDashboardAlertSection(alert: DashboardAlert): Section {
        val searchableText = "${alert.title} ${alert.detail}".lowercase(Locale.US)
        return when {
            searchableText.contains("order") ||
                searchableText.contains("pending") ||
                searchableText.contains("preparing") -> Section.ORDERS
            searchableText.contains("stock") ||
                searchableText.contains("ingredient") ||
                searchableText.contains("reorder") -> Section.INVENTORY
            else -> Section.INVENTORY
        }
    }

    private fun showDashboardLowStockDialog() {
        val alerts = dashboardSnapshot.alerts
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp(), 12.dp(), 24.dp(), 4.dp())
        }

        if (alerts.isEmpty()) {
            container.addView(
                createDialogText(
                    text = getString(R.string.dashboard_low_stock_empty),
                    textSizeSp = 14f,
                    textColorRes = R.color.pos_text_secondary
                )
            )
        } else {
            alerts.forEachIndexed { index, alert ->
                if (index > 0) {
                    container.addView(View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            1.dp()
                        ).apply { topMargin = 10.dp(); bottomMargin = 10.dp() }
                        setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.pos_border))
                    })
                }

                val titleColor = if (alert.level == AlertLevel.CRITICAL) {
                    R.color.stock_critical
                } else {
                    R.color.pos_warning
                }
                container.addView(
                    createDialogText(
                        text = alert.title,
                        textSizeSp = 14f,
                        typeface = Typeface.DEFAULT_BOLD,
                        textColorRes = titleColor
                    )
                )
                container.addView(
                    createDialogText(
                        text = alert.detail,
                        textSizeSp = 13f,
                        textColorRes = R.color.pos_text_secondary,
                        topMarginDp = 4
                    )
                )
            }
        }

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.dashboard_low_stock_dialog_title))
            .setView(ScrollView(this).apply { addView(container) })
            .setNegativeButton(android.R.string.ok, null)

        if (alerts.isNotEmpty()) {
            builder.setPositiveButton(getString(R.string.dashboard_low_stock_open_inventory)) { _, _ ->
                renderSection(Section.INVENTORY)
            }
        }

        builder.showStyledDialog(this)
    }

    private fun setupInteractions() {
        binding.etSearch.doAfterTextChanged { text ->
            viewModel.updateSearchQuery(text?.toString().orEmpty())
        }

        binding.btnFilterSort.setOnClickListener { showSortMenu(it) }

        binding.btnBrowseMenu.setOnClickListener { showMenuBrowseDialog() }

        binding.btnOrderTypeDineIn.setOnClickListener {
            viewModel.setOrderType(PosViewModel.OrderType.DINE_IN)
        }
        binding.btnOrderTypeTakeAway.setOnClickListener {
            viewModel.setOrderType(PosViewModel.OrderType.TAKE_AWAY)
        }
        binding.btnClear.setOnClickListener { viewModel.clearOrder() }
        binding.btnCheckout.setOnClickListener {
            if (viewModel.orderItems.value.isNullOrEmpty()) {
                showInfoDialog(this, getString(R.string.checkout_requires_items))
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
        binding.btnPreviousProductsPage.setOnClickListener {
            viewModel.goToPreviousProductPage()
        }
        binding.btnNextProductsPage.setOnClickListener {
            viewModel.goToNextProductPage()
        }

        binding.notificationFrame.setOnClickListener {
            showNotificationCenterDialog()
        }
        binding.avatar.setOnClickListener { renderSection(Section.PROFILE) }
        binding.topProfileCluster.setOnClickListener { renderSection(Section.PROFILE) }
    }

    private fun setupStaffInteractions() {
        binding.staffContent.btnAddStaff.setOnClickListener {
            showStaffDialog(card = null)
        }

        staffCards.clear()
        binding.staffContent.staffCardsContainer.removeAllViews()

        LocalAppPrefs.loadStaff(this).forEach { saved ->
            addStaffCard(
                name = saved.name,
                employeeId = saved.employeeId,
                role = saved.role
            )
        }

        binding.staffContent.etStaffSearch.doAfterTextChanged { text ->
            staffSearchQuery = text?.toString().orEmpty()
            applyStaffFilters()
        }

        binding.staffContent.btnStaffRoleFilter.setOnClickListener { anchor ->
            showStaffRoleFilterMenu(anchor)
        }

        refreshStaffUi()
    }

    private fun persistStaffCards() {
        LocalAppPrefs.saveStaff(
            this,
            staffCards.map { card ->
                LocalAppPrefs.StoredStaff(
                    name = card.nameView.text?.toString().orEmpty(),
                    employeeId = card.idView.text?.toString()
                        ?.removePrefix("ID:")
                        ?.trim()
                        .orEmpty(),
                    role = card.roleView.text?.toString().orEmpty()
                )
            }
        )
    }

    private fun setupProfileInteractions() {
        binding.profileCard.setOnClickListener {
            renderSection(Section.PROFILE)
        }
        binding.ivProfileAvatar.setOnClickListener {
            showAvatarMenu(it)
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
            dialog.applyZejiosCafeButtonStyling(this)
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

                LocalAppPrefs.saveProfile(
                    this,
                    LocalAppPrefs.StoredProfile(
                        name = userProfileState.name,
                        email = userProfileState.email,
                        role = userProfileState.role,
                        phone = userProfileState.phone,
                        address = userProfileState.address
                    )
                )

                applyUserProfileStateToUi()

                showSuccessDialog(this, getString(R.string.profile_updated_message))

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
                LoginActivity.clearRememberedSession(this)
                viewModel.clearOrder()
                startActivity(
                    Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
                finish()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .showStyledDialog(this)
    }

    private fun applyUserProfileStateToUi() {
        binding.tvProfileName.text = userProfileState.name
        binding.tvProfileEmail.text = userProfileState.email
        binding.tvTopProfileName.text = userProfileState.name
        binding.tvTopProfileEmail.text = userProfileState.email

        binding.profileContent.tvProfilePageName.text = userProfileState.name
        binding.profileContent.tvProfilePageRole.text = userProfileState.role
        binding.profileContent.tvProfilePageEmail.text = userProfileState.email
        binding.profileContent.tvProfilePageEmailDetail.text = userProfileState.email
        binding.profileContent.tvProfilePagePhone.text = userProfileState.phone
        binding.profileContent.tvProfilePageAddress.text = userProfileState.address
        binding.profileContent.tvProfilePageRoleDetail.text = userProfileState.role

        binding.avatar.contentDescription = getString(R.string.profile_avatar_for, userProfileState.name)
        binding.ivProfileAvatar.contentDescription = getString(R.string.profile_avatar_for, userProfileState.name)
    }

    private fun showStaffDialog(card: StaffCardViews?) {
        val isEditMode = card != null
        val dialogView = layoutInflater.inflate(R.layout.dialog_staff_form, null, false)

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvStaffDialogTitle)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvStaffDialogSubtitle)
        val tvInitials = dialogView.findViewById<TextView>(R.id.tvStaffDialogInitials)
        val tvEmployeeId = dialogView.findViewById<TextView>(R.id.tvStaffDialogEmployeeId)
        val tilName = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(
            R.id.tilStaffDialogName
        )
        val etName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(
            R.id.etStaffDialogName
        )
        val actvRole = dialogView.findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(
            R.id.actvStaffDialogRole
        )

        tvTitle.setText(if (isEditMode) R.string.staff_dialog_edit_title else R.string.staff_dialog_add_title)
        tvSubtitle.setText(if (isEditMode) R.string.staff_dialog_edit_subtitle else R.string.staff_dialog_add_subtitle)

        val initialName = card?.nameView?.text?.toString().orEmpty()
        etName.setText(initialName)
        tvInitials.text = staffInitialsFromName(initialName)
        etName.doAfterTextChanged { text ->
            tvInitials.text = staffInitialsFromName(text?.toString().orEmpty())
            if (!text.isNullOrBlank()) tilName.error = null
        }

        val autoEmployeeId = if (isEditMode) {
            card?.idView?.text?.toString()?.removePrefix("ID:")?.trim().orEmpty()
        } else {
            generateNextStaffEmployeeId()
        }
        tvEmployeeId.text = autoEmployeeId

        val roleOptions = listOf(
            getString(R.string.staff_role_manager),
            getString(R.string.staff_role_barista),
            getString(R.string.staff_role_cashier)
        )
        val roleAdapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            roleOptions
        )
        actvRole.setAdapter(roleAdapter)
        val currentRole = card?.roleView?.text?.toString()?.trim()
        val initialRole = roleOptions.firstOrNull { it.equals(currentRole, ignoreCase = true) }
            ?: roleOptions.first()
        actvRole.setText(initialRole, false)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton(
                getString(
                    if (isEditMode) R.string.staff_dialog_save_action else R.string.staff_dialog_add_action
                ),
                null
            )
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.applyZejiosCafeButtonStyling(this)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = etName.text.toString().trim()
                if (name.isBlank()) {
                    tilName.error = getString(R.string.staff_name_required)
                    return@setOnClickListener
                }

                val selectedRole = actvRole.text?.toString()?.trim()
                    ?.takeIf { it.isNotBlank() && roleOptions.any { opt -> opt.equals(it, ignoreCase = true) } }
                    ?: roleOptions.first()

                if (isEditMode) {
                    val staffCard = card ?: return@setOnClickListener
                    staffCard.nameView.text = name
                    staffCard.roleView.text = selectedRole
                    updateStaffCardAvatar(staffCard)

                    showSuccessDialog(this, getString(R.string.staff_updated_message, name))
                } else {
                    val newEmployeeId = normalizeStaffEmployeeId(autoEmployeeId)
                    addStaffCard(
                        name = name,
                        employeeId = newEmployeeId,
                        role = selectedRole
                    )

                    showSuccessDialog(this, getString(R.string.staff_added_message, name))
                }

                persistStaffCards()
                refreshStaffUi()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun staffInitialsFromName(rawName: String): String {
        val parts = rawName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return "?"
        val first = parts.first().firstOrNull()?.uppercaseChar()?.toString().orEmpty()
        val second = parts.getOrNull(1)?.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
        return (first + second).ifBlank { "?" }
    }

    private fun updateStaffCardAvatar(card: StaffCardViews) {
        val initialsView = card.rootView.findViewById<TextView>(R.id.tvStaffCardInitials)
        initialsView?.text = staffInitialsFromName(card.nameView.text.toString())
    }

    private fun addStaffCard(
        name: String,
        employeeId: String,
        role: String
    ) {
        val cardRoot = layoutInflater.inflate(
            R.layout.item_staff_profile_card,
            binding.staffContent.staffCardsContainer,
            false
        )

        val nameView = cardRoot.findViewById<TextView>(R.id.tvStaffName)
        val idView = cardRoot.findViewById<TextView>(R.id.tvStaffId)
        val roleView = cardRoot.findViewById<TextView>(R.id.tvStaffRole)
        val initialsView = cardRoot.findViewById<TextView>(R.id.tvStaffCardInitials)
        val editButton = cardRoot.findViewById<MaterialButton>(R.id.btnEditStaff)
        val actionsButton = cardRoot.findViewById<MaterialButton>(R.id.btnStaffActions)

        nameView.text = name
        idView.text = getString(R.string.staff_id_format, employeeId)
        roleView.text = role
        initialsView.text = staffInitialsFromName(name)

        val newCard = StaffCardViews(
            rootView = cardRoot,
            nameView = nameView,
            idView = idView,
            roleView = roleView,
            editButton = editButton,
            actionsButton = actionsButton
        )

        bindStaffCardInteractions(newCard)
        staffCards.add(newCard)
        rebuildStaffGrid()
    }

    private fun rebuildStaffGrid() {
        val container = binding.staffContent.staffCardsContainer
        container.removeAllViews()

        val rowGap = dpToPx(12)
        val colGap = dpToPx(12)
        val columnsPerRow = 3

        staffCards.chunked(columnsPerRow).forEachIndexed { rowIndex, rowCards ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (rowIndex > 0) topMargin = rowGap
                }
            }

            for (col in 0 until columnsPerRow) {
                val slot = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (col > 0) marginStart = colGap
                }
                val card = rowCards.getOrNull(col)
                if (card != null) {
                    (card.rootView.parent as? ViewGroup)?.removeView(card.rootView)
                    card.rootView.layoutParams = slot
                    row.addView(card.rootView)
                } else {
                    val placeholder = View(this).apply {
                        layoutParams = slot
                        visibility = View.INVISIBLE
                    }
                    row.addView(placeholder)
                }
            }

            container.addView(row)
        }
    }

    private fun normalizeStaffEmployeeId(value: String): String {
        var normalized = value.trim()
        if (normalized.startsWith("ID:", ignoreCase = true)) {
            normalized = normalized.substringAfter(':').trim()
        }

        normalized = normalized.removePrefix("#")
        val digits = Regex("(\\d+)").find(normalized)?.groupValues?.getOrNull(1)
        val number = digits?.toIntOrNull() ?: return "#EMP_001"

        return formatEmployeeId(number)
    }

    private fun formatEmployeeId(number: Int): String {
        return "#EMP_%03d".format(number)
    }

    private fun generateNextStaffEmployeeId(): String {
        val nextNumber = staffCards
            .mapNotNull { card ->
                Regex("EMP[_-](\\d+)", RegexOption.IGNORE_CASE)
                    .find(card.idView.text.toString())
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
            }
            .maxOrNull()
            ?.plus(1)
            ?: 1

        return formatEmployeeId(nextNumber)
    }

    private fun bindStaffCardInteractions(card: StaffCardViews) {
        card.editButton.setOnClickListener {
            showStaffDialog(card)
        }
        card.actionsButton.setOnClickListener { anchor ->
            showStaffActionsMenu(card, anchor)
        }
    }

    private fun showStaffRoleFilterMenu(anchor: View) {
        val roles = staffCards
            .map { it.roleView.text.toString().trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        PopupMenu(this, anchor).apply {
            menu.add(0, 0, 0, getString(R.string.all_roles))
            roles.forEachIndexed { index, role ->
                menu.add(0, index + 1, index + 1, role)
            }
            setOnMenuItemClickListener { item ->
                selectedStaffRole = if (item.itemId == 0) null else item.title.toString()
                applyStaffFilters()
                true
            }
        }.show()
    }

    private fun showStaffActionsMenu(card: StaffCardViews, anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, getString(R.string.staff_action_view_summary))
            menu.add(0, 2, 1, getString(R.string.staff_action_remove))
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> showStaffSummaryDialog(card)
                    2 -> showRemoveStaffDialog(card)
                }
                true
            }
        }.show()
    }

    private fun showStaffSummaryDialog(card: StaffCardViews) {
        val summary = buildString {
            appendLine(getString(R.string.staff_field_employee_id) + ": " + card.idView.text)
            append(getString(R.string.staff_field_role) + ": " + card.roleView.text)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(card.nameView.text)
            .setMessage(summary)
            .setPositiveButton(getString(R.string.edit)) { _, _ ->
                showStaffDialog(card)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .showStyledDialog(this)
    }

    private fun showRemoveStaffDialog(card: StaffCardViews) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.staff_remove_dialog_title))
            .setMessage(getString(R.string.staff_remove_dialog_message, card.nameView.text))
            .setPositiveButton(getString(R.string.staff_remove_dialog_confirm)) { _, _ ->
                (card.rootView.parent as? ViewGroup)?.removeView(card.rootView)
                staffCards.remove(card)
                persistStaffCards()
                rebuildStaffGrid()
                refreshStaffUi()
                showSuccessDialog(
                    this,
                    getString(R.string.staff_removed_message, card.nameView.text)
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .showStyledDialog(this)
    }

    private fun refreshStaffUi() {
        updateStaffMetrics()
        applyStaffFilters()
        updateStaffEmptyState()
        refreshNotificationBadges()
    }

    private fun updateStaffEmptyState() {
        val isEmpty = staffCards.isEmpty()
        binding.staffContent.tvStaffEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.staffContent.staffCardsContainer.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun applyStaffFilters() {
        val normalizedQuery = staffSearchQuery.trim().lowercase(Locale.getDefault())
        val visibleCount = staffCards.count { card ->
            val matches = matchesStaffFilters(card, normalizedQuery)
            card.rootView.visibility = if (matches) View.VISIBLE else View.GONE
            matches
        }

        if (currentSection == Section.STAFF) {
            binding.tvTopSubtitle.text =
                if (normalizedQuery.isBlank() && selectedStaffRole.isNullOrBlank()) {
                    getString(R.string.staff_subtitle)
                } else {
                    getString(R.string.staff_results_summary, visibleCount, staffCards.size)
                }
        }

        binding.staffContent.btnStaffRoleFilter.text = selectedStaffRole ?: getString(R.string.all_roles)
    }

    private fun matchesStaffFilters(card: StaffCardViews, normalizedQuery: String): Boolean {
        val matchesRole = selectedStaffRole.isNullOrBlank() ||
            card.roleView.text.toString().equals(selectedStaffRole, ignoreCase = true)
        val searchableText = listOf(
            card.nameView.text,
            card.idView.text,
            card.roleView.text
        ).joinToString(" ").lowercase(Locale.getDefault())
        val matchesQuery = normalizedQuery.isBlank() || searchableText.contains(normalizedQuery)

        return matchesRole && matchesQuery
    }

    private fun updateStaffMetrics() {
        binding.staffContent.tvStaffMetricTotal.text = staffCards.size.toString()
    }

    private fun showNotificationCenterDialog() {
        val sortedAlerts = dashboardSnapshot.alerts.sortedByDescending { it.level == AlertLevel.CRITICAL }
        val activeOrders = orders
            .filter { it.status != CafeOrderStatus.COMPLETED && it.status != CafeOrderStatus.CANCELLED }
            .sortedByDescending { it.createdAtMillis }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 8.dp(), 20.dp(), 12.dp())
        }

        lateinit var dialog: AlertDialog

        if (sortedAlerts.isEmpty() && activeOrders.isEmpty()) {
            content.addView(
                createDialogText(
                    text = getString(R.string.notification_center_empty),
                    textSizeSp = 14f,
                    textColorRes = R.color.pos_text_secondary
                )
            )
        } else {
            if (sortedAlerts.isNotEmpty()) {
                content.addView(
                    createSectionLabel(
                        getString(R.string.notification_section_cafe_alerts, sortedAlerts.size)
                    )
                )
                sortedAlerts.forEach { alert ->
                    val accent = when (alert.level) {
                        AlertLevel.CRITICAL -> R.color.stock_critical
                        AlertLevel.WARNING -> R.color.pos_warning
                    }
                    content.addView(
                        createNotificationRow(
                            title = alert.title,
                            subtitle = alert.detail,
                            accentColorRes = accent
                        ) {
                            dialog.dismiss()
                            renderSection(Section.DASHBOARD)
                        }
                    )
                }
            }

            if (activeOrders.isNotEmpty()) {
                content.addView(
                    createSectionLabel(
                        getString(R.string.notification_section_active_orders, activeOrders.size)
                    )
                )
                activeOrders.forEach { order ->
                    val customerLabel = order.customerName.ifBlank {
                        getString(R.string.order_walk_in_label)
                    }
                    val subtitle = getString(
                        R.string.notification_order_subtitle,
                        customerLabel,
                        order.itemCount,
                        formatOrderStatus(order.status)
                    )
                    val accent = when (order.status) {
                        CafeOrderStatus.PENDING -> R.color.pos_warning
                        CafeOrderStatus.PREPARING -> R.color.pos_secondary
                        else -> R.color.pos_text_secondary
                    }
                    content.addView(
                        createNotificationRow(
                            title = getString(R.string.notification_order_title, order.id),
                            subtitle = subtitle,
                            accentColorRes = accent
                        ) {
                            dialog.dismiss()
                            renderSection(Section.ORDERS)
                            revealOrderInOrders(order)
                        }
                    )
                }
            }
        }

        val scrollView = ScrollView(this).apply {
            addView(content)
            isFillViewport = true
        }

        dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.notification_center_title))
            .setView(scrollView)
            .setNegativeButton(android.R.string.cancel, null)
            .showStyledDialog(this)
    }

    private fun createNotificationRow(
        title: String,
        subtitle: String,
        accentColorRes: Int,
        onClick: () -> Unit
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
            background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.bg_notification_container
            )
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10.dp() }
            setOnClickListener { onClick() }
        }

        val stripe = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                4.dp(),
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply {
                marginEnd = 12.dp()
            }
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, accentColorRes))
        }
        row.addView(stripe)

        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val titleView = TextView(this).apply {
            text = title
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pos_text_primary))
        }
        textColumn.addView(titleView)

        val subtitleView = TextView(this).apply {
            text = subtitle
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pos_text_secondary))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4.dp() }
        }
        textColumn.addView(subtitleView)

        row.addView(textColumn)
        return row
    }

    private fun refreshNotificationBadges() {
        val topCount = dashboardSnapshot.alerts.size +
            orders.count {
                it.status != CafeOrderStatus.COMPLETED && it.status != CafeOrderStatus.CANCELLED
            }
        setBadgeCount(binding.tvNotificationBadge, topCount)
    }

    private fun setBadgeCount(badgeView: TextView, count: Int) {
        badgeView.visibility = if (count > 0) View.VISIBLE else View.GONE
        badgeView.text = when {
            count > 9 -> "9+"
            else -> count.toString()
        }
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

    private fun createStaffLabeledDropdown(
        container: LinearLayout,
        label: String,
        options: List<String>,
        selectedValue: String?
    ): android.widget.Spinner {
        val labelView = TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pos_text_secondary))
            setPadding(0, dpToPx(8), 0, dpToPx(4))
        }
        container.addView(labelView)

        val spinner = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                options
            )
            setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
            setBackgroundResource(R.drawable.bg_input_field)
            val initialIndex = options.indexOfFirst { it.equals(selectedValue, ignoreCase = true) }
            if (initialIndex >= 0) setSelection(initialIndex)
        }
        container.addView(spinner)
        return spinner
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

    private fun showMenuBrowseDialog() {
        if (supportFragmentManager.findFragmentByTag(MenuBrowseDialogFragment.TAG) != null) {
            return
        }
        val dialog = MenuBrowseDialogFragment().apply {
            setThumbnails(categoryThumbnails)
        }
        dialog.show(supportFragmentManager, MenuBrowseDialogFragment.TAG)
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

        viewModel.productGroups.observe(this) { groups ->
            productAdapter.submitList(groups)
            binding.rvProducts.scrollToPosition(0)
            // Accumulate one representative image per category for the picker dialog.
            groups.forEach { group ->
                if (!categoryThumbnails.containsKey(group.category) && !group.imageUrl.isNullOrBlank()) {
                    categoryThumbnails[group.category] = group.imageUrl
                }
            }
        }

        viewModel.productPaginationState.observe(this) { state ->
            renderProductPagination(state)
        }

        viewModel.selectedSortOption.observe(this) { option ->
            updateSortButtonLabel(option)
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

        // Subtotal / tax / total are no longer rendered in the side cart;
        // the checkout dialog reads them straight from the ViewModel.

        // Payment method is now selected in the checkout modal only

        viewModel.selectedOrderType.observe(this) { orderType ->
            applyOrderTypeSelection(orderType)
        }

        viewModel.menuLoadError.observe(this) { errorMessage ->
            if (!errorMessage.isNullOrBlank()) {
                showErrorDialog(this, errorMessage)
                viewModel.onMenuLoadErrorConsumed()
            }
        }

        viewModel.stockLimitNotice.observe(this) { notice ->
            if (notice != null) {
                showNoticeDialog(
                    context = this,
                    titleRes = R.string.notice_dialog_stock_limit_title,
                    message = notice.message
                )
                viewModel.onStockLimitNoticeConsumed()
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
                addOrReplaceOrder(savedOrder, reveal = true)
                loadOrdersFromSupabase(
                    showError = false,
                    force = true,
                    orderToKeepVisible = savedOrder
                )
                viewModel.refreshMenu()
                dashboardViewModel.refreshDashboard(force = true)
                if (receipt != null) {
                    showReceiptDialog(savedOrder, receipt)
                } else {
                    showSuccessDialog(
                        this,
                        getString(R.string.checkout_saved_message, savedOrder.id)
                    )
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
                showErrorDialog(
                    this,
                    getString(R.string.checkout_save_failed, errorMessage)
                )
                viewModel.onCheckoutErrorConsumed()
            }
        }
    }

    private fun observeDashboardViewModel() {
        dashboardViewModel.dashboardSnapshot.observe(this) { snapshot ->
            bindDashboardSnapshot(snapshot)
            handleInventoryNotices(snapshot.inventoryNotices)
        }

        dashboardViewModel.dashboardError.observe(this) { errorMessage ->
            if (!errorMessage.isNullOrBlank()) {
                showErrorDialog(this, errorMessage)
                dashboardViewModel.onDashboardErrorConsumed()
            }
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val alreadyGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!alreadyGranted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun handleInventoryNotices(notices: List<InventoryStockNotice>) {
        val currentLow = notices.filter { it.status == InventoryStockStatus.LOW }
            .map(InventoryStockNotice::ingredientId)
            .toSet()
        val currentOut = notices.filter { it.status == InventoryStockStatus.OUT }
            .map(InventoryStockNotice::ingredientId)
            .toSet()

        // Skip the very first snapshot: we don't want to bury the user in
        // notifications for every ingredient that was already low when the
        // app launched.
        if (!hasSeenInitialStockSnapshot) {
            notifiedLowStockIds.clear()
            notifiedLowStockIds.addAll(currentLow)
            notifiedOutOfStockIds.clear()
            notifiedOutOfStockIds.addAll(currentOut)
            hasSeenInitialStockSnapshot = true
            return
        }

        notices.forEach { notice ->
            when (notice.status) {
                InventoryStockStatus.OUT -> {
                    if (notifiedOutOfStockIds.add(notice.ingredientId)) {
                        AppNotifications.notifyOutOfStock(this, notice.ingredientName)
                    }
                }
                InventoryStockStatus.LOW -> {
                    // Promotion from low to out is handled in the OUT branch
                    // above. Only fire the low notification once per dip.
                    if (notice.ingredientId !in notifiedOutOfStockIds &&
                        notifiedLowStockIds.add(notice.ingredientId)
                    ) {
                        AppNotifications.notifyLowStock(
                            context = this,
                            ingredientName = notice.ingredientName,
                            currentStock = formatStockValue(notice.currentStock),
                            unit = notice.unit
                        )
                    }
                }
            }
        }

        // Drop tracking entries for ingredients that have recovered, so the
        // next dip below threshold triggers a fresh notification.
        notifiedLowStockIds.retainAll(currentLow + currentOut)
        notifiedOutOfStockIds.retainAll(currentOut)
    }

    private fun handleNewOrderArrivals(latestOrders: List<CafeOrder>) {
        if (!hasSeenInitialOrders) {
            notifiedOrderIds.clear()
            notifiedOrderIds.addAll(latestOrders.map(CafeOrder::id))
            hasSeenInitialOrders = true
            return
        }

        val activeOrders = latestOrders.filter {
            it.status == CafeOrderStatus.PENDING || it.status == CafeOrderStatus.PREPARING
        }
        activeOrders.forEach { order ->
            if (notifiedOrderIds.add(order.id)) {
                AppNotifications.notifyNewOrder(
                    context = this,
                    orderNumber = order.id,
                    customerName = order.customerName,
                    itemCount = order.itemCount
                )
            }
        }
    }

    private fun formatStockValue(amount: Double): String {
        return if (amount % 1.0 == 0.0) amount.toInt().toString()
        else String.format(Locale.US, "%.1f", amount)
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

    private fun updateSortButtonLabel(option: PosViewModel.SortOption) {
        binding.btnFilterSort.text = when (option) {
            PosViewModel.SortOption.NAME_ASC -> getString(R.string.sort_name_asc)
            PosViewModel.SortOption.NAME_DESC -> getString(R.string.sort_name_desc)
            PosViewModel.SortOption.PRICE_ASC -> getString(R.string.sort_price_asc)
            PosViewModel.SortOption.PRICE_DESC -> getString(R.string.sort_price_desc)
        }
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
        val previousSection = currentSection
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

        val hideTopBar = section == Section.REPORTS
        binding.topBar.visibility = if (hideTopBar) View.GONE else View.VISIBLE
        binding.leftPanel.visibility = if (showPos) View.VISIBLE else View.GONE
        binding.rightPanel.visibility = if (showPos && isCheckoutExpanded) View.VISIBLE else View.GONE
        binding.dashboardContent.root.visibility = if (showDashboard) View.VISIBLE else View.GONE
        binding.ordersContent.root.visibility = if (showOrders) View.VISIBLE else View.GONE
        binding.staffContent.root.visibility = if (showStaff) View.VISIBLE else View.GONE
        binding.profileContent.root.visibility = if (showProfile) View.VISIBLE else View.GONE
        binding.placeholderContent.root.visibility = if (showPlaceholder) View.VISIBLE else View.GONE
        binding.fragmentContainer.visibility = if (showFragmentScreen) View.VISIBLE else View.GONE

        if (showPos) {
            if (previousSection != Section.POS) {
                viewModel.refreshMenu()
            }
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

    private fun shouldDefaultSidebarBeExpanded(): Boolean {
        return resources.configuration.smallestScreenWidthDp >= 600
    }

    private fun setCheckoutExpanded(expanded: Boolean, animate: Boolean) {
        if (isCheckoutExpanded == expanded && currentSection == Section.POS) {
            return
        }
        isCheckoutExpanded = expanded
        if (expanded) {
            if (sidebarExpandedBeforeCheckout == null) {
                sidebarExpandedBeforeCheckout = isSidebarExpanded
            }
            if (isSidebarExpanded) {
                setSidebarExpanded(expanded = false, animate = animate)
            }
        } else {
            val previous = sidebarExpandedBeforeCheckout
            sidebarExpandedBeforeCheckout = null
            if (previous == true && !isSidebarExpanded) {
                setSidebarExpanded(expanded = true, animate = animate)
            }
        }
        if (currentSection == Section.POS) {
            applyCheckoutPanelState(expanded = expanded, animate = animate)
        }
    }

    private fun setSidebarExpanded(expanded: Boolean, animate: Boolean) {
        if (isSidebarExpanded == expanded) {
            return
        }
        isSidebarExpanded = expanded
        applySidebarState(expanded, animate = animate)
    }

    private fun toggleSidebar(animate: Boolean) {
        val collapsedWidth = resources.getDimensionPixelSize(R.dimen.sidebar_collapsed_width)
        val currentWidth = binding.sidebarContainer.width.takeIf { it > 0 }
            ?: binding.sidebarContainer.layoutParams.width
        val currentlyExpanded = currentWidth > collapsedWidth || binding.btnToggleSidebar.visibility == View.VISIBLE
        setSidebarExpanded(expanded = !currentlyExpanded, animate = animate)
    }

    private fun applyCheckoutPanelState(expanded: Boolean, animate: Boolean) {
        val guideParams = binding.contentGuide.layoutParams as? ConstraintLayout.LayoutParams ?: return
        val targetPercent = if (expanded) checkoutExpandedGuidePercent else CHECKOUT_COLLAPSED_GUIDE_PERCENT
        val startPercent = guideParams.guidePercent
        val checkoutPanel = binding.rightPanel
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val slideOffset = resources.getDimension(R.dimen.checkout_panel_slide_offset)

        checkoutAnimator?.cancel()
        updatePosCategoryChipMode(compact = false)
        updatePosCategoryStripPadding(expanded)
        updateProductGridSpanCount()

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
        isSidebarExpanded = expanded
        val targetWidth = resources.getDimensionPixelSize(
            if (expanded) R.dimen.sidebar_expanded_width else R.dimen.sidebar_collapsed_width
        )
        sidebarAnimator?.cancel()

        sidebarExpandedOnlyViews.forEach { view ->
            view.visibility = if (expanded) View.VISIBLE else View.GONE
        }
        sidebarLabelViews.forEach { view ->
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
        binding.profileCard.updatePaddingRelative(start = horizontalPadding, end = horizontalPadding)
        binding.profileCard.updateLayoutParams<LinearLayout.LayoutParams> {
            marginStart = rowMargin
            marginEnd = rowMargin
        }
        binding.btnToggleSidebar.visibility = if (expanded) View.VISIBLE else View.GONE
        binding.topProfileTextContainer.visibility = if (expanded) View.GONE else View.VISIBLE
        binding.ivSidebarLogo.updateLayoutParams<ConstraintLayout.LayoutParams> {
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = if (expanded) ConstraintLayout.LayoutParams.UNSET else ConstraintLayout.LayoutParams.PARENT_ID
            horizontalBias = if (expanded) 0f else 0.5f
        }
        val contentGap = resources.getDimensionPixelSize(
            if (expanded) R.dimen.main_content_gap_expanded else R.dimen.main_content_gap_collapsed
        )
        binding.mainContainer.updateLayoutParams<androidx.constraintlayout.widget.ConstraintLayout.LayoutParams> {
            marginStart = contentGap
        }
        binding.btnToggleSidebar.rotation = if (expanded) 90f else -90f
        updateSidebarToggleAccessibility(expanded)
        applySidebarAppearance(expanded)
        updateProductGridSpanCount()

        val startWidth = binding.sidebarContainer.width.takeIf { it > 0 }
            ?: binding.sidebarContainer.layoutParams.width
        if (!animate || startWidth <= 0) {
            binding.sidebarContainer.updateLayoutParams { width = targetWidth }
            return
        }

        sidebarAnimator = ValueAnimator.ofInt(startWidth, targetWidth).apply {
            duration = 220L
            addUpdateListener { animator ->
                binding.sidebarContainer.updateLayoutParams {
                    width = animator.animatedValue as Int
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                    binding.sidebarContainer.updateLayoutParams { width = targetWidth }
                    sidebarAnimator = null
                }

                override fun onAnimationEnd(animation: Animator) {
                    binding.sidebarContainer.updateLayoutParams { width = targetWidth }
                    sidebarAnimator = null
                }
            })
            start()
        }
    }

    private fun renderProductPagination(state: PosViewModel.PaginationState) {
        val shouldShow = state.totalItems > POS_PAGE_SIZE
        binding.productPaginationContainer.visibility = if (shouldShow) View.VISIBLE else View.GONE
        binding.tvProductsPageInfo.text = getString(
            R.string.pagination_page_status,
            state.currentPage,
            state.totalPages
        )
        binding.btnPreviousProductsPage.isEnabled = state.canGoPrevious
        binding.btnNextProductsPage.isEnabled = state.canGoNext
    }

    private fun applySidebarAppearance(expanded: Boolean) {
        val sidebarText = ContextCompat.getColor(this, R.color.pos_text_on_sidebar)
        val sidebarMuted = ContextCompat.getColor(this, R.color.pos_text_on_sidebar_muted)
        val white = ContextCompat.getColor(this, R.color.white)
        val selectedBackground = if (expanded) {
            R.drawable.bg_sidebar_item_selected
        } else {
            R.drawable.bg_sidebar_item_selected_compact
        }

        binding.sidebarSurface.setBackgroundResource(R.drawable.bg_sidebar_surface)
        val profileSelected = currentSection == Section.PROFILE
        binding.profileCard.setBackgroundResource(
            if (profileSelected) selectedBackground else {
                if (expanded) R.drawable.bg_profile_card else 0
            }
        )
        binding.ivSidebarLogo.imageTintList = null
        binding.ivProfileAvatar.setBackgroundResource(
            if (expanded) R.drawable.bg_avatar_circle else 0
        )
        binding.ivProfileAvatar.imageTintList = ColorStateList.valueOf(
            if (expanded) ContextCompat.getColor(this, R.color.pos_primary) else white
        )
        binding.btnToggleSidebar.imageTintList = ColorStateList.valueOf(white)
        binding.tvSidebarTitle.setTextColor(white)
        binding.tvSidebarSubtitle.setTextColor(sidebarMuted)
        binding.tvProfileName.setTextColor(white)
        binding.tvProfileEmail.setTextColor(if (profileSelected) white else sidebarMuted)

        sidebarItems.forEach { item ->
            val isSelected = item.section == currentSection
            item.row.setBackgroundResource(if (isSelected) selectedBackground else 0)
            val itemColor = if (isSelected) white else sidebarText
            item.icon.imageTintList = ColorStateList.valueOf(itemColor)
            item.label.setTextColor(itemColor)
            item.label.setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun updateSidebarToggleAccessibility(expanded: Boolean) {
        val description = getString(
            if (expanded) R.string.collapse_sidebar else R.string.expand_sidebar
        )
        binding.btnToggleSidebar.contentDescription = description
    }

    private fun loadSidebarLogo() {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    assets.open(SIDEBAR_LOGO_ASSET_PATH).use(BitmapFactory::decodeStream)
                }.getOrNull()
            }
            bitmap?.let {
                binding.ivSidebarLogo.scaleType = ImageView.ScaleType.CENTER_INSIDE
                binding.ivSidebarLogo.setImageBitmap(it)
            }
        }
    }

    private fun updateCheckoutButtonState() {
        val isEnabled = hasCheckoutItems && !isCheckoutSaving && !isPayMongoVerificationRunning
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

    private fun updateProductGridSpanCount() {
        val layoutManager = binding.rvProducts.layoutManager as? GridLayoutManager ?: return
        val configuration = resources.configuration
        val isTablet = configuration.smallestScreenWidthDp >= 600
        val targetSpanCount = when {
            !isTablet -> 2
            configuration.orientation == Configuration.ORIENTATION_LANDSCAPE -> {
                if (isCheckoutExpanded) 4 else 5
            }
            else -> {
                if (isCheckoutExpanded) 3 else 4
            }
        }

        if (layoutManager.spanCount != targetSpanCount) {
            layoutManager.spanCount = targetSpanCount
        }
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

