package com.example.zejioscafese.dashboard.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.zejioscafese.R
import com.example.zejioscafese.databinding.ItemDashboardTopProductBinding
import com.example.zejioscafese.dashboard.model.DashboardTopItem
import com.example.zejioscafese.pos.ui.RemoteImageLoader

class DashboardTopItemAdapter :
    ListAdapter<DashboardTopItem, DashboardTopItemAdapter.TopItemViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TopItemViewHolder {
        val binding = ItemDashboardTopProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TopItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TopItemViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class TopItemViewHolder(
        private val binding: ItemDashboardTopProductBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DashboardTopItem) {
            RemoteImageLoader.load(binding.ivTopProduct, resolveImageUrl(item), R.drawable.ic_coffee_24)
            binding.tvTopProductName.text = item.name
            binding.tvTopProductOrders.text = binding.root.context.getString(
                R.string.orders_count_format,
                item.orders
            )
            binding.tvTopProductRevenue.text = binding.root.context.getString(
                R.string.revenue_short_format,
                item.revenue
            )
        }

        private fun resolveImageUrl(item: DashboardTopItem): String {
            return item.imageUrl.trim()
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<DashboardTopItem>() {
        override fun areItemsTheSame(oldItem: DashboardTopItem, newItem: DashboardTopItem): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: DashboardTopItem, newItem: DashboardTopItem): Boolean {
            return oldItem == newItem
        }
    }
}
