package com.example.zejioscafese.orders.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.zejioscafese.R
import com.example.zejioscafese.databinding.ItemManagementOrderBinding
import com.example.zejioscafese.orders.model.CafeOrder
import com.example.zejioscafese.orders.model.CafeOrderStatus

class OrderManagementAdapter :
    ListAdapter<CafeOrder, OrderManagementAdapter.OrderViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = ItemManagementOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return OrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class OrderViewHolder(
        private val binding: ItemManagementOrderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CafeOrder) {
            val context = binding.root.context
            val (chipColor, chipSurface, label) = when (item.status) {
                CafeOrderStatus.PENDING -> Triple(R.color.pos_warning, R.color.pos_warning_soft, R.string.pending)
                CafeOrderStatus.PREPARING -> Triple(R.color.pos_info, R.color.pos_info_soft, R.string.preparing)
                CafeOrderStatus.COMPLETED -> Triple(R.color.pos_secondary, R.color.pos_success_soft, R.string.completed)
            }

            binding.tvOrderId.text = item.id
            binding.tvOrderCustomerName.text = item.customerName
            binding.tvOrderCustomerTable.text = context.getString(R.string.table_format, item.tableLabel)
            binding.tvOrderItems.text = item.itemsSummary
            binding.tvOrderItemCount.text = context.getString(R.string.items_count_format, item.itemCount)
            binding.tvOrderTime.text = item.timeLabel
            binding.tvOrderStatus.text = context.getString(label)
            binding.tvOrderTotal.text = context.getString(R.string.currency_format, item.total)
            binding.tvOrderInitials.text = item.initials

            val accent = ContextCompat.getColor(context, chipColor)
            ViewCompat.setBackgroundTintList(
                binding.tvOrderStatus,
                ColorStateList.valueOf(ContextCompat.getColor(context, chipSurface))
            )
            binding.tvOrderStatus.setTextColor(accent)
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<CafeOrder>() {
        override fun areItemsTheSame(oldItem: CafeOrder, newItem: CafeOrder): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: CafeOrder, newItem: CafeOrder): Boolean = oldItem == newItem
    }
}
