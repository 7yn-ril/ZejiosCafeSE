package com.example.zejioscafese.pos.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.zejioscafese.R
import com.example.zejioscafese.databinding.ItemProductBinding
import com.example.zejioscafese.pos.data.model.Product
import com.example.zejioscafese.pos.data.model.ProductGroup

class ProductAdapter(
    private val onGroupClick: (ProductGroup) -> Unit,
    private val onIncrease: (Product) -> Unit,
    private val onDecrease: (Product) -> Unit = {}
) : ListAdapter<ProductGroup, ProductAdapter.ProductViewHolder>(DiffCallback) {

    private val variantQuantities = linkedMapOf<String, Int>()

    fun submitQuantities(updatedQuantities: Map<String, Int>) {
        variantQuantities.clear()
        variantQuantities.putAll(updatedQuantities)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProductViewHolder(binding, onGroupClick, onIncrease, onDecrease)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val group = getItem(position)
        val groupQuantity = group.variants.sumOf { variantQuantities[it.id] ?: 0 }
        holder.bind(group, groupQuantity)
    }

    class ProductViewHolder(
        private val binding: ItemProductBinding,
        private val onGroupClick: (ProductGroup) -> Unit,
        private val onIncrease: (Product) -> Unit,
        private val onDecrease: (Product) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(group: ProductGroup, totalQuantity: Int) {
            val context = binding.root.context
            binding.tvProductCategory.text = group.category
            binding.tvProductName.text = group.displayName
            binding.tvProductPrice.text = formatPrice(context, group)
            binding.tvStockLeft.text = context.getString(
                R.string.stock_left_format,
                group.totalStock
            )

            val inCart = totalQuantity > 0
            val isSingleVariant = !group.hasMultipleVariants
            val singleVariant = group.singleVariant

            // Multi-variant cards never show the inline stepper — they always open the picker.
            val showStepper = isSingleVariant && inCart
            val showAddButton = !showStepper

            binding.btnAddToDish.visibility = if (showAddButton) View.VISIBLE else View.GONE
            binding.productStepper.visibility = if (showStepper) View.VISIBLE else View.GONE
            binding.tvProductQuantity.text = totalQuantity.toString()

            binding.btnAddToDish.text = if (group.hasMultipleVariants) {
                context.getString(R.string.choose_size)
            } else {
                context.getString(R.string.add_to_cart)
            }

            // Multi-variant in-cart badge (top-right of price row)
            if (group.hasMultipleVariants && inCart) {
                binding.tvQuantityBadge.visibility = View.VISIBLE
                binding.tvQuantityBadge.text = context.getString(R.string.in_cart_count, totalQuantity)
            } else {
                binding.tvQuantityBadge.visibility = View.GONE
            }

            val selectedStroke = ContextCompat.getColor(context, R.color.pos_secondary)
            val defaultStroke = ContextCompat.getColor(context, R.color.pos_border)
            binding.productCard.strokeColor = if (inCart) selectedStroke else defaultStroke
            binding.productCard.strokeWidth = if (inCart) dp(context, 2) else dp(context, 1)

            RemoteImageLoader.load(binding.ivProduct, group.imageUrl, group.imageResId)

            binding.productCard.setOnClickListener { onGroupClick(group) }
            binding.btnAddToDish.setOnClickListener { onGroupClick(group) }

            if (isSingleVariant && singleVariant != null) {
                binding.btnProductIncrease.setOnClickListener { onIncrease(singleVariant) }
                binding.btnProductDecrease.setOnClickListener { onDecrease(singleVariant) }
            } else {
                binding.btnProductIncrease.setOnClickListener { onGroupClick(group) }
                binding.btnProductDecrease.setOnClickListener { onGroupClick(group) }
            }
        }

        private fun formatPrice(context: android.content.Context, group: ProductGroup): String {
            return if (group.hasMultipleVariants && group.minPrice != group.maxPrice) {
                context.getString(R.string.price_from_format, group.minPrice)
            } else {
                context.getString(R.string.currency_format, group.minPrice)
            }
        }

        private fun dp(context: android.content.Context, value: Int): Int {
            return (value * context.resources.displayMetrics.density).toInt()
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ProductGroup>() {
        override fun areItemsTheSame(oldItem: ProductGroup, newItem: ProductGroup): Boolean {
            return oldItem.groupId == newItem.groupId
        }

        override fun areContentsTheSame(oldItem: ProductGroup, newItem: ProductGroup): Boolean {
            return oldItem == newItem
        }
    }
}
