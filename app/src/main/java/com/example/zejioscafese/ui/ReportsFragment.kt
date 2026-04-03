package com.example.zejioscafese.ui

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.zejioscafese.R
import com.example.zejioscafese.databinding.FragmentReportsBinding
import com.example.zejioscafese.pos.data.model.CategorySalesRecord
import com.google.android.material.snackbar.Snackbar
import java.util.Locale

class ReportsFragment : Fragment() {

    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ReportsViewModel by viewModels()

    private val categoryColors by lazy {
        mapOf(
            "Drinks" to ContextCompat.getColor(requireContext(), R.color.cat_drinks),
            "Meals" to ContextCompat.getColor(requireContext(), R.color.cat_meals),
            "Desserts" to ContextCompat.getColor(requireContext(), R.color.cat_desserts),
            "Snacks" to ContextCompat.getColor(requireContext(), R.color.cat_snacks),
            "Specials" to ContextCompat.getColor(requireContext(), R.color.cat_specials)
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

    private fun setupDateRangeButtons() {
        binding.btnToday.setOnClickListener {
            viewModel.setDateRange(ReportsViewModel.DateRange.TODAY)
        }
        binding.btnThisWeek.setOnClickListener {
            viewModel.setDateRange(ReportsViewModel.DateRange.THIS_WEEK)
        }
        binding.btnThisMonth.setOnClickListener {
            viewModel.setDateRange(ReportsViewModel.DateRange.THIS_MONTH)
        }
    }

    private fun setupExportButton() {
        binding.btnExport.setOnClickListener {
            Snackbar.make(binding.root, "Export coming soon", Snackbar.LENGTH_SHORT).show()
        }
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

        viewModel.bestCategory.observe(viewLifecycleOwner) { category ->
            binding.tvBestCategory.text = category
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

        viewModel.transactions.observe(viewLifecycleOwner) { transactions ->
            buildTransactionTable(transactions)
        }
    }

    private fun updateDateRangeUI(range: ReportsViewModel.DateRange) {
        val buttons = listOf(
            binding.btnToday to ReportsViewModel.DateRange.TODAY,
            binding.btnThisWeek to ReportsViewModel.DateRange.THIS_WEEK,
            binding.btnThisMonth to ReportsViewModel.DateRange.THIS_MONTH
        )
        buttons.forEach { (btn, r) ->
            val isActive = r == range
            btn.setBackgroundResource(
                if (isActive) R.drawable.bg_date_selector_active else R.drawable.bg_date_selector
            )
            btn.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (isActive) R.color.white else R.color.pos_text_primary
                )
            )
        }
    }

    private fun buildCategoryBreakdown(categories: List<CategorySalesRecord>) {
        val container = binding.categoryBreakdownContainer
        container.removeAllViews()
        val ctx = requireContext()

        val maxRevenue = categories.maxOfOrNull { it.totalRevenue } ?: 1.0

        categories.forEach { record ->
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
                val ratio = record.totalRevenue / maxRevenue
                layoutParams = FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT)
                setBackgroundResource(R.drawable.bg_stock_bar_fill)
                val color = categoryColors[record.categoryName]
                    ?: ContextCompat.getColor(ctx, R.color.chart_bar)
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

    private fun buildTransactionTable(transactions: List<ReportsViewModel.TransactionRecord>) {
        val container = binding.transactionTableBody
        container.removeAllViews()
        val ctx = requireContext()

        transactions.forEachIndexed { index, tx ->
            // Row
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
            }

            row.addView(createTableCell(tx.orderId, 1f, R.color.pos_text_primary))
            row.addView(createTableCell(tx.items, 1.5f, R.color.pos_text_secondary))
            row.addView(createTableCell(
                String.format(Locale.getDefault(), "PHP %,.2f", tx.total),
                1f, R.color.pos_text_primary
            ))
            row.addView(createTableCell(
                tx.status,
                0.8f,
                if (tx.status == "Completed") R.color.pos_secondary else R.color.pos_primary
            ))
            row.addView(createTableCell(tx.date, 1f, R.color.pos_text_secondary))

            container.addView(row)

            // Divider (skip after last)
            if (index < transactions.lastIndex) {
                val divider = View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1)
                    )
                    setBackgroundColor(ContextCompat.getColor(ctx, R.color.pos_chip_bg))
                }
                container.addView(divider)
            }
        }
    }

    private fun createTableCell(text: String, weight: Float, colorRes: Int): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            this.textSize = 13f
            setTextColor(ContextCompat.getColor(requireContext(), colorRes))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
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
