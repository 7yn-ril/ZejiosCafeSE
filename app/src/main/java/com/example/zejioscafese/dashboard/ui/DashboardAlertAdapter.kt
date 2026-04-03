package com.example.zejioscafese.dashboard.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.zejioscafese.R
import com.example.zejioscafese.databinding.ItemDashboardAlertBinding
import com.example.zejioscafese.dashboard.model.AlertLevel
import com.example.zejioscafese.dashboard.model.DashboardAlert

class DashboardAlertAdapter :
    ListAdapter<DashboardAlert, DashboardAlertAdapter.AlertViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val binding = ItemDashboardAlertBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AlertViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AlertViewHolder(
        private val binding: ItemDashboardAlertBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DashboardAlert) {
            val context = binding.root.context
            val isCritical = item.level == AlertLevel.CRITICAL
            val accent = ContextCompat.getColor(
                context,
                if (isCritical) R.color.pos_badge else R.color.pos_warning
            )
            val surface = ContextCompat.getColor(
                context,
                if (isCritical) R.color.pos_critical_soft else R.color.pos_warning_soft
            )

            binding.root.setCardBackgroundColor(surface)
            binding.root.strokeColor = accent
            binding.ivAlertIcon.imageTintList = ColorStateList.valueOf(accent)
            binding.tvAlertTitle.text = item.title
            binding.tvAlertDetail.text = item.detail
            binding.tvAlertLevel.text = context.getString(
                if (isCritical) R.string.alert_level_critical else R.string.alert_level_warning
            )
            binding.tvAlertLevel.setTextColor(accent)
            ViewCompat.setBackgroundTintList(binding.tvAlertLevel, ColorStateList.valueOf(ContextCompat.getColor(context, R.color.white)))
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<DashboardAlert>() {
        override fun areItemsTheSame(oldItem: DashboardAlert, newItem: DashboardAlert): Boolean {
            return oldItem.title == newItem.title
        }

        override fun areContentsTheSame(oldItem: DashboardAlert, newItem: DashboardAlert): Boolean {
            return oldItem == newItem
        }
    }
}
