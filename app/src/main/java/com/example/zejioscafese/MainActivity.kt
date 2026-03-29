package com.example.zejioscafese

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.zejioscafese.databinding.ActivityMainBinding
import com.example.zejioscafese.pos.presentation.PosViewModel
import com.example.zejioscafese.pos.ui.CategoryAdapter
import com.example.zejioscafese.pos.ui.OrderItemAdapter
import com.example.zejioscafese.pos.ui.ProductAdapter

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: PosViewModel by viewModels()

    private lateinit var productAdapter: ProductAdapter
    private lateinit var orderItemAdapter: OrderItemAdapter
    private lateinit var categoryAdapter: CategoryAdapter

    private var isSidebarExpanded: Boolean = true

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

        orderItemAdapter = OrderItemAdapter()
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
            // Placeholder for checkout flow integration.
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
}