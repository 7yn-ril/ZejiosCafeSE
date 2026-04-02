package com.example.zejioscafese.pos.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.zejioscafese.R
import com.example.zejioscafese.databinding.ItemProductBinding
import com.example.zejioscafese.pos.data.model.Product

class ProductAdapter(
    private val onCardClick: (Product) -> Unit
) : ListAdapter<Product, ProductAdapter.ProductViewHolder>(DiffCallback) {

    private val quantities = linkedMapOf<String, Int>()

    fun submitQuantities(updatedQuantities: Map<String, Int>) {
        quantities.clear()
        quantities.putAll(updatedQuantities)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProductViewHolder(binding, onCardClick)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = getItem(position)
        holder.bind(product, quantities[product.id] ?: 0)
    }

    class ProductViewHolder(
        private val binding: ItemProductBinding,
        private val onCardClick: (Product) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(product: Product, quantity: Int) {
            binding.tvProductCategory.text = product.category
            binding.tvProductName.text = product.name
            binding.tvProductPrice.text = binding.root.context.getString(
                R.string.currency_format,
                product.price
            )
            binding.tvStockLeft.text = binding.root.context.getString(
                R.string.stock_left_format,
                product.stockLeft
            )
            binding.tvQuantityBadge.isVisible = quantity > 0
            binding.tvQuantityBadge.text = binding.root.context.getString(
                R.string.items_selected_format,
                quantity
            )

            RemoteImageLoader.load(binding.ivProduct, product.imageUrl, product.imageResId)

            binding.productCard.setOnClickListener { onCardClick(product) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Product>() {
        override fun areItemsTheSame(oldItem: Product, newItem: Product): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Product, newItem: Product): Boolean {
            return oldItem == newItem
        }
    }
}
