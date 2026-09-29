/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.util

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import androidx.annotation.StringRes
import androidx.core.app.ShareCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.amnezia.awg.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * Getting a tunnel configuration or a subscription link out of the app: to the clipboard, to
 * another app via the share sheet, to a file in Downloads, or onto the screen as a QR code.
 *
 * Two of the four payloads are secrets. A tunnel configuration carries its own private key, and a
 * subscription URL is the token the app authenticates to the provider with. Whoever holds either one
 * can act as the user, so [withAuthentication] gates the former and [confirmSecret] warns about the
 * latter. Neither is a hard lock: both follow the behaviour already set by the zip exporter, which
 * lets the action through when the device has no biometric hardware or no enrolled credential.
 */
object ShareUtils {
    private const val TAG = "AmneziaWG/ShareUtils"

    /**
     * Runs [block] once the user has authenticated, or immediately on a device with no biometric
     * hardware or no enrolled credential.
     *
     * [onFailure] gets the message to show for a rejected or failed attempt; a cancellation is
     * silent, because the user just backed out.
     */
    fun withAuthentication(
        fragment: Fragment,
        @StringRes promptTitle: Int,
        onFailure: (CharSequence) -> Unit,
        block: () -> Unit
    ) {
        BiometricAuthenticator.authenticate(promptTitle, fragment) {
            when (it) {
                // Authenticated, or there is nothing to authenticate with — same as the zip
                // exporter, which also lets the export through in the latter case.
                is BiometricAuthenticator.Result.Success,
                is BiometricAuthenticator.Result.HardwareUnavailableOrDisabled -> block()

                is BiometricAuthenticator.Result.Failure -> onFailure(it.message)
                is BiometricAuthenticator.Result.Cancelled -> Unit
            }
        }
    }

    /**
     * Warns that [warning] describes a credential, then runs [onConfirmed] if the user agrees.
     *
     * Used for the subscription URL, which is worth protecting but not worth locking the user out
     * of their own provider over.
     */
    fun confirmSecret(fragment: Fragment, @StringRes warning: Int, onConfirmed: () -> Unit) {
        val context = fragment.context ?: return
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.share_confirm_title)
            .setMessage(warning)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.share_continue) { _, _ -> onConfirmed() }
            .show()
    }

    /** Puts [text] on the clipboard and confirms it. */
    fun copy(anchor: View, @StringRes label: Int, text: CharSequence) {
        val context = anchor.context
        ClipboardUtils.copyPlainText(anchor, context.getString(label), text)
    }

    /**
     * Whether configuration export is permitted at all.
     *
     * An app-restrictions policy that forbids exporting a zip forbids handing out the same
     * configurations one at a time just as much, so every share path respects it.
     */
    val isConfigExportAllowed: Boolean
        get() = !AdminKnobs.disableConfigExport

    /** Opens the system share sheet with [text] as plain text. */
    fun shareText(context: Context, @StringRes chooserTitle: Int, @StringRes subject: Int, text: CharSequence) {
        val intent = ShareCompat.IntentBuilder(context)
            .setType("text/plain")
            .setSubject(context.getString(subject))
            .setText(text)
            .setChooserTitle(chooserTitle)
            .createChooserIntent()
            .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
        context.startActivity(intent)
    }

    /**
     * Writes [text] to `Downloads/<name>` and reports where it landed, reusing the same save path
     * as the zip exporter so both land somewhere the user can actually find.
     *
     * [onDone] is called with the saved path, or with an error message.
     */
    suspend fun exportFile(
        activity: FragmentActivity,
        name: String,
        text: CharSequence,
        onDone: (Result<String>) -> Unit
    ) {
        val context = activity as Context
        val saver = DownloadsFileSaver(activity)
        try {
            val file = withContext(Dispatchers.IO) {
                // Logged before the write, so a failed export is still visible in logcat: the
                // snackbar only appears on success and is gone within a few seconds.
                Log.d(TAG, "Exporting $name (${text.length} chars) to Downloads")
                // No MIME type: MediaStore appends an extension derived from it, and there is no
                // type that maps to `.conf`, so passing one turns `DE-FRA.conf` into
                // `DE-FRA.conf.txt`. With the type omitted the name is used verbatim, which also
                // makes the overwrite delete below match.
                val downloadsFile = saver.save(name, mimeType = null, overwriteExisting = true)
                    ?: return@withContext null
                try {
                    OutputStreamWriter(downloadsFile.outputStream, StandardCharsets.UTF_8).use {
                        it.write(text.toString())
                    }
                    downloadsFile
                } catch (e: Throwable) {
                    // Never leave a half-written file lying around next to a valid-looking name.
                    downloadsFile.delete()
                    throw e
                }
            }
            if (file == null) {
                // The user declined the storage permission, so there is nothing to report.
                return
            }
            onDone(Result.success(file.fileName))
        } catch (e: Throwable) {
            Log.e(TAG, "Export of $name failed", e)
            onDone(Result.failure(e))
        }
    }

    /**
     * The file name a tunnel is exported under.
     *
     * Tunnel names are already limited to `[a-zA-Z0-9_=+.-]{1,15}` by `Tunnel.NAME_PATTERN`, so
     * they are safe as a path segment and the name is reused verbatim with the extension appended.
     */
    fun configFileName(tunnelName: String) = tunnelName + CONFIG_EXTENSION

    private const val CONFIG_EXTENSION = ".conf"
}
