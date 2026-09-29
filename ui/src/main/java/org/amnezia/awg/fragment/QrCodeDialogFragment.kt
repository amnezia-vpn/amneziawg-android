/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.fragment

import android.app.Dialog
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import androidx.annotation.StringRes
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.amnezia.awg.R
import org.amnezia.awg.databinding.QrCodeDialogBinding
import org.amnezia.awg.util.QrCodeRenderer
import org.amnezia.awg.util.ShareUtils

/**
 * Shows [ARG_PAYLOAD] as a QR code, with the plain text underneath so it can be selected and copied
 * by hand if the code turns out to be too dense to scan.
 *
 * The code is rendered off the main thread because a long configuration at a large size is real
 * work: a full tunnel config is a few hundred characters, which lands around QR version 15, i.e. 77
 * modules per side, each written pixel by pixel.
 */
class QrCodeDialogFragment : DialogFragment() {
    private var binding: QrCodeDialogBinding? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val payload = requireArguments().getString(ARG_PAYLOAD).orEmpty()
        val context = requireContext()
        val dialogBinding = QrCodeDialogBinding.inflate(LayoutInflater.from(context))
        binding = dialogBinding

        dialogBinding.qrCodeWarning.setText(requireArguments().getInt(ARG_WARNING))
        dialogBinding.qrCodePayload.text = payload

        val sizePx = targetSizePx()
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.Default) { QrCodeRenderer.renderOrNull(payload, sizePx) }
            val currentBinding = binding
            if (currentBinding == null) return@launch
            if (bitmap == null) {
                // Too long for any QR version. The text below is still selectable, so this is a
                // dead end rather than a failure.
                currentBinding.qrCodeImage.setImageResource(R.drawable.ic_qr_code_too_long)
                currentBinding.qrCodeImage.contentDescription =
                    getString(R.string.qr_code_too_long)
            } else {
                currentBinding.qrCodeImage.setImageBitmap(bitmap)
            }
        }

        return MaterialAlertDialogBuilder(context)
            .setTitle(R.string.qr_code_title)
            .setView(dialogBinding.root)
            .setNeutralButton(R.string.copy) { _, _ -> copyPayload() }
            .setPositiveButton(R.string.share) { _, _ -> sharePayload() }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun copyPayload() {
        val anchor = binding?.qrCodePayload ?: return
        ShareUtils.copy(anchor, R.string.qr_code_copy_label, anchor.text)
    }

    private fun sharePayload() {
        val context = context ?: return
        val payload = requireArguments().getString(ARG_PAYLOAD).orEmpty()
        ShareUtils.shareText(context, R.string.share, R.string.qr_code_share_subject, payload)
    }

    /**
     * The largest square that still leaves a margin of dialog padding on both sides. Capped so a
     * tablet does not render a code no camera can frame.
     */
    private fun targetSizePx(): Int {
        val metrics: DisplayMetrics = resources.displayMetrics
        val shortest = minOf(metrics.widthPixels, metrics.heightPixels)
        val fromScreen = (shortest * 0.7f).toInt()
        return minOf(fromScreen, MAX_SIZE_PX).coerceAtLeast(MIN_SIZE_PX)
    }

    companion object {
        private const val TAG = "QrCodeDialogFragment"
        private const val ARG_PAYLOAD = "payload"
        private const val ARG_WARNING = "warning"
        private const val MIN_SIZE_PX = 480
        private const val MAX_SIZE_PX = 1024

        /**
         * @param warning the string shown above the code, saying what a scanner would get. Must
         *   describe this payload, not a generic one — a subscription link does not carry a private
         *   key, and saying it does is both wrong and needlessly alarming.
         */
        fun newInstance(payload: String, @StringRes warning: Int) = QrCodeDialogFragment().apply {
            arguments = Bundle(2).apply {
                putString(ARG_PAYLOAD, payload)
                putInt(ARG_WARNING, warning)
            }
        }

        /** @see newInstance */
        fun show(fragmentManager: FragmentManager, payload: String, @StringRes warning: Int) {
            if (fragmentManager.findFragmentByTag(TAG) != null) return
            newInstance(payload, warning).show(fragmentManager, TAG)
        }
    }
}
