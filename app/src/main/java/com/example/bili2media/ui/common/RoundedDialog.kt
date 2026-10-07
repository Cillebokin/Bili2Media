package com.example.bili2media.ui.common

import android.content.DialogInterface
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.example.bili2media.R

fun AlertDialog.Builder.showRounded(): AlertDialog {
    return create().apply {
        setOnShowListener {
            window?.setBackgroundDrawable(
                ContextCompat.getDrawable(context, R.drawable.bg_dialog_rounded)
                    ?: ColorDrawable(android.graphics.Color.TRANSPARENT)
            )
            getButton(DialogInterface.BUTTON_POSITIVE)?.setTextColor(
                ContextCompat.getColor(context, R.color.bili2media_accent)
            )
            getButton(DialogInterface.BUTTON_NEGATIVE)?.setTextColor(
                ContextCompat.getColor(context, R.color.bili2media_text_secondary)
            )
            getButton(DialogInterface.BUTTON_NEUTRAL)?.setTextColor(
                ContextCompat.getColor(context, R.color.bili2media_text_secondary)
            )
        }
    }.also { it.show() }
}
