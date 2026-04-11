package com.example.zejioscafese.pos.ui

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.ViewGroup.MarginLayoutParams
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

    var compactMode: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

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
        holder.bind(
            category = getItem(position),
            isSelected = getItem(position) == selectedCategory,
            compactMode = compactMode
        )
    }

    class CategoryViewHolder(
        private val binding: ItemCategoryTabBinding,
        private val onCategoryClick: (String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(category: String, isSelected: Boolean, compactMode: Boolean) {
            binding.btnCategory.setIconResource(categoryIcon(category))
            binding.btnCategory.isSelected = isSelected
            binding.btnCategory.text = if (compactMode) "" else category
            binding.btnCategory.contentDescription = category
            updateButtonLayout(compactMode)
            binding.btnCategory.setOnClickListener { onCategoryClick(category) }
        }

        private fun updateButtonLayout(compactMode: Boolean) {
            val targetPaddingStart = dpToPx(if (compactMode) 10 else 14)
            val targetPaddingEnd = dpToPx(if (compactMode) 10 else 16)
            val targetIconPadding = dpToPx(if (compactMode) 0 else 8)
            val targetMarginEnd = dpToPx(if (compactMode) 6 else 10)

            val button = binding.btnCategory
            val params = button.layoutParams as MarginLayoutParams

            val startPaddingStart = button.paddingStart
            val startPaddingEnd = button.paddingEnd
            val startIconPadding = button.iconPadding
            val startMarginEnd = params.marginEnd

            if (
                startPaddingStart == targetPaddingStart &&
                startPaddingEnd == targetPaddingEnd &&
                startIconPadding == targetIconPadding &&
                startMarginEnd == targetMarginEnd
            ) {
                return
            }

            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 180L
                addUpdateListener { animator ->
                    val fraction = animator.animatedValue as Float
                    val currentPaddingStart = lerp(startPaddingStart, targetPaddingStart, fraction)
                    val currentPaddingEnd = lerp(startPaddingEnd, targetPaddingEnd, fraction)
                    val currentIconPadding = lerp(startIconPadding, targetIconPadding, fraction)
                    val currentMarginEnd = lerp(startMarginEnd, targetMarginEnd, fraction)

                    button.setPaddingRelative(
                        currentPaddingStart,
                        button.paddingTop,
                        currentPaddingEnd,
                        button.paddingBottom
                    )
                    button.iconPadding = currentIconPadding

                    val updatedParams = button.layoutParams as MarginLayoutParams
                    updatedParams.marginEnd = currentMarginEnd
                    button.layoutParams = updatedParams
                }
            }.start()
        }

        private fun lerp(start: Int, end: Int, fraction: Float): Int {
            return (start + ((end - start) * fraction)).toInt()
        }

        private fun dpToPx(dp: Int): Int {
            return (dp * binding.root.resources.displayMetrics.density).toInt()
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
