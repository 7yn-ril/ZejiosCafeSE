package com.example.zejioscafese.ui

import android.content.ClipData
import android.content.Intent
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
import androidx.core.content.FileProvider
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.zejioscafese.R
import com.example.zejioscafese.databinding.FragmentReportsBinding
import com.example.zejioscafese.pos.data.model.CategorySalesRecord
import com.example.zejioscafese.pos.data.model.ProductSalesRecord
import com.example.zejioscafese.reports.data.model.ProductGroupFilter
import com.example.zejioscafese.reports.data.model.ReportTransaction
import com.example.zejioscafese.reports.data.model.SalesTimelinePoint
import com.example.zejioscafese.reports.data.model.TypeBreakdown
import com.example.zejioscafese.ui.showErrorDialog
import com.example.zejioscafese.ui.showInfoDialog
import com.example.zejioscafese.ui.showStyledDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
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
            _binding?.let {
                showInfoDialog(requireContext(), getString(R.string.reports_export_excel_cancelled))
            }
            return@registerForActivityResult
        }

        saveExcelReport(uri, export)
    }

    // Hue-separated palette for every chart on the Reports page. We
    // assign by index (sorted position) instead of mapping category
    // names to colors — the old name-map had multiple categories
    // collapsing onto the same hex value, which is why Coffee and Milk
    // Tea looked identical on the donut.
    private val chartPalette by lazy {
        listOf(
            R.color.chart_palette_1,
            R.color.chart_palette_2,
            R.color.chart_palette_3,
            R.color.chart_palette_4,
            R.color.chart_palette_5,
            R.color.chart_palette_6,
            R.color.chart_palette_7,
            R.color.chart_palette_8
        ).map { ContextCompat.getColor(requireContext(), it) }
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
        setupProductGroupFilter()
        setupRevenueChart()
        setupExportButton()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        // Force a refresh on every visit so newly-completed orders show
        // up immediately. The 60s staleness gate used to swallow this:
        // if the user completed an order in Orders and switched back to
        // Reports within a minute, the page kept showing pre-checkout
        // numbers. Forcing here is cheap (one query batch) and matches
        // the user's mental model of "navigate to see fresh data".
        viewModel.refreshReports(force = true)
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

    private fun setupProductGroupFilter() {
        binding.toggleProductGroupFilter.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val filter = when (checkedId) {
                R.id.btnFilterFood -> ProductGroupFilter.FOOD
                R.id.btnFilterDrinks -> ProductGroupFilter.DRINKS
                else -> ProductGroupFilter.ALL
            }
            viewModel.setProductGroupFilter(filter)
        }
    }

    private fun setupRevenueChart() {
        // Tapping a bar drills the KPIs + breakdowns to that single bucket.
        binding.chartRevenueTrend.setOnBarClickListener { point ->
            viewModel.setSelectedBucketLabel(point.label)
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
            orderTypes = viewModel.salesByOrderType.value.orEmpty(),
            paymentMethods = viewModel.salesByPaymentMethod.value.orEmpty(),
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
            val ctx = requireContext()
            result
                .onSuccess {
                    showExcelExportSavedDialog(export)
                }
                .onFailure {
                    showErrorDialog(ctx, getString(R.string.reports_export_excel_failed))
                }
        }
    }

    private fun showExcelExportSavedDialog(export: PendingExcelExport) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.notice_success_title))
            .setMessage(getString(R.string.reports_export_excel_success))
            .setPositiveButton(getString(R.string.reports_export_excel_share)) { _, _ ->
                shareExcelReport(export)
            }
            .setNegativeButton(android.R.string.ok, null)
            .showStyledDialog(requireContext())
    }

    private fun shareExcelReport(export: PendingExcelExport) {
        val ctx = requireContext()
        runCatching {
            val exportDir = File(ctx.cacheDir, EXPORT_CACHE_DIR).apply { mkdirs() }
            val reportFile = File(exportDir, export.fileName)
            reportFile.writeBytes(export.workbook.toByteArray(Charsets.UTF_8))

            val reportUri = FileProvider.getUriForFile(
                ctx,
                "${ctx.packageName}.fileprovider",
                reportFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = EXCEL_MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, reportUri)
                putExtra(Intent.EXTRA_SUBJECT, export.fileName)
                clipData = ClipData.newUri(ctx.contentResolver, export.fileName, reportUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(
                Intent.createChooser(
                    shareIntent,
                    getString(R.string.reports_export_excel_share_title)
                )
            )
        }.onFailure {
            showErrorDialog(ctx, getString(R.string.reports_export_excel_share_failed))
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

        val orderTypeRows = breakdownRows(
            labelHeader = "Order Type",
            rows = data.orderTypes
        )

        val paymentMethodRows = breakdownRows(
            labelHeader = "Payment Method",
            rows = data.paymentMethods
        )

        val transactionRows = listOf(
            listOf(
                textCell("Order ID", STYLE_HEADER),
                textCell("Date/Time", STYLE_HEADER),
                textCell("Order Type", STYLE_HEADER),
                textCell("Payment", STYLE_HEADER),
                textCell("Items", STYLE_HEADER),
                textCell("Item Count", STYLE_HEADER),
                textCell("Subtotal", STYLE_HEADER),
                textCell("Discount", STYLE_HEADER),
                textCell("Discount %", STYLE_HEADER),
                textCell("Discount Amount", STYLE_HEADER),
                textCell("Total", STYLE_HEADER),
                textCell("Status", STYLE_HEADER),
            )
        ) + data.transactions.map { transaction ->
            listOf(
                textCell(transaction.orderId),
                textCell(transaction.date),
                textCell(transaction.orderType),
                textCell(transaction.paymentMethod),
                textCell(transaction.items),
                numberCell(transaction.itemCount),
                numberCell(transaction.subtotal, STYLE_CURRENCY),
                textCell(transaction.discountLabel.orEmpty()),
                transaction.discountPercent?.let { numberCell(it, STYLE_PERCENT) } ?: textCell(""),
                numberCell(transaction.discountAmount, STYLE_CURRENCY),
                numberCell(transaction.total, STYLE_CURRENCY),
                textCell(transaction.status),
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
            appendWorksheet("Order Types", orderTypeRows)
            appendWorksheet("Payment Methods", paymentMethodRows)
            appendWorksheet("Transactions", transactionRows)
            appendLine("</Workbook>")
        }
    }

    private fun breakdownRows(labelHeader: String, rows: List<TypeBreakdown>): List<List<ExcelCell>> {
        return listOf(
            listOf(
                textCell(labelHeader, STYLE_HEADER),
                textCell("Orders", STYLE_HEADER),
                textCell("Revenue", STYLE_HEADER),
                textCell("Percent of Orders", STYLE_HEADER)
            )
        ) + rows.map { row ->
            listOf(
                textCell(row.label),
                numberCell(row.count),
                numberCell(row.revenue, STYLE_CURRENCY),
                numberCell(row.percentage, STYLE_PERCENT)
            )
        }
    }

    private fun StringBuilder.appendWorksheet(
        name: String,
        rows: List<List<ExcelCell>>
    ) {
        appendLine("""<Worksheet ss:Name="${xmlEscape(name.take(31))}">""")
        appendLine("<Table>")
        val columnCount = rows.maxOfOrNull { it.size } ?: 1
        repeat(columnCount) {
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

        viewModel.chartReferenceMax.observe(viewLifecycleOwner) { referenceMax ->
            binding.chartRevenueTrend.setReferenceMax(referenceMax)
        }

        viewModel.selectedBucketLabel.observe(viewLifecycleOwner) { label ->
            binding.chartRevenueTrend.setSelectedLabel(label)
            binding.tvSelectedBucketLabel.text = if (label.isNullOrBlank()) {
                getString(R.string.reports_selected_bucket_default)
            } else {
                getString(R.string.reports_selected_bucket_format, label)
            }
        }

        viewModel.productGroupFilter.observe(viewLifecycleOwner) { filter ->
            updateProductGroupFilterUI(filter)
        }

        viewModel.salesByCategory.observe(viewLifecycleOwner) { categories ->
            buildCategoryBreakdown(categories)
        }

        viewModel.salesByOrderType.observe(viewLifecycleOwner) { breakdown ->
            renderTypeBreakdown(
                bar = binding.orderTypeBar,
                legend = binding.orderTypeLegend,
                rows = breakdown
            )
        }

        viewModel.salesByPaymentMethod.observe(viewLifecycleOwner) { breakdown ->
            renderTypeBreakdown(
                bar = binding.paymentMethodBar,
                legend = binding.paymentMethodLegend,
                rows = breakdown
            )
        }

        viewModel.salesByProduct.observe(viewLifecycleOwner) { products ->
            buildProductBreakdown(products)
        }

        viewModel.reportError.observe(viewLifecycleOwner) { errorMessage ->
            if (!errorMessage.isNullOrBlank()) {
                showErrorDialog(requireContext(), errorMessage)
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

    private fun updateProductGroupFilterUI(filter: ProductGroupFilter) {
        val targetButtonId = when (filter) {
            ProductGroupFilter.FOOD -> R.id.btnFilterFood
            ProductGroupFilter.DRINKS -> R.id.btnFilterDrinks
            ProductGroupFilter.ALL -> R.id.btnFilterAll
        }
        if (binding.toggleProductGroupFilter.checkedButtonId != targetButtonId) {
            binding.toggleProductGroupFilter.check(targetButtonId)
        }

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
            binding.btnFilterAll,
            binding.btnFilterFood,
            binding.btnFilterDrinks
        ).forEach { button ->
            styleDateRangeButton(
                button = button,
                selected = button.id == targetButtonId,
                selectedBackground = selectedBackground,
                unselectedBackground = unselectedBackground,
                selectedTextColor = selectedTextColor,
                unselectedTextColor = unselectedTextColor,
                selectedStroke = selectedStroke,
                unselectedStroke = unselectedStroke
            )
        }
    }

    private fun categoryColorFor(@Suppress("UNUSED_PARAMETER") name: String, index: Int): Int {
        // Slice color is purely positional now — categories sort by
        // revenue, so the same #1 category always gets palette[0].
        return chartPalette[index % chartPalette.size]
    }

    // Caps the donut + legend at 6 entries: top 5 by revenue plus a merged
    // "Other (N)" bucket. Categories arrive pre-sorted descending by
    // revenue, so the long tail is just whatever sits past index 4.
    private fun capCategoriesForDonut(categories: List<CategorySalesRecord>): List<CategorySalesRecord> {
        if (categories.size <= DONUT_CATEGORY_LIMIT) return categories
        val visible = categories.take(DONUT_CATEGORY_LIMIT - 1)
        val tail = categories.drop(DONUT_CATEGORY_LIMIT - 1)
        val mergedBucket = CategorySalesRecord(
            categoryName = getString(R.string.reports_category_other_bucket, tail.size),
            totalRevenue = tail.sumOf(CategorySalesRecord::totalRevenue),
            itemsSold = tail.sumOf(CategorySalesRecord::itemsSold),
            percentageOfTotal = tail.sumOf(CategorySalesRecord::percentageOfTotal)
        )
        return visible + mergedBucket
    }

    private fun buildCategoryBreakdown(categories: List<CategorySalesRecord>) {
        val ctx = requireContext()
        val donut = binding.categoryDonutChart
        val legendContainer = binding.categoryBreakdownContainer
        legendContainer.removeAllViews()

        if (categories.isEmpty()) {
            donut.setSlices(emptyList(), getString(R.string.reports_empty_categories_short))
            legendContainer.addView(
                TextView(ctx).apply {
                    text = getString(R.string.reports_empty_categories)
                    textSize = 13f
                    setTextColor(ContextCompat.getColor(ctx, R.color.pos_text_secondary))
                }
            )
            return
        }

        val totalRevenue = categories.sumOf(CategorySalesRecord::totalRevenue)
        val displayCategories = capCategoriesForDonut(categories)
        val slices = displayCategories.mapIndexed { index, record ->
            CategoryDonutChartView.Slice(
                label = record.categoryName,
                value = record.totalRevenue,
                color = categoryColorFor(record.categoryName, index)
            )
        }
        donut.setSlices(slices, CategoryDonutChartView.formatCenterTotal(totalRevenue))

        displayCategories.forEachIndexed { index, record ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(6), 0, dpToPx(6))
            }

            val swatch = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(10), dpToPx(10))
                background = ContextCompat.getDrawable(ctx, R.drawable.bg_stock_bar_fill)
                backgroundTintList = ColorStateList.valueOf(
                    categoryColorFor(record.categoryName, index)
                )
            }
            row.addView(swatch)

            val nameLabel = TextView(ctx).apply {
                text = record.categoryName
                textSize = 13f
                setTextColor(ContextCompat.getColor(ctx, R.color.pos_text_primary))
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply { marginStart = dpToPx(8) }
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            row.addView(nameLabel)

            val statsLabel = TextView(ctx).apply {
                text = String.format(
                    Locale.getDefault(),
                    "PHP %,.2f  ·  %.1f%%",
                    record.totalRevenue,
                    record.percentageOfTotal
                )
                textSize = 12f
                setTextColor(ContextCompat.getColor(ctx, R.color.pos_text_secondary))
            }
            row.addView(statsLabel)

            legendContainer.addView(row)
        }
    }

    private fun buildProductBreakdown(products: List<ProductSalesRecord>) {
        val ctx = requireContext()
        val container = binding.productBreakdownContainer
        container.removeAllViews()

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

        val ranked = products.take(LEADERBOARD_LIMIT)
        val maxRevenue = ranked.maxOf(ProductSalesRecord::totalRevenue).coerceAtLeast(1.0)

        ranked.forEachIndexed { index, record ->
            container.addView(buildProductBarRow(ctx, index + 1, record, maxRevenue))
        }
    }

    /**
     * Polished horizontal-bar row for a single top-product entry. Layout:
     *   - Bold rank number on the far left.
     *   - Two stacked text lines (name + stats) above the fill bar.
     *   - Track + colored fill underneath. Width is proportional to the
     *     top product's revenue so the leader always reads as 100%.
     */
    private fun buildProductBarRow(
        ctx: android.content.Context,
        rank: Int,
        record: ProductSalesRecord,
        maxRevenue: Double
    ): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = if (rank == 1) 0 else dpToPx(14) }
        }

        val rankView = TextView(ctx).apply {
            text = rank.toString()
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.pos_text_secondary))
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dpToPx(28), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        row.addView(rankView)

        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply { marginStart = dpToPx(8) }
        }

        val labelRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        labelRow.addView(
            TextView(ctx).apply {
                text = record.productName
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(ctx, R.color.pos_text_primary))
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
        )

        labelRow.addView(
            TextView(ctx).apply {
                text = getString(
                    R.string.reports_product_row_stats_format,
                    record.totalRevenue,
                    record.itemsSold,
                    record.percentageOfTotal
                )
                textSize = 12f
                setTextColor(ContextCompat.getColor(ctx, R.color.pos_text_secondary))
            }
        )

        content.addView(labelRow)

        val barTrack = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(8)
            ).apply { topMargin = dpToPx(6) }
            setBackgroundResource(R.drawable.bg_stock_bar_track)
        }

        val barFill = View(ctx).apply {
            // Width = proportion against the #1 product's revenue, so #1
            // always reads as a full bar and the rest fall in line.
            val ratio = (record.totalRevenue / maxRevenue).coerceIn(0.0, 1.0)
            layoutParams = FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT)
            setBackgroundResource(R.drawable.bg_stock_bar_fill)
            backgroundTintList = ColorStateList.valueOf(productBarColor(rank))
            post {
                val params = layoutParams as FrameLayout.LayoutParams
                params.width = (barTrack.width * ratio).toInt()
                layoutParams = params
            }
        }
        barTrack.addView(barFill)
        content.addView(barTrack)

        row.addView(content)
        return row
    }

    private fun productBarColor(rank: Int): Int {
        // Same palette as the donut so the visual story is consistent
        // across the two cards. rank is 1-based, so subtract one.
        return chartPalette[(rank - 1) % chartPalette.size]
    }

    /**
     * Renders a Order-Type / Payment-Method mini summary: a thin
     * proportional segmented bar, plus a vertical legend of "Label · count
     * (pct%)" rows underneath. The bar uses LinearLayout weights so
     * proportions stay correct as the parent column flexes.
     */
    private fun renderTypeBreakdown(
        bar: LinearLayout,
        legend: LinearLayout,
        rows: List<TypeBreakdown>
    ) {
        val ctx = requireContext()
        bar.removeAllViews()
        legend.removeAllViews()

        if (rows.isEmpty()) {
            legend.addView(
                TextView(ctx).apply {
                    text = getString(R.string.reports_breakdown_empty)
                    textSize = 11f
                    setTextColor(ContextCompat.getColor(ctx, R.color.pos_text_secondary))
                }
            )
            return
        }

        rows.forEachIndexed { index, item ->
            val color = breakdownColor(index)
            val segment = View(ctx).apply {
                setBackgroundColor(color)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    item.percentage.toFloat().coerceAtLeast(0.001f)
                )
            }
            bar.addView(segment)

            val legendRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { if (index > 0) topMargin = dpToPx(4) }
            }

            val swatch = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(8), dpToPx(8))
                background = ContextCompat.getDrawable(ctx, R.drawable.bg_stock_bar_fill)
                backgroundTintList = ColorStateList.valueOf(color)
            }
            legendRow.addView(swatch)

            val text = TextView(ctx).apply {
                text = getString(
                    R.string.reports_breakdown_row_format,
                    item.label,
                    item.count,
                    item.percentage
                )
                textSize = 12f
                setTextColor(ContextCompat.getColor(ctx, R.color.pos_text_primary))
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply { marginStart = dpToPx(8) }
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            legendRow.addView(text)

            legend.addView(legendRow)
        }
    }

    private fun breakdownColor(index: Int): Int {
        return chartPalette[index % chartPalette.size]
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
        val orderTypes: List<TypeBreakdown>,
        val paymentMethods: List<TypeBreakdown>,
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
        const val EXPORT_CACHE_DIR = "report_exports"
        const val LEADERBOARD_LIMIT = 10
        const val DONUT_CATEGORY_LIMIT = 6
        val EXPORT_FILE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
        val EXPORT_DISPLAY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
