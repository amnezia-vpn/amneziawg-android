/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.vpn.preference

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.widget.Toast
import androidx.preference.Preference
import org.amnezia.vpn.Application
import org.amnezia.vpn.BuildConfig
import org.amnezia.vpn.R
import org.amnezia.vpn.backend.Backend
import org.amnezia.vpn.backend.GoBackend
import org.amnezia.vpn.backend.WgQuickBackend
import org.amnezia.vpn.util.ErrorMessages
import org.amnezia.vpn.util.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VersionPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    private var versionSummary: String? = null

    override fun getSummary() = versionSummary

    override fun getTitle() = context.getString(R.string.version_title, BuildConfig.VERSION_NAME)

    override fun onClick() {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse("https://www.wireguard.com/")
        try {
            context.startActivity(intent)
        } catch (e: Throwable) {
            Toast.makeText(context, ErrorMessages[e], Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private fun getBackendPrettyName(context: Context, backend: Backend) = when (backend) {
            is WgQuickBackend -> context.getString(R.string.type_name_kernel_module)
            is GoBackend -> context.getString(R.string.type_name_go_userspace)
            else -> ""
        }
    }

    init {
        lifecycleScope.launch {
            val backend = Application.getBackend()
            versionSummary = getContext().getString(R.string.version_summary_checking, getBackendPrettyName(context, backend).lowercase())
            notifyChanged()
            versionSummary = try {
                getContext().getString(R.string.version_summary, getBackendPrettyName(context, backend), withContext(Dispatchers.IO) { backend.version })
            } catch (_: Throwable) {
                getContext().getString(R.string.version_summary_unknown, getBackendPrettyName(context, backend).lowercase())
            }
            notifyChanged()
        }
    }
}
