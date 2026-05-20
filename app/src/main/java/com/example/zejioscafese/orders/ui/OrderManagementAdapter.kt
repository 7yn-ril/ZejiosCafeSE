package com.example.zejioscafese.orders.ui

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
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

class OrderManagementAdapter(
    private val onOrderItemsClick: (CafeOrder) -> Unit = {},
    private val onOrderActionClick: (CafeOrder, View) -> Unit = { _, _ -> }
) :
    ListAdapter<CafeOrder, OrderManagementAdapter.OrderViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = ItemManagementOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return OrderViewHolder(binding, onOrderItemsClick, onOrderActionClick)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class OrderViewHolder(
        private val binding: ItemManagementOrderBinding,
        private val onOrderItemsClick: (CafeOrder) -> Unit,
        private val onOrderActionClick: (CafeOrder, View) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CafeOrder) {
            val context = binding.root.context
            val (chipColor, chipSurface, label) = when (item.status) {
                CafeOrderStatus.PENDING -> Triple(R.color.pos_warning, R.color.pos_warning_soft, R.string.pending)
                CafeOrderStatus.PREPARING -> Triple(R.color.pos_info, R.color.pos_info_soft, R.string.preparing)
                CafeOrderStatus.COMPLETED -> Triple(R.color.pos_secondary, R.color.pos_success_soft, R.string.completed)
                CafeOrderStatus.CANCELLED -> Triple(R.color.pos_badge, R.color.pos_critical_soft, R.string.cancelled)
            }

            binding.tvOrderId.text = item.id
            binding.tvOrderCustomerName.text = item.customerName
            binding.tvOrderTime.text = item.timeLabel
            binding.tvOrderStatus.text = context.getString(label)
            binding.tvOrderTotal.text = context.getString(R.string.currency_format, item.total)
            binding.tvOrderInitials.text = item.initials

            val accent = ContextCompat.getColor(context, chipColor)
            val defaultTextColor = ContextCompat.getColor(context, R.color.pos_text_primary)
            val actionTextColor = ContextCompat.getColor(context, R.color.pos_secondary)
            ViewCompat.setBackgroundTintList(
                binding.tvOrderStatus,
                ColorStateList.valueOf(ContextCompat.getColor(context, chipSurface))
            )
            binding.tvOrderStatus.setTextColor(accent)

            val hasMultipleItems = item.orderedItems.size > 1
            val primaryItemLabel = item.orderedItems.firstOrNull().orEmpty().ifBlank { item.itemsSummary }

            binding.tvOrderItems.text = if (hasMultipleItems) {
                context.getString(
                    R.string.order_items_preview_more,
                    primaryItemLabel,
                    item.orderedItems.size - 1
                )
            } else {
                primaryItemLabel
            }
            val isPreparing = item.status == CafeOrderStatus.PREPARING
            binding.tvOrderItemCount.text = if (hasMultipleItems || isPreparing) {
                context.getString(R.string.order_items_tap_to_view, item.itemCount)
            } else {
                context.getString(R.string.items_count_format, item.itemCount)
            }
            // Tap opens the per-item completion dialog. We surface it for any
            // multi-item order (so the items are inspectable) and for any
            // PREPARING order (so single-item PREPARING tickets can be
            // checked off the same way as multi-item ones).
            val tappable = item.orderedItems.isNotEmpty() && (hasMultipleItems || isPreparing)
            val highlightAsAction = hasMultipleItems || isPreparing

            val orderItemClickListener = if (tappable) {
                View.OnClickListener { onOrderItemsClick(item) }
            } else {
                null
            }

            binding.tvOrderItems.setOnClickListener(orderItemClickListener)
            binding.tvOrderItemCount.setOnClickListener(orderItemClickListener)
            binding.tvOrderItems.isClickable = tappable
            binding.tvOrderItemCount.isClickable = tappable
            binding.tvOrderItems.isFocusable = tappable
            binding.tvOrderItemCount.isFocusable = tappable
            binding.tvOrderItems.setTextColor(if (highlightAsAction) actionTextColor else defaultTextColor)
            binding.tvOrderItems.setTypeface(null, if (highlightAsAction) Typeface.BOLD else Typeface.NORMAL)

            // CHANGE: Orders — show the Take Out pill on takeout orders.
            // Visibility flips with the orderType field on CafeOrder.
            binding.tvOrderTakeoutBadge.visibility = if (item.isTakeout) View.VISIBLE else View.GONE

            binding.btnOrderActions.setOnClickListener { anchor ->
                onOrderActionClick(item, anchor)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<CafeOrder>() {
        override fun areItemsTheSame(oldItem: CafeOrder, newItem: CafeOrder): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: CafeOrder, newItem: CafeOrder): Boolean = oldItem == newItem
    }
}
