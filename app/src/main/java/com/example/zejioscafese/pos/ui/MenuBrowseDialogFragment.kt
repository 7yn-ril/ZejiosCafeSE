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
 * Fullscreen category-picker dialog.
 *
 * Shows every menu category as a card with a representative product
 * thumbnail in a 2-column grid.  Tapping a card selects that category
 * in the shared [PosViewModel] and immediately dismisses the dialog,
 * so the POS inline grid filters to that category.
 */
class MenuBrowseDialogFragment : DialogFragment() {

    private val viewModel: PosViewModel by activityViewModels()

    private var _binding: DialogMenuBrowseBinding? = null
    private val binding get() = _binding!!

    private lateinit var pickerAdapter: CategoryPickerAdapter

    /**
     * Category → representative product image URL.
     * Set by the host before showing the dialog.
     */
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

        binding.tvModalTitle.text = getString(R.string.filter_sort)
        binding.tvModalSubtitle.text = getString(R.string.search_hint)
        binding.btnCloseModal.setOnClickListener { dismiss() }

        pickerAdapter = CategoryPickerAdapter { category ->
            viewModel.selectCategory(category)
            dismiss()
        }

        binding.rvModalCategories.apply {
            adapter = pickerAdapter
            layoutManager = GridLayoutManager(requireContext(), 3)
            itemAnimator = null
        }

        // Push thumbnails to the adapter.
        pickerAdapter.submitThumbnails(categoryThumbnails)

        // Observe categories and selected state.
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
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            setBackgroundDrawableResource(android.R.color.transparent)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "MenuBrowseDialog"
    }
}
