package com.example.zejioscafese.pos.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.zejioscafese.R
import com.example.zejioscafese.databinding.ItemCategoryPickerBinding

/**
 * Adapter for the category-picker dialog.
 *
 * Each item is a card showing a representative product thumbnail for
 * the category alongside the category name.  When a card is tapped
 * the [onCategoryClick] callback fires and the dialog dismisses.
 */
class CategoryPickerAdapter(
    private val onCategoryClick: (String) -> Unit
) : ListAdapter<String, CategoryPickerAdapter.PickerViewHolder>(DiffCallback) {

    /** Category name → first product image URL discovered for that category. */
    private val thumbnails = mutableMapOf<String, String?>()

    /** Currently selected category — used to highlight the active card. */
    var selectedCategory: String? = null
        set(value) {
            val old = field
            field = value
            if (old != value) notifyDataSetChanged()
        }

    /**
     * Supply a mapping of category → representative image URL.
     * Call this once before or after [submitList].
     */
    fun submitThumbnails(map: Map<String, String?>) {
        thumbnails.clear()
        thumbnails.putAll(map)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PickerViewHolder {
        val binding = ItemCategoryPickerBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PickerViewHolder(binding, onCategoryClick)
    }

    override fun onBindViewHolder(holder: PickerViewHolder, position: Int) {
        val category = getItem(position)
        holder.bind(category, thumbnails[category], category == selectedCategory)
    }

    class PickerViewHolder(
        private val binding: ItemCategoryPickerBinding,
        private val onCategoryClick: (String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(category: String, imageUrl: String?, isSelected: Boolean) {
            binding.tvCategoryName.text = category

            // Always show the category icon (never a remote photo) so every card
            // looks consistent.
            val icon = when {
                category.contains("Coffee", ignoreCase = true) -> R.drawable.ic_coffee_24
                category.contains("Meal", ignoreCase = true) ||
                    category.contains("Burger", ignoreCase = true) -> R.drawable.ic_meal_24
                category.contains("Dessert", ignoreCase = true) -> R.drawable.ic_dessert_24
                category.contains("Snack", ignoreCase = true) -> R.drawable.ic_snack_24
                else -> R.drawable.ic_coffee_24
            }
            binding.ivCategoryThumb.setImageResource(icon)

            // Highlight the currently selected category.
            binding.selectedOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
            val strokeColor = if (isSelected) R.color.pos_primary else R.color.pos_border
            binding.categoryPickerCard.strokeColor =
                ContextCompat.getColor(binding.root.context, strokeColor)
            binding.categoryPickerCard.strokeWidth = if (isSelected) {
                binding.root.resources.getDimensionPixelSize(R.dimen.payment_button_stroke_width) * 2
            } else {
                binding.root.resources.getDimensionPixelSize(R.dimen.payment_button_stroke_width)
            }

            binding.categoryPickerCard.setOnClickListener { onCategoryClick(category) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String) = oldItem == newItem
        override fun areContentsTheSame(oldItem: String, newItem: String) = oldItem == newItem
    }
}
