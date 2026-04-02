package com.example.zejioscafese.pos.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.zejioscafese.R
import com.example.zejioscafese.databinding.ItemCategoryTabBinding

class CategoryAdapter(
    private val onCategoryClick: (String) -> Unit
) : ListAdapter<String, CategoryAdapter.CategoryViewHolder>(DiffCallback) {

    var selectedCategory: String = ""
        set(value) {
            if (field == value) return

            val previous = field
            field = value

            val oldIndex = currentList.indexOf(previous)
            if (oldIndex >= 0) notifyItemChanged(oldIndex)

            val newIndex = currentList.indexOf(value)
            if (newIndex >= 0) notifyItemChanged(newIndex)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemCategoryTabBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CategoryViewHolder(binding, onCategoryClick)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(getItem(position), getItem(position) == selectedCategory)
    }

    class CategoryViewHolder(
        private val binding: ItemCategoryTabBinding,
        private val onCategoryClick: (String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(category: String, isSelected: Boolean) {
            binding.btnCategory.text = category
            binding.btnCategory.setIconResource(categoryIcon(category))
            binding.btnCategory.isSelected = isSelected
            binding.btnCategory.setOnClickListener { onCategoryClick(category) }
        }

        @DrawableRes
        private fun categoryIcon(category: String): Int {
            return when (category) {
                "All" -> R.drawable.ic_grid_24
                "Drinks" -> R.drawable.ic_coffee_24
                "Meals" -> R.drawable.ic_meal_24
                "Desserts" -> R.drawable.ic_dessert_24
                "Snacks" -> R.drawable.ic_snack_24
                "Specials" -> R.drawable.ic_star_24
                else -> R.drawable.ic_coffee_24
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem

        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
    }
}
