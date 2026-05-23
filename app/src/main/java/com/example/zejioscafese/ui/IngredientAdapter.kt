package com.example.zejioscafese.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.zejioscafese.R
import com.example.zejioscafese.pos.data.model.Ingredient
import com.example.zejioscafese.pos.data.model.IngredientStockStatus
import com.example.zejioscafese.pos.data.model.IngredientUnits
import java.util.Locale

class IngredientAdapter(
    private val onEditClick: (Ingredient) -> Unit,
    private val onRestockClick: (Ingredient) -> Unit
) : ListAdapter<Ingredient, IngredientAdapter.IngredientViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IngredientViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ingredient, parent, false)
        return IngredientViewHolder(view, onEditClick, onRestockClick)
    }

    override fun onBindViewHolder(holder: IngredientViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class IngredientViewHolder(
        itemView: View,
        private val onEditClick: (Ingredient) -> Unit,
        private val onRestockClick: (Ingredient) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvName: TextView = itemView.findViewById(R.id.tvIngredientName)
        private val tvCategory: TextView = itemView.findViewById(R.id.tvIngredientCategory)
        private val tvStock: TextView = itemView.findViewById(R.id.tvCurrentStock)
        private val tvServings: TextView = itemView.findViewById(R.id.tvServings)
        private val tvCostPerUnit: TextView = itemView.findViewById(R.id.tvCostPerUnit)
        private val tvTotalValue: TextView = itemView.findViewById(R.id.tvTotalValue)
        private val tvStockStatus: TextView = itemView.findViewById(R.id.tvStockStatus)
        // UI CHANGE: per-row Edit/Restock buttons collapsed into a kebab menu.
        private val btnRowActions: ImageButton = itemView.findViewById(R.id.btnRowActions)

        // bind() renders the seven required columns using each ingredient's
        // actual unit (pcs / mL / g) so a sauce bottle no longer reads as
        // "2000 piece" and a tapioca bag doesn't read as "5000 piece".
        // The Total Value math is invariant: stock × cost-per-unit always
        // yields the right PHP, regardless of unit, because cost-per-unit
        // is stored in the same unit as the stock count.
        fun bind(ingredient: Ingredient) {
            tvName.text = ingredient.name
            tvCategory.text = ingredient.category

            val unitLabel = displayUnit(ingredient)
            val perServingAmount = ingredient.mlPerServing?.takeIf { it > 0.0 } ?: 1.0
            val totalValue = ingredient.currentStock * ingredient.costPerUnit

            tvStock.text = formatWithUnit(ingredient.currentStock, unitLabel)
            tvServings.text = formatWithUnit(perServingAmount, unitLabel)
            tvCostPerUnit.text = itemView.context.getString(
                R.string.inventory_value_price_per_unit,
                ingredient.costPerUnit,
                unitLabel
            )
            tvTotalValue.text = String.format(Locale.getDefault(), "PHP %,.2f", totalValue)

            applyStockStatusBadge(ingredient.stockStatus)

            btnRowActions.setOnClickListener { anchor ->
                PopupMenu(anchor.context, anchor).apply {
                    menuInflater.inflate(R.menu.inventory_row_actions, menu)
                    setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            R.id.action_edit_ingredient -> {
                                onEditClick(ingredient); true
                            }
                            R.id.action_restock_ingredient -> {
                                onRestockClick(ingredient); true
                            }
                            else -> false
                        }
                    }
                }.show()
            }
        }

        private fun applyStockStatusBadge(status: IngredientStockStatus) {
            val ctx = itemView.context
            when (status) {
                IngredientStockStatus.NO_STOCK -> {
                    tvStockStatus.text = ctx.getString(R.string.inventory_status_no_stock)
                    tvStockStatus.setBackgroundResource(R.drawable.bg_status_no_stock)
                    tvStockStatus.setTextColor(ContextCompat.getColor(ctx, R.color.white))
                }
                IngredientStockStatus.LOW_STOCK -> {
                    tvStockStatus.text = ctx.getString(R.string.inventory_status_low_stock)
                    tvStockStatus.setBackgroundResource(R.drawable.bg_status_low_stock)
                    tvStockStatus.setTextColor(ContextCompat.getColor(ctx, R.color.stock_critical))
                }
                IngredientStockStatus.IN_STOCK -> {
                    tvStockStatus.text = ctx.getString(R.string.inventory_status_in_stock)
                    tvStockStatus.setBackgroundResource(R.drawable.bg_status_in_stock)
                    tvStockStatus.setTextColor(ContextCompat.getColor(ctx, R.color.stock_good))
                }
            }
        }

        // Display-friendly unit label. Resolves to "pcs" / "mL" / "g" via
        // normalize() so any odd unit string in the DB (e.g. "ML", "ml",
        // "shot") still shows as one of the three canonical labels.
        private fun displayUnit(ingredient: Ingredient): String {
            return IngredientUnits.normalize(ingredient.unit)
        }

        private fun formatWithUnit(value: Double, unit: String): String {
            return itemView.context.getString(
                R.string.inventory_value_with_unit,
                formatNumber(value),
                unit
            )
        }

        private fun formatNumber(value: Double): String {
            val rounded = value.coerceAtLeast(0.0)
            return if (rounded % 1.0 == 0.0) {
                rounded.toLong().toString()
            } else {
                String.format(Locale.getDefault(), "%.1f", rounded)
                    .trimEnd('0')
                    .trimEnd('.')
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Ingredient>() {
        override fun areItemsTheSame(oldItem: Ingredient, newItem: Ingredient): Boolean {
            return oldItem.id == newItem.id
        }
        override fun areContentsTheSame(oldItem: Ingredient, newItem: Ingredient): Boolean {
            return oldItem == newItem
        }
    }
}
