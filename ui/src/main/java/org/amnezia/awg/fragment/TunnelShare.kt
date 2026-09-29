/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.fragment

import android.view.View
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import org.amnezia.awg.R
import org.amnezia.awg.model.ObservableTunnel
import org.amnezia.awg.util.ErrorMessages
import org.amnezia.awg.util.ShareUtils

/**
 * The share action on a tunnel row: copy the configuration, show it as a QR code, or save it to a
 * file. All three hand out the tunnel's private key, so all three authenticate first.
 */
fun Fragment.showTunnelShareMenu(tunnel: ObservableTunnel, anchor: View) {
    val context = context ?: return
    val activity = activity as? FragmentActivity ?: return
    PopupMenu(context, anchor).apply {
        menuInflater.inflate(R.menu.tunnel_share, menu)
        setOnMenuItemClickListener { item ->
            withTunnelConfig(tunnel, activity) { config ->
                when (item.itemId) {
                    R.id.tunnel_share_copy ->
                        ShareUtils.copy(anchor, R.string.tunnel_share_copy_label, config)

                    R.id.tunnel_share_qr ->
                        QrCodeDialogFragment.show(
                            childFragmentManager, config, R.string.qr_code_contains_private_key
                        )

                    R.id.tunnel_share_export ->
                        exportTunnelConfig(activity, anchor, tunnel.name, config)
                }
            }
            true
        }
        show()
    }
}

/**
 * Resolves [tunnel]'s configuration and runs [block] on it behind an authentication prompt.
 *
 * A configuration is read per tunnel rather than kept in memory, so a failure here is reported to
 * the user instead of throwing.
 */
private fun Fragment.withTunnelConfig(
    tunnel: ObservableTunnel,
    activity: FragmentActivity,
    block: (String) -> Unit
) {
    if (!ShareUtils.isConfigExportAllowed) {
        snack(activity, getString(R.string.share_export_denied))
        return
    }
    // The prompt is shown first and the config is only fetched afterwards, so an unauthenticated
    // attempt never touches the private key at all.
    ShareUtils.withAuthentication(this, R.string.biometric_prompt_share_tunnel_title, { message ->
        snack(activity, message)
    }) {
        viewLifecycleOwner.lifecycleScope.launch {
            val config = runCatching { tunnel.getConfigAsync().toAwgQuickString() }
            config
                .onSuccess(block)
                .onFailure { throwable ->
                    snack(activity, getString(R.string.tunnel_share_read_error, ErrorMessages[throwable]))
                }
        }
    }
}

/** Saves [config] to Downloads under the tunnel's own name, reporting where it went. */
private fun Fragment.exportTunnelConfig(
    activity: FragmentActivity,
    anchor: View,
    tunnelName: String,
    config: String
) {
    viewLifecycleOwner.lifecycleScope.launch {
        ShareUtils.exportFile(activity, ShareUtils.configFileName(tunnelName), config) { result ->
            val message = result.fold(
                onSuccess = { path -> getString(R.string.share_export_success, path) },
                onFailure = { throwable -> getString(R.string.share_export_error, ErrorMessages[throwable]) }
            )
            snack(activity, message, anchor)
        }
    }
}

/**
 * Reports [message] against the tunnel list, falling back to the window content outside it — the
 * share sheet and the file saver can both outlive the row that started them.
 */
private fun Fragment.snack(activity: FragmentActivity, message: CharSequence, anchor: View? = null) {
    val anchorView = anchor ?: view?.findViewById<View>(R.id.create_fab)
    val root = anchorView?.let { Snackbar.make(it, message, Snackbar.LENGTH_LONG) }
        ?: Snackbar.make(activity.findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG)
    anchorView?.let { root.setAnchorView(it) }
    root.show()
}
