package com.example.zejioscafese.ui

import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.zejioscafese.R
import com.example.zejioscafese.databinding.FragmentReportsBinding
import com.example.zejioscafese.pos.data.model.CategorySalesRecord
import com.example.zejioscafese.pos.data.model.ProductSalesRecord
import com.example.zejioscafese.reports.data.model.ReportTransaction
import com.example.zejioscafese.reports.data.model.SalesTimelinePoint
import com.example.zejioscafese.ui.showStyledDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReportsFragment : Fragment() {

    private var _binding: FragmentReportsBinding? = null
    private val binding: FragmentReportsBinding
        get() = requireNotNull(_binding) { "Reports view binding is only valid between onCreateView and onDestroyView." }
    private val viewModel: ReportsViewModel by activityViewModels()
    private var pendingExcelExport: PendingExcelExport? = null

    private val createReportDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument(EXCEL_MIME_TYPE)
    ) { uri ->
        val export = pendingExcelExport
        pendingExcelExport = null
        if (export == null) return@registerForActivityResult

        if (uri == null) {
            _binding?.root?.let { root ->
                Snackbar.make(
                    root,
                    getString(R.string.reports_export_excel_cancelled),
                    Snackbar.LENGTH_SHORT
                ).show()
            }
            return@registerForActivityResult
        }

        saveExcelReport(uri, export)
    }

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
                R.id.btnReportYearly -> ReportsViewModel.DateRange.YEARLY
                else -> ReportsViewModel.DateRange.DAILY
            }
            viewModel.setDateRange(range)
        }
    }

    private fun setupExportButton() {
        binding.btnExport.setOnClickListener {
            showExportDownloadDialog()
        }
    }

    private fun showExportDownloadDialog() {
        val selectedRange = viewModel.selectedRange.value ?: ReportsViewModel.DateRange.DAILY
        val generatedAt = LocalDateTime.now().format(EXPORT_DISPLAY_FORMATTER)
        val fileStamp = LocalDateTime.now().format(EXPORT_FILE_FORMATTER)
        val rangeLabel = getString(selectedRange.labelRes)
        val fileName = "ZejiosCafe_${rangeLabel}_Report_$fileStamp.xls"
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val exportData = ReportExportData(
            rangeLabel = rangeLabel,
            generatedAt = generatedAt,
            totalRevenue = viewModel.totalRevenue.value ?: 0.0,
            totalOrders = viewModel.totalOrders.value ?: 0,
            averageOrderValue = viewModel.avgOrderValue.value ?: 0.0,
            bestProduct = viewModel.bestProduct.value ?: getString(R.string.reports_best_seller_empty),
            timeline = viewModel.salesByDateRange.value.orEmpty(),
            categories = viewModel.salesByCategory.value.orEmpty(),
            products = viewModel.salesByProduct.value.orEmpty(),
            transactions = viewModel.transactions.value.orEmpty()
        )

        val workbook = buildExcelWorkbook(exportData)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.reports_export_excel_dialog_title))
            .setMessage(
                getString(
                    R.string.reports_export_excel_dialog_message,
                    fileName,
                    rangeLabel
                )
            )
            .setPositiveButton(getString(R.string.reports_export_excel_download)) { _, _ ->
                pendingExcelExport = PendingExcelExport(
                    fileName = fileName,
                    workbook = workbook
                )
                createReportDocumentLauncher.launch(fileName)
            }
            .setNeutralButton(getString(R.string.reports_export_refresh)) { _, _ ->
                viewModel.refreshReports(force = true)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .showStyledDialog(requireContext())
    }

    private fun saveExcelReport(uri: Uri, export: PendingExcelExport) {
        val appContext = requireContext().applicationContext
        binding.btnExport.isEnabled = false
        Snackbar.make(
            binding.root,
            getString(R.string.reports_export_excel_progress),
            Snackbar.LENGTH_SHORT
        ).show()

        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(export.workbook.toByteArray(Charsets.UTF_8))
                    } ?: throw IOException("Could not open report output stream.")
                }
            }

            val currentBinding = _binding ?: return@launch
            currentBinding.btnExport.isEnabled = true
            result
                .onSuccess {
                    Snackbar.make(
                        currentBinding.root,
                        getString(R.string.reports_export_excel_success),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
                .onFailure {
                    Snackbar.make(
                        currentBinding.root,
                        getString(R.string.reports_export_excel_failed),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun buildExcelWorkbook(data: ReportExportData): String {
        val summaryRows = listOf(
            listOf(textCell("Zejios Cafe Report", STYLE_TITLE)),
            listOf(textCell("Metric", STYLE_HEADER), textCell("Value", STYLE_HEADER)),
            listOf(textCell("Range"), textCell(data.rangeLabel)),
            listOf(textCell("Generated At"), textCell(data.generatedAt)),
            listOf(textCell("Total Revenue"), numberCell(data.totalRevenue, STYLE_CURRENCY)),
            listOf(textCell("Total Orders"), numberCell(data.totalOrders)),
            listOf(textCell("Average Order Value"), numberCell(data.averageOrderValue, STYLE_CURRENCY)),
            listOf(textCell("Best Product"), textCell(data.bestProduct))
        )

        val trendRows = listOf(
            listOf(
                textCell("Period", STYLE_HEADER),
                textCell("Revenue", STYLE_HEADER),
                textCell("Orders", STYLE_HEADER),
                textCell("Average Order Value", STYLE_HEADER)
            )
        ) + data.timeline.map { point ->
            listOf(
                textCell(point.label),
                numberCell(point.totalSales, STYLE_CURRENCY),
                numberCell(point.totalOrders),
                numberCell(point.averageOrderValue, STYLE_CURRENCY)
            )
        }

        val categoryRows = listOf(
            listOf(
                textCell("Category", STYLE_HEADER),
                textCell("Revenue", STYLE_HEADER),
                textCell("Items Sold", STYLE_HEADER),
                textCell("Percent of Total", STYLE_HEADER)
            )
        ) + data.categories.map { category ->
            listOf(
                textCell(category.categoryName),
                numberCell(category.totalRevenue, STYLE_CURRENCY),
                numberCell(category.itemsSold),
                numberCell(category.percentageOfTotal, STYLE_PERCENT)
            )
        }

        val productRows = listOf(
            listOf(
                textCell("Product", STYLE_HEADER),
                textCell("Revenue", STYLE_HEADER),
                textCell("Items Sold", STYLE_HEADER),
                textCell("Percent of Total", STYLE_HEADER)
            )
        ) + data.products.map { product ->
            listOf(
                textCell(product.productName),
                numberCell(product.totalRevenue, STYLE_CURRENCY),
                numberCell(product.itemsSold),
                numberCell(product.percentageOfTotal, STYLE_PERCENT)
            )
        }

        val transactionRows = listOf(
            listOf(
                textCell("Order ID", STYLE_HEADER),
                textCell("Date", STYLE_HEADER),
                textCell("Items", STYLE_HEADER),
                textCell("Status", STYLE_HEADER),
                textCell("Total", STYLE_HEADER)
            )
        ) + data.transactions.map { transaction ->
            listOf(
                textCell(transaction.orderId),
                textCell(transaction.date),
                textCell(transaction.items),
                textCell(transaction.status),
                numberCell(transaction.total, STYLE_CURRENCY)
            )
        }

        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<?mso-application progid="Excel.Sheet"?>""")
            appendLine("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\"")
            appendLine(" xmlns:o=\"urn:schemas-microsoft-com:office:office\"")
            appendLine(" xmlns:x=\"urn:schemas-microsoft-com:office:excel\"")
            appendLine(" xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\">")
            appendLine("<Styles>")
            appendLine("""<Style ss:ID="$STYLE_TITLE"><Font ss:Bold="1" ss:Size="16"/></Style>""")
            appendLine("""<Style ss:ID="$STYLE_HEADER"><Font ss:Bold="1" ss:Color="#FFFFFF"/><Interior ss:Color="#6B4A3D" ss:Pattern="Solid"/></Style>""")
            appendLine("""<Style ss:ID="$STYLE_CURRENCY"><NumberFormat ss:Format="&quot;PHP&quot; #,##0.00"/></Style>""")
            appendLine("""<Style ss:ID="$STYLE_PERCENT"><NumberFormat ss:Format="0.0"/></Style>""")
            appendLine("</Styles>")
            appendWorksheet("Summary", summaryRows)
            appendWorksheet("Revenue Trend", trendRows)
            appendWorksheet("Categories", categoryRows)
            appendWorksheet("Products", productRows)
            appendWorksheet("Transactions", transactionRows)
            appendLine("</Workbook>")
        }
    }

    private fun StringBuilder.appendWorksheet(
        name: String,
        rows: List<List<ExcelCell>>
    ) {
        appendLine("""<Worksheet ss:Name="${xmlEscape(name.take(31))}">""")
        appendLine("<Table>")
        repeat(6) {
            appendLine("""<Column ss:AutoFitWidth="1" ss:Width="140"/>""")
        }
        rows.forEach { cells ->
            append("<Row>")
            cells.forEach { cell ->
                val style = cell.styleId?.let { " ss:StyleID=\"$it\"" }.orEmpty()
                append("""<Cell$style><Data ss:Type="${cell.type}">${xmlEscape(cell.value)}</Data></Cell>""")
            }
            appendLine("</Row>")
        }
        appendLine("</Table>")
        appendLine("</Worksheet>")
    }

    private fun textCell(value: String, styleId: String? = null): ExcelCell {
        return ExcelCell(value = value, type = EXCEL_TYPE_STRING, styleId = styleId)
    }

    private fun numberCell(value: Number, styleId: String? = null): ExcelCell {
        return ExcelCell(
            value = when (value) {
                is Float, is Double -> String.format(Locale.US, "%.2f", value.toDouble())
                else -> value.toLong().toString()
            },
            type = EXCEL_TYPE_NUMBER,
            styleId = styleId
        )
    }

    private fun xmlEscape(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
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
            ReportsViewModel.DateRange.YEARLY -> R.id.btnReportYearly
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
            binding.btnReportMonthly,
            binding.btnReportYearly
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

    private data class ReportExportData(
        val rangeLabel: String,
        val generatedAt: String,
        val totalRevenue: Double,
        val totalOrders: Int,
        val averageOrderValue: Double,
        val bestProduct: String,
        val timeline: List<SalesTimelinePoint>,
        val categories: List<CategorySalesRecord>,
        val products: List<ProductSalesRecord>,
        val transactions: List<ReportTransaction>
    )

    private data class ExcelCell(
        val value: String,
        val type: String,
        val styleId: String? = null
    )

    private data class PendingExcelExport(
        val fileName: String,
        val workbook: String
    )

    private companion object {
        const val EXCEL_MIME_TYPE = "application/vnd.ms-excel"
        const val EXCEL_TYPE_STRING = "String"
        const val EXCEL_TYPE_NUMBER = "Number"
        const val STYLE_TITLE = "Title"
        const val STYLE_HEADER = "Header"
        const val STYLE_CURRENCY = "Currency"
        const val STYLE_PERCENT = "Percent"
        val EXPORT_FILE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
        val EXPORT_DISPLAY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
