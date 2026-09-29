/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.util

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Renders text as a QR code bitmap.
 *
 * The encoder itself ships in `com.google.zxing:core`, which the app already pulls in through
 * `zxing-android-embedded` — the same artifact that provides the scanner. Nothing extra is needed
 * to *write* codes as well as read them.
 */
object QrCodeRenderer {
    private const val TAG = "AmneziaWG/QrCodeRenderer"

    /**
     * Quiet zone, in modules, around the code. The spec asks for four, but scanners tolerate far
     * less and every module we drop is one more module of payload in a code that is already close
     * to the size limit for a full configuration.
     */
    private const val MARGIN = 2

    /** Below this the code stops being reliably scannable, so we never go there. */
    private const val MIN_PIXELS = 320

    private val hints = mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        // Tunnels are the long payloads, so they get the lowest error correction to keep the
        // module count down. A dropped code is still a QR code; an oversized one will not scan.
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
        EncodeHintType.MARGIN to MARGIN
    )

    /**
     * Encodes [text] into a square bitmap of roughly [targetPixels] on a side.
     *
     * The bitmap is sized to an exact whole number of modules per pixel, which keeps every module
     * edge sharp — scaling to an arbitrary size makes some modules blur into their neighbours and
     * costs a couple of scan attempts.
     *
     * @throws WriterException if [text] is empty or too long for any QR version.
     */
    @Throws(WriterException::class)
    fun render(text: String, targetPixels: Int): Bitmap {
        val writer = QRCodeWriter()
        // A 1x1 request yields the matrix at its natural size, i.e. one pixel per module, which is
        // all we need to learn how many modules there are before picking an integer scale factor.
        val natural = writer.encode(text, BarcodeFormat.QR_CODE, 1, 1, hints)
        val modules = natural.width
        val wanted = targetPixels.coerceAtLeast(MIN_PIXELS)
        val scale = (wanted / modules).coerceAtLeast(1)
        val side = modules * scale
        Log.d(TAG, "Rendering QR for ${text.length} chars: $modules modules, scale $scale, ${side}px")
        val matrix = writer.encode(text, BarcodeFormat.QR_CODE, side, side, hints)
        return toBitmap(matrix)
    }

    /** As [render], but returns null instead of throwing when the text will not fit. */
    fun renderOrNull(text: String, targetPixels: Int): Bitmap? = try {
        render(text, targetPixels)
    } catch (e: WriterException) {
        Log.w(TAG, "Content does not fit in a QR code (${text.length} chars)", e)
        null
    }

    private fun toBitmap(matrix: BitMatrix): Bitmap {
        val side = matrix.width
        val pixels = IntArray(side * side)
        for (y in 0 until side) {
            val row = y * side
            for (x in 0 until side) {
                pixels[row + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
    }
}
