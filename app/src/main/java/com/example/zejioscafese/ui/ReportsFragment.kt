package com.example.zejioscafese.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.Fragment
import com.example.zejioscafese.R
import com.example.zejioscafese.databinding.FragmentReportsBinding
import com.example.zejioscafese.pos.data.model.CategorySalesRecord
import com.example.zejioscafese.pos.data.model.ProductSalesRecord
import com.example.zejioscafese.ui.showStyledDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.util.Locale

class ReportsFragment : Fragment() {

    private var _binding: FragmentReportsBinding? = null
    private val binding: FragmentReportsBinding
        get() = requireNotNull(_binding) { "Reports view binding is only valid between onCreateView and onDestroyView." }
    private val viewModel: ReportsViewModel by activityViewModels()

    private val categoryColors by lazy {
        mapOf(
            "Milk Tea" to ContextCompat.getColor(requireContext(), R.color.cat_drinks),
            "Coffee" to ContextCompat.getColor(requireContext(), R.color.pos_primary),
            "Non-Coffee" to ContextCompat.getColor(requireContext(), R.color.cat_specials),
            "Burgers" to ContextCompat.getColor(requireContext(), R.color.cat_meals),
            "Wings" to ContextCompat.getColor(requireContext(), R.color.cat_snacks),
            "Rice Meals" to ContextCompat.getColor(requireContext(), R.color.cat_meals),
            "Appetizers & Sides" to ContextCompat.getColor(requireContext(), R.color.cat_desserts),
            "Combo Meals" to ContextCompat.getColor(requireContext(), R.color.pos_secondary)
        )
    }
    private val fallbackCategoryPalette by lazy {
        listOf(
            ContextCompat.getColor(requireContext(), R.color.cat_drinks),
            ContextCompat.getColor(requireContext(), R.color.cat_meals),
            ContextCompat.getColor(requireContext(), R.color.cat_desserts),
            ContextCompat.getColor(requireContext(), R.color.cat_snacks),
            ContextCompat.getColor(requireContext(), R.color.cat_specials),
            ContextCompat.getColor(requireContext(), R.color.pos_secondary)
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDateRangeButtons()
        setupExportButton()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshReportsIfStale()
    }

    private fun setupDateRangeButtons() {
        binding.toggleReportRangeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener

            val range = when (checkedId) {
                R.id.btnReportHourly -> ReportsViewModel.DateRange.HOURLY
                R.id.btnReportWeekly -> ReportsViewModel.DateRange.WEEKLY
                R.id.btnReportMonthly -> ReportsViewModel.DateRange.MONTHLY
                else -> ReportsViewModel.DateRange.DAILY
            }
            viewModel.setDateRange(range)
        }
    }

    private fun setupExportButton() {
        binding.btnExport.setOnClickListener {
            showExportDialog()
        }
    }

    private fun showExportDialog() {
        val selectedRange = viewModel.selectedRange.value ?: ReportsViewModel.DateRange.DAILY
        val revenueText = String.format(
            Locale.getDefault(),
            "PHP %,.2f",
            viewModel.totalRevenue.value ?: 0.0
        )
        val averageText = String.format(
            Locale.getDefault(),
            "PHP %,.2f",
            viewModel.avgOrderValue.value ?: 0.0
        )
        val exportSummary = buildString {
            appendLine(getString(R.string.reports_export_range_summary, getString(selectedRange.labelRes)))
            appendLine(getString(R.string.reports_export_revenue_summary, revenueText))
            appendLine(getString(R.string.reports_export_orders_summary, viewModel.totalOrders.value ?: 0))
            appendLine(getString(R.string.reports_export_average_summary, averageText))
            append(
                getString(
                    R.string.reports_export_best_product_summary,
                    viewModel.bestProduct.value ?: getString(R.string.reports_best_seller_empty)
                )
            )
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.reports_export_dialog_title))
            .setMessage(exportSummary)
            .setPositiveButton(getString(R.string.reports_export_copy)) { _, _ ->
                copyExportSummary(exportSummary)
            }
            .setNeutralButton(getString(R.string.reports_export_refresh)) { _, _ ->
                viewModel.refreshReports(force = true)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .showStyledDialog(requireContext())
    }

