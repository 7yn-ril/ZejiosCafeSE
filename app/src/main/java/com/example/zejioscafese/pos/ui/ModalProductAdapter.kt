package com.example.zejioscafese.pos.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.zejioscafese.R
import com.example.zejioscafese.databinding.ItemProductModalBinding
import com.example.zejioscafese.pos.data.model.Product

/**
 * Lightweight adapter for the fullscreen menu browse modal.
 * Inflates the larger [item_product_modal] layout and delegates the
 * click to the same lambda used by the inline [ProductAdapter].
 *
 * This class intentionally duplicates a small amount of bind logic
 * instead of modifying [ProductAdapter], respecting the constraint
 * that existing adapter code must not be changed.
 */
class ModalProductAdapter(
    private val onCardClick: (Product) -> Unit
) : ListAdapter<Product, ModalProductAdapter.ModalProductViewHolder>(DiffCallback) {

    private val quantities = linkedMapOf<String, Int>()

    fun submitQuantities(updatedQuantities: Map<String, Int>) {
        quantities.clear()
        quantities.putAll(updatedQuantities)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModalProductViewHolder {
        val binding = ItemProductModalBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ModalProductViewHolder(binding, onCardClick)
    }

    override fun onBindViewHolder(holder: ModalProductViewHolder, position: Int) {
        val product = getItem(position)
        holder.bind(product, quantities[product.id] ?: 0)
    }

    class ModalProductViewHolder(
        private val binding: ItemProductModalBinding,
        private val onCardClick: (Product) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(product: Product, quantity: Int) {
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
            binding.btnAddToOrder.setOnClickListener { onCardClick(product) }
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
