package com.example.zejioscafese.pos.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.zejioscafese.R
import com.example.zejioscafese.databinding.ItemOrderBinding
import com.example.zejioscafese.pos.data.model.OrderItem

class OrderItemAdapter(
    private val onIncreaseClick: (OrderItem) -> Unit,
    private val onDecreaseClick: (OrderItem) -> Unit
) : ListAdapter<OrderItem, OrderItemAdapter.OrderItemViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderItemViewHolder {
        val binding = ItemOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return OrderItemViewHolder(binding, onIncreaseClick, onDecreaseClick)
    }

    override fun onBindViewHolder(holder: OrderItemViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class OrderItemViewHolder(
        private val binding: ItemOrderBinding,
        private val onIncreaseClick: (OrderItem) -> Unit,
        private val onDecreaseClick: (OrderItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: OrderItem) {
            RemoteImageLoader.load(binding.ivOrderItem, item.product.imageUrl, item.product.imageResId)
            binding.tvOrderItemName.text = item.product.name
            binding.tvOrderItemQty.text = item.quantity.toString()
            binding.tvOrderItemUnitPrice.text = binding.root.context.getString(
                R.string.unit_price_format,
                item.product.price
            )
            binding.tvOrderItemPrice.text = binding.root.context.getString(
                R.string.currency_format,
                item.lineTotal
            )
            binding.btnOrderItemDecrease.contentDescription = binding.root.context.getString(
                R.string.decrease_quantity_for_format,
                item.product.name
            )
            binding.btnOrderItemIncrease.contentDescription = binding.root.context.getString(
                R.string.increase_quantity_for_format,
                item.product.name
            )
            binding.btnOrderItemIncrease.setOnClickListener { onIncreaseClick(item) }
            binding.btnOrderItemDecrease.setOnClickListener { onDecreaseClick(item) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<OrderItem>() {
        override fun areItemsTheSame(oldItem: OrderItem, newItem: OrderItem): Boolean {
            return oldItem.product.id == newItem.product.id
        }

        override fun areContentsTheSame(oldItem: OrderItem, newItem: OrderItem): Boolean {
            return oldItem == newItem
        }
    }
}