    private fun copyExportSummary(summary: String) {
        val clipboardManager = requireContext().getSystemService(ClipboardManager::class.java)
        clipboardManager?.setPrimaryClip(ClipData.newPlainText("reports-summary", summary))
        Snackbar.make(
            binding.root,
            getString(R.string.reports_export_copied),
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun observeViewModel() {
        viewModel.totalRevenue.observe(viewLifecycleOwner) { revenue ->
            binding.tvReportRevenue.text = String.format(Locale.getDefault(), "PHP %,.2f", revenue)
        }

        viewModel.totalOrders.observe(viewLifecycleOwner) { orders ->
            binding.tvReportOrders.text = orders.toString()
        }

        viewModel.avgOrderValue.observe(viewLifecycleOwner) { avg ->
            binding.tvReportAvgOrder.text = String.format(Locale.getDefault(), "PHP %,.2f", avg)
        }

        viewModel.bestProduct.observe(viewLifecycleOwner) { product ->
            binding.tvBestCategory.text = product
        }

        viewModel.selectedRange.observe(viewLifecycleOwner) { range ->
            updateDateRangeUI(range)
        }

        viewModel.salesByDateRange.observe(viewLifecycleOwner) { salesData ->
            binding.chartRevenueTrend.setData(salesData)
        }

        viewModel.salesByCategory.observe(viewLifecycleOwner) { categories ->
            buildCategoryBreakdown(categories)
        }

        viewModel.salesByProduct.observe(viewLifecycleOwner) { products ->
            buildProductBreakdown(products)
        }

        viewModel.reportError.observe(viewLifecycleOwner) { errorMessage ->
            if (!errorMessage.isNullOrBlank()) {
                Snackbar.make(binding.root, errorMessage, Snackbar.LENGTH_LONG).show()
                viewModel.onReportErrorConsumed()
            }
        }
    }

    private fun updateDateRangeUI(range: ReportsViewModel.DateRange) {
        val selectedButtonId = when (range) {
            ReportsViewModel.DateRange.HOURLY -> R.id.btnReportHourly
            ReportsViewModel.DateRange.DAILY -> R.id.btnReportDaily
            ReportsViewModel.DateRange.WEEKLY -> R.id.btnReportWeekly
            ReportsViewModel.DateRange.MONTHLY -> R.id.btnReportMonthly
        }

        if (binding.toggleReportRangeGroup.checkedButtonId != selectedButtonId) {
            binding.toggleReportRangeGroup.check(selectedButtonId)
        }

        styleDateRangeButtons(selectedButtonId)
        binding.tvRevenueTrendSubtitle.setText(range.subtitleRes)
    }

    private fun styleDateRangeButtons(selectedButtonId: Int) {
        val selectedBackground = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), R.color.pos_primary)
        )
        val unselectedBackground = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), R.color.pos_surface)
        )
        val selectedTextColor = ContextCompat.getColor(requireContext(), R.color.white)
        val unselectedTextColor = ContextCompat.getColor(requireContext(), R.color.pos_text_secondary)
        val selectedStroke = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), R.color.pos_primary)
        )
        val unselectedStroke = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), R.color.pos_border)
        )

        listOf(
            binding.btnReportHourly,
            binding.btnReportDaily,
            binding.btnReportWeekly,
            binding.btnReportMonthly
        ).forEach { button ->
            styleDateRangeButton(
                button = button,
                selected = button.id == selectedButtonId,
                selectedBackground = selectedBackground,
                unselectedBackground = unselectedBackground,
                selectedTextColor = selectedTextColor,
                unselectedTextColor = unselectedTextColor,
                selectedStroke = selectedStroke,
                unselectedStroke = unselectedStroke
            )
        }
    }

    private fun styleDateRangeButton(
        button: MaterialButton,
        selected: Boolean,
        selectedBackground: ColorStateList,
        unselectedBackground: ColorStateList,
        selectedTextColor: Int,
        unselectedTextColor: Int,
        selectedStroke: ColorStateList,
        unselectedStroke: ColorStateList
    ) {
        button.backgroundTintList = if (selected) selectedBackground else unselectedBackground
        button.setTextColor(if (selected) selectedTextColor else unselectedTextColor)
        button.strokeColor = if (selected) selectedStroke else unselectedStroke
    }

    private fun buildCategoryBreakdown(categories: List<CategorySalesRecord>) {
        val container = binding.categoryBreakdownContainer
        container.removeAllViews()
        val ctx = requireContext()

        if (categories.isEmpty()) {
            container.addView(
                TextView(ctx).apply {
                    text = getString(R.string.reports_empty_categories)
                    textSize = 13f
                    setTextColor(ContextCompat.getColor(ctx, R.color.pos_text_secondary))
                }
            )
            return
        }

        val maxRevenue = (categories.maxOfOrNull { it.totalRevenue } ?: 0.0).coerceAtLeast(1.0)

        categories.forEachIndexed { index, record ->
            val rowLayout = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dpToPx(6), 0, dpToPx(6))
            }

            // Label row
            val labelRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val nameLabel = TextView(ctx).apply {
                text = record.categoryName
                textSize = 13f
                setTextColor(ContextCompat.getColor(ctx, R.color.pos_text_primary))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            labelRow.addView(nameLabel)

            val statsLabel = TextView(ctx).apply {
                text = String.format(Locale.getDefault(), "PHP %,.2f  ·  %.1f%%", record.totalRevenue, record.percentageOfTotal)
                textSize = 12f
                setTextColor(ContextCompat.getColor(ctx, R.color.pos_text_secondary))
            }
            labelRow.addView(statsLabel)
            rowLayout.addView(labelRow)

            // Bar
            val barTrack = FrameLayout(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(10)
                ).apply { topMargin = dpToPx(4) }
                setBackgroundResource(R.drawable.bg_stock_bar_track)
            }

            val barFill = View(ctx).apply {
                val ratio = (record.totalRevenue / maxRevenue).coerceIn(0.0, 1.0)
                layoutParams = FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT)
                setBackgroundResource(R.drawable.bg_stock_bar_fill)
                val color = categoryColors[record.categoryName]
                    ?: fallbackCategoryPalette[index % fallbackCategoryPalette.size]
                backgroundTintList = android.content.res.ColorStateList.valueOf(color)

                post {
                    val params = layoutParams as FrameLayout.LayoutParams
                    params.width = (barTrack.width * ratio).toInt()
                    layoutParams = params
                }
            }
            barTrack.addView(barFill)
            rowLayout.addView(barTrack)
            container.addView(rowLayout)
        }
    }

    private fun buildProductBreakdown(products: List<ProductSalesRecord>) {
        val container = binding.productBreakdownContainer
        container.removeAllViews()
        val ctx = requireContext()

        if (products.isEmpty()) {
            container.addView(
                TextView(ctx).apply {
                    text = getString(R.string.reports_empty_products)
                    textSize = 13f
                    setTextColor(ContextCompat.getColor(ctx, R.color.pos_text_secondary))
                }
            )
            return
        }

        val maxRevenue = (products.maxOfOrNull { it.totalRevenue } ?: 0.0).coerceAtLeast(1.0)

        products.forEachIndexed { index, record ->
            val rowLayout = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dpToPx(6), 0, dpToPx(6))
            }

            val labelRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val nameLabel = TextView(ctx).apply {
                text = record.productName
                textSize = 13f
                setTextColor(ContextCompat.getColor(ctx, R.color.pos_text_primary))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            labelRow.addView(nameLabel)

            val statsLabel = TextView(ctx).apply {
                text = String.format(
                    Locale.getDefault(),
                    "PHP %,.2f  ·  %d sold  ·  %.1f%%",
                    record.totalRevenue,
                    record.itemsSold,
                    record.percentageOfTotal
                )
                textSize = 12f
                setTextColor(ContextCompat.getColor(ctx, R.color.pos_text_secondary))
            }
            labelRow.addView(statsLabel)
            rowLayout.addView(labelRow)

            val barTrack = FrameLayout(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(10)
                ).apply { topMargin = dpToPx(4) }
                setBackgroundResource(R.drawable.bg_stock_bar_track)
            }

            val barFill = View(ctx).apply {
                val ratio = (record.totalRevenue / maxRevenue).coerceIn(0.0, 1.0)
                layoutParams = FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT)
                setBackgroundResource(R.drawable.bg_stock_bar_fill)
                val color = fallbackCategoryPalette[index % fallbackCategoryPalette.size]
                backgroundTintList = ColorStateList.valueOf(color)

                post {
                    val params = layoutParams as FrameLayout.LayoutParams
                    params.width = (barTrack.width * ratio).toInt()
                    layoutParams = params
                }
            }
            barTrack.addView(barFill)
            rowLayout.addView(barTrack)
            container.addView(rowLayout)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
