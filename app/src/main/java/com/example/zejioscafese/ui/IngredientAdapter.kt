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
import java.util.Locale
import kotlin.math.floor

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

        // UI CHANGE: bind() now renders the seven required columns and never
        // exposes raw ml/L/kg/g. Liquid ingredients are surfaced via their
        // backend-computed servings figures so the user only ever sees
        // "{N} piece" / "PHP X / piece".
        fun bind(ingredient: Ingredient) {
            tvName.text = ingredient.name
            tvCategory.text = ingredient.category

            val servingsRemaining = pieceCountForStock(ingredient)
            val amountPerServing = amountPerServingInPieces(ingredient)
            val pricePerPiece = pricePerPiece(ingredient)
            val totalValue = servingsRemaining * pricePerPiece

            tvStock.text = formatPieces(servingsRemaining)
            tvServings.text = formatPieces(amountPerServing)
            tvCostPerUnit.text = String.format(Locale.getDefault(), "PHP %,.2f", pricePerPiece)
            tvTotalValue.text = String.format(Locale.getDefault(), "PHP %,.2f", totalValue)

            // UI CHANGE: Stock Level badge — ≤10 servings remaining flips the
            // pill into the red "Low Stock" state. Toast notification is
            // fired once per refresh by InventoryFragment, not per row.
            applyStockStatusBadge(servingsRemaining)

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

        private fun applyStockStatusBadge(servingsRemaining: Double) {
            val ctx = itemView.context
            val isLow = servingsRemaining <= LOW_STOCK_THRESHOLD
            if (isLow) {
                tvStockStatus.text = ctx.getString(R.string.inventory_status_low_stock)
                tvStockStatus.setBackgroundResource(R.drawable.bg_status_low_stock)
                tvStockStatus.setTextColor(ContextCompat.getColor(ctx, R.color.stock_critical))
            } else {
                tvStockStatus.text = ctx.getString(R.string.inventory_status_in_stock)
                tvStockStatus.setBackgroundResource(R.drawable.bg_status_in_stock)
                tvStockStatus.setTextColor(ContextCompat.getColor(ctx, R.color.stock_good))
            }
        }

        // For liquids we display the backend-computed total servings; for
        // anything else the raw stock count is already in pieces/units we
        // can label as "piece".
        private fun pieceCountForStock(ingredient: Ingredient): Double {
            return ingredient.totalServings ?: ingredient.currentStock
        }

        // Reference rules: solids show their amount-per-serving (stored in
        // mlPerServing for both types so the existing schema stays put).
        // Liquids show "servings per bottle" = floor(mlPerBottle / mlPerServing).
        private fun amountPerServingInPieces(ingredient: Ingredient): Double {
            if (ingredient.isLiquid) {
                val perBottle = ingredient.mlPerBottle ?: 0.0
                val perServing = ingredient.mlPerServing ?: 0.0
                if (perBottle > 0.0 && perServing > 0.0) {
                    return floor(perBottle / perServing)
                }
                return 0.0
            }
            return ingredient.mlPerServing?.takeIf { it > 0.0 } ?: 1.0
        }

        // Liquids store cost as PHP/ml, so we re-derive the per-serving (per
        // piece) cost. Solids already price by piece.
        private fun pricePerPiece(ingredient: Ingredient): Double {
            return ingredient.costPerServing ?: ingredient.costPerUnit
        }

        private fun formatPieces(value: Double): String {
            val rounded = value.toLong()
            return itemView.context.getString(R.string.inventory_value_pieces, rounded)
        }

        private companion object {
            const val LOW_STOCK_THRESHOLD = 10.0
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
