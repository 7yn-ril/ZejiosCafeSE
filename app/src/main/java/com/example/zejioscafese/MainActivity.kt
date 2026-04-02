// app/src/main/java/com/example/zejioscafese/MainActivity.kt
package com.example.zejioscafese

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.zejioscafese.databinding.ActivityMainBinding
import com.example.zejioscafese.dashboard.DashboardFragment
import com.example.zejioscafese.inventory.InventoryFragment
import com.example.zejioscafese.orders.OrdersFragment
import com.example.zejioscafese.reports.ReportsFragment
import com.example.zejioscafese.staff.StaffFragment
import com.example.zejioscafese.settings.SettingsFragment
import com.example.zejioscafese.pos.presentation.PosViewModel
import com.example.zejioscafese.pos.ui.CategoryAdapter
import com.example.zejioscafese.pos.ui.OrderItemAdapter
import com.example.zejioscafese.pos.ui.ProductAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: PosViewModel by viewModels()

    private lateinit var productAdapter: ProductAdapter
    private lateinit var orderItemAdapter: OrderItemAdapter
    private lateinit var categoryAdapter: CategoryAdapter

    private var isSidebarExpanded: Boolean = true
    private var currentScreen: String = SCREEN_POS

    companion object {
        private const val SCREEN_DASHBOARD = "dashboard"
        private const val SCREEN_POS = "pos"
        private const val SCREEN_ORDERS = "orders"
        private const val SCREEN_INVENTORY = "inventory"
        private const val SCREEN_REPORTS = "reports"
        private const val SCREEN_STAFF = "staff"
        private const val SCREEN_SETTINGS = "settings"

        private const val KEY_CURRENT_SCREEN = "key_current_screen"
    }

    private val sidebarLabels by lazy {
        listOf(
            binding.tvSidebarTitle,
            binding.tvSidebarSubtitle,
            binding.tvSidebarDashboard,
            binding.tvSidebarPos,
            binding.tvSidebarOrders,
            binding.tvSidebarInventory,
            binding.tvSidebarReports,
            binding.tvSidebarStaff,
            binding.tvSidebarSettings
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isSidebarExpanded = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        setupRecyclerViews()
        setupSidebar()
        setupInteractions()
        observeViewModel()
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

        productAdapter = ProductAdapter(onProductClick = viewModel::addProduct)
        binding.rvProducts.apply {
            adapter = productAdapter
            layoutManager = GridLayoutManager(
                this@MainActivity,
                resources.getInteger(R.integer.product_grid_span_count)
            )
        }

        orderItemAdapter = OrderItemAdapter(onItemLongClick = { orderItem ->
            MaterialAlertDialogBuilder(this)
                .setTitle("Remove Item")
                .setMessage("Remove \"${orderItem.product.name}\" from the order?")
                .setPositiveButton("Remove") { _, _ ->
                    viewModel.removeProduct(orderItem.product.id)
                }
                .setNegativeButton("Cancel", null)
                .show()
        })
        binding.rvOrderItems.apply {
            adapter = orderItemAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
    }

    private fun setupSidebar() {
        binding.btnToggleSidebar.setOnClickListener {
            isSidebarExpanded = !isSidebarExpanded
            applySidebarState(isSidebarExpanded, animate = true)
        }

        // Sidebar navigation — all 7 items
        binding.itemDashboard.setOnClickListener { navigateTo(SCREEN_DASHBOARD) }
        binding.itemPos.setOnClickListener { navigateTo(SCREEN_POS) }
        binding.itemOrders.setOnClickListener { navigateTo(SCREEN_ORDERS) }
        binding.itemInventory.setOnClickListener { navigateTo(SCREEN_INVENTORY) }
        binding.itemReports.setOnClickListener { navigateTo(SCREEN_REPORTS) }
        binding.itemStaff.setOnClickListener { navigateTo(SCREEN_STAFF) }
        binding.itemSettings.setOnClickListener { navigateTo(SCREEN_SETTINGS) }
    }

    private fun setupInteractions() {
        binding.etSearch.doAfterTextChanged { text ->
            viewModel.updateSearchQuery(text?.toString().orEmpty())
        }

        binding.btnFilterSort.setOnClickListener {
            // Placeholder for future sorting/filter dialog integration.
        }

        binding.btnClear.setOnClickListener {
            viewModel.clearOrder()
        }

        binding.btnCheckout.setOnClickListener {
            val total = viewModel.total.value ?: 0.0
            if (total <= 0.0) {
                Snackbar.make(binding.root, "Add items to the order first", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val method = when (viewModel.selectedPaymentMethod.value) {
                PosViewModel.PaymentMethod.CASH -> "Cash"
                PosViewModel.PaymentMethod.GCASH -> "GCash"
                PosViewModel.PaymentMethod.CARD -> "Card"
                else -> "Cash"
            }

            MaterialAlertDialogBuilder(this)
                .setTitle("Confirm Order")
                .setMessage("Confirm order of PHP %.2f?\nPayment: %s".format(total, method))
                .setPositiveButton("Confirm") { _, _ ->
                    viewModel.checkout()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.fabCart.setOnClickListener {
            binding.rightPanel.performClick()
        }

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

    private fun applySidebarState(expanded: Boolean, animate: Boolean) {
        val targetWidth = resources.getDimensionPixelSize(
            if (expanded) R.dimen.sidebar_expanded_width else R.dimen.sidebar_collapsed_width
        )

        sidebarLabels.forEach { label ->
            label.visibility = if (expanded) View.VISIBLE else View.GONE
        }

        val startWidth = binding.sidebarContainer.layoutParams.width
        if (!animate || startWidth <= 0) {
            binding.sidebarContainer.updateLayoutParams {
                width = targetWidth
            }
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

    private fun applyPaymentSelection(method: PosViewModel.PaymentMethod) {
        val selectedBg = ContextCompat.getColor(this, R.color.pos_secondary)
        val unselectedBg = ContextCompat.getColor(this, R.color.pos_chip_bg)
        val selectedText = ContextCompat.getColor(this, R.color.white)
        val unselectedText = ContextCompat.getColor(this, R.color.pos_text_primary)

        val buttons = mapOf(
            binding.btnCash to PosViewModel.PaymentMethod.CASH,
            binding.btnGcash to PosViewModel.PaymentMethod.GCASH,
            binding.btnCard to PosViewModel.PaymentMethod.CARD
        )

        buttons.forEach { (button, value) ->
            val isSelected = method == value
            button.backgroundTintList = ColorStateList.valueOf(if (isSelected) selectedBg else unselectedBg)
            button.setTextColor(if (isSelected) selectedText else unselectedText)
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

    // ── Navigation between screens ──────────────────────────
    private fun navigateTo(screen: String) {
        if (screen == currentScreen) return
        currentScreen = screen

        // Views that belong to the inline POS screen
        val posViews = listOf(
            binding.topBar,
            binding.leftPanel,
            binding.rightPanel,
            binding.fabCart
        )

        when (screen) {
            SCREEN_POS -> {
                posViews.forEach { it.visibility = View.VISIBLE }
                binding.fragmentContainer.visibility = View.GONE
                supportFragmentManager.findFragmentById(R.id.fragmentContainer)?.let {
                    supportFragmentManager.beginTransaction().remove(it).commit()
                }
            }
            else -> {
                posViews.forEach { it.visibility = View.GONE }
                binding.fragmentContainer.visibility = View.VISIBLE
                val fragment = when (screen) {
                    SCREEN_DASHBOARD -> DashboardFragment()
                    SCREEN_ORDERS -> OrdersFragment()
                    SCREEN_INVENTORY -> InventoryFragment()
                    SCREEN_REPORTS -> ReportsFragment()
                    SCREEN_STAFF -> StaffFragment()
                    SCREEN_SETTINGS -> SettingsFragment()
                    else -> return
                }
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, fragment)
                    .commit()
            }
        }

        applySidebarHighlight(screen)
    }

    private fun applySidebarHighlight(screen: String) {
        val items = mapOf(
            SCREEN_DASHBOARD to binding.itemDashboard,
            SCREEN_POS to binding.itemPos,
            SCREEN_ORDERS to binding.itemOrders,
            SCREEN_INVENTORY to binding.itemInventory,
            SCREEN_REPORTS to binding.itemReports,
            SCREEN_STAFF to binding.itemStaff,
            SCREEN_SETTINGS to binding.itemSettings
        )

        items.forEach { (key, item) ->
            val isSelected = key == screen
            if (isSelected) {
                item.setBackgroundResource(R.drawable.bg_sidebar_item_selected)
            } else {
                item.background = null
            }
            val iconColor = if (isSelected) R.color.white else R.color.pos_text_secondary
            val textColor = if (isSelected) R.color.white else R.color.pos_text_primary
            val icon = item.getChildAt(0) as? android.widget.ImageView
            val text = item.getChildAt(1) as? android.widget.TextView
            icon?.setColorFilter(ContextCompat.getColor(this, iconColor))
            text?.setTextColor(ContextCompat.getColor(this, textColor))
            if (isSelected) {
                text?.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                text?.setTypeface(null, android.graphics.Typeface.NORMAL)
            }
        }
    }
}