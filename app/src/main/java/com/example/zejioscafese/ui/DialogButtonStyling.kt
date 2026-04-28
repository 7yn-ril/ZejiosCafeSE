package com.example.zejioscafese.ui

import android.content.Context
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AlertDialog.Builder
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.example.zejioscafese.R

fun AlertDialog.applyZejiosCafeButtonStyling(context: Context) {
    styleButton(getButton(AlertDialog.BUTTON_POSITIVE), context, primary = true)
    styleButton(getButton(AlertDialog.BUTTON_NEGATIVE), context, primary = false)
    styleButton(getButton(AlertDialog.BUTTON_NEUTRAL), context, primary = false)
}

fun Builder.showStyledDialog(context: Context, onShown: (AlertDialog) -> Unit = {}): AlertDialog {
    val dialog = create()
    dialog.setOnShowListener {
        dialog.applyZejiosCafeButtonStyling(context)
        onShown(dialog)
    }
    dialog.show()
    return dialog
}

private fun styleButton(button: Button?, context: Context, primary: Boolean) {
    if (button == null) return

    button.isAllCaps = false
    button.minimumHeight = context.resources.getDimensionPixelSize(R.dimen.dialog_button_min_height)
    button.minimumWidth = context.resources.getDimensionPixelSize(R.dimen.dialog_button_min_width)
    button.setTextColor(
        ContextCompat.getColor(
            context,
            if (primary) R.color.white else R.color.pos_primary
        )
    )
    button.setPadding(
        context.resources.getDimensionPixelSize(R.dimen.dialog_button_padding_horizontal),
        button.paddingTop,
        context.resources.getDimensionPixelSize(R.dimen.dialog_button_padding_horizontal),
        button.paddingBottom
    )
    button.backgroundTintList = null
    button.background = ContextCompat.getDrawable(
        context,
        if (primary) R.drawable.bg_dialog_btn_primary else R.drawable.bg_dialog_btn_outlined
    )
    ViewCompat.setElevation(button, 0f)
}