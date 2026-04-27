package com.example.zejioscafese.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.zejioscafese.R
import com.example.zejioscafese.inventory.data.model.ProducibleProduct

class ProducibleProductAdapter(
    private val onViewIngredientsClick: (ProducibleProduct) -> Unit,
    private val onEditClick: (ProducibleProduct) -> Unit,
    private val onDeleteClick: (ProducibleProduct) -> Unit
) :
    ListAdapter<ProducibleProduct, ProducibleProductAdapter.ProducibleProductViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProducibleProductViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_producible_product, parent, false)
        return ProducibleProductViewHolder(
            itemView = view,
            onViewIngredientsClick = onViewIngredientsClick,
            onEditClick = onEditClick,
            onDeleteClick = onDeleteClick
        )
    }

    override fun onBindViewHolder(holder: ProducibleProductViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ProducibleProductViewHolder(
        itemView: View,
        private val onViewIngredientsClick: (ProducibleProduct) -> Unit,
        private val onEditClick: (ProducibleProduct) -> Unit,
        private val onDeleteClick: (ProducibleProduct) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvCategory: TextView = itemView.findViewById(R.id.tvProducibleCategory)
        private val tvName: TextView = itemView.findViewById(R.id.tvProducibleName)
        private val tvVariant: TextView = itemView.findViewById(R.id.tvProducibleVariant)
        private val tvPrice: TextView = itemView.findViewById(R.id.tvProduciblePrice)
        private val tvEstimatedValue: TextView = itemView.findViewById(R.id.tvProducibleValue)
        private val tvQuantity: TextView = itemView.findViewById(R.id.tvProducibleQuantity)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvProducibleStatus)
        private val btnViewIngredients: View =
            itemView.findViewById(R.id.btnViewProductIngredients)
        private val btnEdit: View = itemView.findViewById(R.id.btnEditProduct)
        private val btnDelete: View = itemView.findViewById(R.id.btnDeleteProduct)

        fun bind(product: ProducibleProduct) {
            val context = itemView.context
            tvCategory.text = product.category
            tvName.text = product.name
            tvVariant.text = if (
                product.variantName.equals("standard", ignoreCase = true) ||
                product.variantName.equals("combo", ignoreCase = true)
            ) {
                context.getString(R.string.inventory_standard_variant)
            } else {
                product.variantName
            }
            tvPrice.text = context.getString(
                R.string.inventory_producible_price,
                context.getString(R.string.currency_format, product.price)
            )
            tvEstimatedValue.text = context.getString(
                R.string.inventory_estimated_value,
                context.getString(R.string.currency_format, product.estimatedValue)
            )
            tvQuantity.text = context.getString(
                R.string.inventory_can_make_format,
                product.availableQuantity
            )

            val isOutOfStock = product.availableQuantity <= 0
            tvStatus.text = if (isOutOfStock) {
                context.getString(R.string.inventory_out_of_stock)
            } else {
                context.getString(R.string.inventory_can_make_format, product.availableQuantity)
            }

            val statusColor = ContextCompat.getColor(
                context,
                if (isOutOfStock) R.color.stock_critical else R.color.stock_good
            )
            tvStatus.setTextColor(statusColor)
            tvQuantity.setTextColor(statusColor)

            btnViewIngredients.setOnClickListener { onViewIngredientsClick(product) }
            btnEdit.setOnClickListener { onEditClick(product) }
            btnDelete.setOnClickListener { onDeleteClick(product) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ProducibleProduct>() {
        override fun areItemsTheSame(oldItem: ProducibleProduct, newItem: ProducibleProduct): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ProducibleProduct, newItem: ProducibleProduct): Boolean {
            return oldItem == newItem
        }
    }
}
