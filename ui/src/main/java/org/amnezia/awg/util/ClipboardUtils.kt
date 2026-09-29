/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.util

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.core.content.getSystemService
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import org.amnezia.awg.R

/**
 * Standalone utilities for interacting with the system clipboard.
 */
object ClipboardUtils {
    @JvmStatic
    fun copyTextView(view: View) {
        val data = when (view) {
            is TextInputEditText -> Pair(view.editableText, view.hint)
            is TextView -> Pair(view.text, view.contentDescription)
            else -> return
        }
        if (data.first == null || data.first.isEmpty()) {
            return
        }
        val service = view.context.getSystemService<ClipboardManager>() ?: return
        service.setPrimaryClip(ClipData.newPlainText(data.second, data.first))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Snackbar.make(view, view.context.getString(R.string.copied_to_clipboard, data.second), Snackbar.LENGTH_LONG).show()
        }
    }

    /**
     * Copies [text] under [label], which is what the clipboard entry will be called in the other
     * app. Used for the share actions, which have no view of their own to copy from.
     */
    @JvmStatic
    fun copyPlainText(view: View, label: CharSequence, text: CharSequence) {
        if (text.isEmpty()) {
            return
        }
        val service = view.context.getSystemService<ClipboardManager>() ?: return
        // The clipboard is deliberately not readable back by the app on Android 10+, so this is the
        // only record of what was copied. Logged at the length rather than the content, since the
        // content is a credential.
        Log.d(TAG, "Copied $label (${text.length} chars) to clipboard")
        service.setPrimaryClip(ClipData.newPlainText(label, text))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Snackbar.make(view, view.context.getString(R.string.copied_to_clipboard, label), Snackbar.LENGTH_LONG).show()
        }
    }

    private const val TAG = "AmneziaWG/ClipboardUtils"
}
