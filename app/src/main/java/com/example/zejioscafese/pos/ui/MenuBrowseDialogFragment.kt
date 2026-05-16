package com.example.zejioscafese.pos.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.example.zejioscafese.R
import com.example.zejioscafese.databinding.DialogMenuBrowseBinding
import com.example.zejioscafese.pos.presentation.PosViewModel

/**
 * Centered category-picker modal.
 *
 * Shows every menu category as a tile in a non-scrollable 3-column grid.
 * Tapping a card selects that category in the shared [PosViewModel] and
 * dismisses the dialog so the POS inline grid filters to that category.
 */
class MenuBrowseDialogFragment : DialogFragment() {

    private val viewModel: PosViewModel by activityViewModels()

    private var _binding: DialogMenuBrowseBinding? = null
    private val binding: DialogMenuBrowseBinding
        get() = requireNotNull(_binding) { "Menu browse binding is only valid between onCreateView and onDestroyView." }

    private lateinit var pickerAdapter: CategoryPickerAdapter

    /** Category → representative product image URL. Set by the host. */
    private var categoryThumbnails: Map<String, String?> = emptyMap()

    fun setThumbnails(thumbnails: Map<String, String?>) {
        categoryThumbnails = thumbnails
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, R.style.Theme_ZejiosCafeSE)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            setCanceledOnTouchOutside(true)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogMenuBrowseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnCloseModal.setOnClickListener { dismiss() }

        pickerAdapter = CategoryPickerAdapter { category ->
            viewModel.selectCategory(category)
            dismiss()
        }

        binding.rvModalCategories.apply {
            adapter = pickerAdapter
            layoutManager = GridLayoutManager(requireContext(), CATEGORY_COLUMNS)
            itemAnimator = null
            isNestedScrollingEnabled = false
            setHasFixedSize(true)
        }

        pickerAdapter.submitThumbnails(categoryThumbnails)

        viewModel.categories.observe(viewLifecycleOwner) { categories ->
            pickerAdapter.submitList(categories)
        }

        viewModel.selectedCategory.observe(viewLifecycleOwner) { selected ->
            pickerAdapter.selectedCategory = selected
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            // Full-window scrim; the inner FrameLayout centers the card.
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            setBackgroundDrawableResource(android.R.color.transparent)
            // Strong black scrim so the modal pops from the busy POS surface.
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.75f)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "MenuBrowseDialog"
        private const val CATEGORY_COLUMNS = 6
    }
}
