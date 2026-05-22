package com.example.zejioscafese.ui

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.WindowManager
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDialog
import androidx.core.content.ContextCompat
import com.example.zejioscafese.R
import com.example.zejioscafese.databinding.DialogNoticeBinding

/**
 * Shared modal for warning / informational / success notices. Replaces the
 * Snackbar pattern across the app so every alert surfaces as a centered,
 * branded modal with consistent styling.
 *
 * Use the [NoticeType] variants (Warning, Success, Error, Info) or call
 * [showNoticeDialog] directly to override the icon/color combo.
 */
enum class NoticeType(
    @DrawableRes val iconRes: Int,
    @ColorRes val iconTintRes: Int,
    @ColorRes val circleColorRes: Int
) {
    Warning(
        iconRes = R.drawable.ic_warning_24,
        iconTintRes = R.color.pos_warning,
        circleColorRes = R.color.pos_warning_soft
    ),
    Success(
        iconRes = R.drawable.ic_check_circle_24,
        iconTintRes = R.color.pos_secondary,
        circleColorRes = R.color.pos_success_soft
    ),
    Error(
        iconRes = R.drawable.ic_error_24,
        iconTintRes = R.color.pos_warning,
        circleColorRes = R.color.pos_warning_soft
    ),
    Info(
        iconRes = R.drawable.ic_info_24,
        iconTintRes = R.color.pos_info,
        circleColorRes = R.color.pos_info_soft
    );
}

fun showNoticeDialog(
    context: Context,
    title: CharSequence,
    message: CharSequence,
    type: NoticeType = NoticeType.Warning,
    okLabel: CharSequence = context.getString(R.string.notice_dialog_ok),
    onDismiss: (() -> Unit)? = null
): Dialog {
    val binding = DialogNoticeBinding.inflate(android.view.LayoutInflater.from(context))
    binding.tvNoticeTitle.text = title
    binding.tvNoticeMessage.text = message
    binding.ivNoticeIcon.setImageDrawable(ContextCompat.getDrawable(context, type.iconRes))
    binding.ivNoticeIcon.imageTintList = ColorStateList.valueOf(
        ContextCompat.getColor(context, type.iconTintRes)
    )
    binding.noticeIconWrapper.backgroundTintList = ColorStateList.valueOf(
        ContextCompat.getColor(context, type.circleColorRes)
    )
    binding.btnNoticeOk.text = okLabel

    val dialog = AppCompatDialog(context).apply {
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        setContentView(binding.root)
        setCancelable(true)
    }

    binding.btnNoticeOk.setOnClickListener { dialog.dismiss() }
    binding.btnCloseNotice.setOnClickListener { dialog.dismiss() }
    if (onDismiss != null) {
        dialog.setOnDismissListener { onDismiss() }
    }

    dialog.window?.apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        // Force explicit pixel width so the project's themed
        // windowMinWidthMinor (86%) and other dialog-theme constraints
        // don't squeeze the sheet down to a tall narrow column on tablets.
        val widthPx = (360 * context.resources.displayMetrics.density).toInt()
        val params = attributes
        params.gravity = Gravity.CENTER
        params.x = 0
        params.y = 0
        params.width = widthPx
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        attributes = params
    }

    dialog.show()
    return dialog
}

/** String-resource convenience overload. */
fun showNoticeDialog(
    context: Context,
    @StringRes titleRes: Int,
    message: CharSequence,
    type: NoticeType = NoticeType.Warning
): Dialog = showNoticeDialog(
    context = context,
    title = context.getString(titleRes),
    message = message,
    type = type
)

/** Convenience wrappers for common alert types. */
fun showSuccessDialog(
    context: Context,
    message: CharSequence,
    title: CharSequence = context.getString(R.string.notice_success_title)
): Dialog = showNoticeDialog(context, title, message, NoticeType.Success)

fun showErrorDialog(
    context: Context,
    message: CharSequence,
    title: CharSequence = context.getString(R.string.notice_error_title)
): Dialog = showNoticeDialog(context, title, message, NoticeType.Error)

fun showInfoDialog(
    context: Context,
    message: CharSequence,
    title: CharSequence = context.getString(R.string.notice_info_title)
): Dialog = showNoticeDialog(context, title, message, NoticeType.Info)

fun showWarningDialog(
    context: Context,
    message: CharSequence,
    title: CharSequence = context.getString(R.string.notice_warning_title)
): Dialog = showNoticeDialog(context, title, message, NoticeType.Warning)
