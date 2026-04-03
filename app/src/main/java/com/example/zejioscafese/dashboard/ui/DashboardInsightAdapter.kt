package com.example.zejioscafese.dashboard.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.zejioscafese.databinding.ItemDashboardInsightBinding
import com.example.zejioscafese.dashboard.model.DashboardInsight

class DashboardInsightAdapter :
    ListAdapter<DashboardInsight, DashboardInsightAdapter.InsightViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InsightViewHolder {
        val binding = ItemDashboardInsightBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return InsightViewHolder(binding)
    }

    override fun onBindViewHolder(holder: InsightViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class InsightViewHolder(
        private val binding: ItemDashboardInsightBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DashboardInsight) {
            binding.tvInsightTitle.text = item.title
            binding.tvInsightValue.text = item.value
            binding.tvInsightSupporting.text = item.supportingText
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<DashboardInsight>() {
        override fun areItemsTheSame(oldItem: DashboardInsight, newItem: DashboardInsight): Boolean {
            return oldItem.title == newItem.title
        }

        override fun areContentsTheSame(oldItem: DashboardInsight, newItem: DashboardInsight): Boolean {
            return oldItem == newItem
        }
    }
}
