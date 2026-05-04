package com.example.zejioscafese.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.zejioscafese.R
import com.example.zejioscafese.pos.data.model.Ingredient
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
        private val stockBarTrack: View = itemView.findViewById(R.id.stockBarTrack)
        private val stockBarFill: View = itemView.findViewById(R.id.stockBarFill)
        private val btnEdit: View = itemView.findViewById(R.id.btnEditIngredient)
        private val btnRestock: View = itemView.findViewById(R.id.btnRestockIngredient)

        fun bind(ingredient: Ingredient) {
            tvName.text = ingredient.name
            tvCategory.text = ingredient.category

            // Stock display
            tvStock.text = String.format(Locale.getDefault(), "%.1f %s", ingredient.currentStock, ingredient.unit)

            val totalServings = ingredient.totalServings
            val costPerServing = ingredient.costPerServing
            val mlPerServing = ingredient.mlPerServing
            if (totalServings != null && costPerServing != null && mlPerServing != null) {
                // ml ingredient with per-serving config: show servings count
                // and switch the cost cell to PHP/serving so staff sees
                // recipe economics directly.
                tvServings.visibility = View.VISIBLE
                tvServings.text = String.format(
                    Locale.getDefault(),
                    "≈ %d servings · %s ml each",
                    totalServings.toInt(),
                    formatTrim(mlPerServing)
                )
                tvCostPerUnit.text = String.format(
                    Locale.getDefault(),
                    "PHP %.2f/serving",
                    costPerServing
                )
            } else {
                tvServings.visibility = View.GONE
                tvCostPerUnit.text = String.format(
                    Locale.getDefault(),
                    "PHP %.2f/%s",
                    ingredient.costPerUnit,
                    ingredient.unit
                )
            }

            val totalVal = ingredient.currentStock * ingredient.costPerUnit
            tvTotalValue.text = String.format(Locale.getDefault(), "PHP %,.2f", totalVal)

            // Stock level bar
            val ratio = if (ingredient.minimumStock > 0)
                (ingredient.currentStock / (ingredient.minimumStock * 3.0)).coerceIn(0.0, 1.0)
            else 1.0

            stockBarFill.post {
                val trackWidth = stockBarTrack.width
                val params = stockBarFill.layoutParams as FrameLayout.LayoutParams
                params.width = (trackWidth * ratio).toInt()
                stockBarFill.layoutParams = params
            }

            // Color based on stock level
            val ctx = itemView.context
            val stockColor = when {
                ingredient.currentStock <= ingredient.minimumStock ->
                    ContextCompat.getColor(ctx, R.color.stock_critical)
                ingredient.currentStock <= ingredient.minimumStock * 1.5 ->
                    ContextCompat.getColor(ctx, R.color.stock_warning)
                else ->
                    ContextCompat.getColor(ctx, R.color.stock_good)
            }
            stockBarFill.backgroundTintList = android.content.res.ColorStateList.valueOf(stockColor)
            tvStock.setTextColor(stockColor)

            // Actions
            btnEdit.setOnClickListener { onEditClick(ingredient) }
            btnRestock.setOnClickListener { onRestockClick(ingredient) }
        }

        private fun formatTrim(value: Double): String {
            return if (value % 1.0 == 0.0) {
                value.toInt().toString()
            } else {
                String.format(Locale.getDefault(), "%.1f", value)
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
