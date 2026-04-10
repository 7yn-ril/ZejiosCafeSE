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
            val current = item.imageUrl.trim()
            if (current.isNotEmpty()) return current

            val name = item.name.lowercase()
            return when {
                "latte" in name || "cappuccino" in name || "espresso" in name || "coffee" in name ->
                    "https://images.unsplash.com/photo-1509042239860-f550ce710b93?auto=format&fit=crop&w=800&q=80"
                "croissant" in name || "pastry" in name || "muffin" in name || "bread" in name ->
                    "https://images.unsplash.com/photo-1509440159596-0249088772ff?auto=format&fit=crop&w=800&q=80"
                "burger" in name || "sandwich" in name || "panini" in name ->
                    "https://images.unsplash.com/photo-1550317138-10000687a72b?auto=format&fit=crop&w=800&q=80"
                else ->
                    "https://images.unsplash.com/photo-1517701604599-bb29b565090c?auto=format&fit=crop&w=800&q=80"
            }
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
