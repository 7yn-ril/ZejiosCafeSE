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

class ProductAdapter(
    private val onCardClick: (Product) -> Unit,
    private val onIncrease: (Product) -> Unit = onCardClick,
    private val onDecrease: (Product) -> Unit = {}
) : ListAdapter<Product, ProductAdapter.ProductViewHolder>(DiffCallback) {

    private val quantities = linkedMapOf<String, Int>()

    fun submitQuantities(updatedQuantities: Map<String, Int>) {
        quantities.clear()
        quantities.putAll(updatedQuantities)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProductViewHolder(binding, onCardClick, onIncrease, onDecrease)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = getItem(position)
        holder.bind(product, quantities[product.id] ?: 0)
    }

    class ProductViewHolder(
        private val binding: ItemProductBinding,
        private val onCardClick: (Product) -> Unit,
        private val onIncrease: (Product) -> Unit,
        private val onDecrease: (Product) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(product: Product, quantity: Int) {
            val context = binding.root.context
            binding.tvProductCategory.text = product.category
            binding.tvProductName.text = product.name
            binding.tvProductPrice.text = context.getString(
                R.string.currency_format,
                product.price
            )
            binding.tvStockLeft.text = context.getString(
                R.string.stock_left_format,
                product.stockLeft
            )

            val inCart = quantity > 0
            binding.btnAddToDish.visibility = if (inCart) View.GONE else View.VISIBLE
            binding.productStepper.visibility = if (inCart) View.VISIBLE else View.GONE
            binding.tvProductQuantity.text = quantity.toString()

            val selectedStroke = ContextCompat.getColor(context, R.color.pos_secondary)
            val defaultStroke = ContextCompat.getColor(context, R.color.pos_border)
            binding.productCard.strokeColor = if (inCart) selectedStroke else defaultStroke
            binding.productCard.strokeWidth = if (inCart) dp(context, 2) else dp(context, 1)

            RemoteImageLoader.load(binding.ivProduct, product.imageUrl, product.imageResId)

            binding.productCard.setOnClickListener { onCardClick(product) }
            binding.btnAddToDish.setOnClickListener { onCardClick(product) }
            binding.btnProductIncrease.setOnClickListener { onIncrease(product) }
            binding.btnProductDecrease.setOnClickListener { onDecrease(product) }
        }

        private fun dp(context: android.content.Context, value: Int): Int {
            return (value * context.resources.displayMetrics.density).toInt()
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
