package io.github.ivanosh.stmaterial.signin

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

private const val QUIET_ZONE_MODULES = 4

/**
 * The link to put in the QR code: the one that carries the code (the phone opens Google's page with the code
 * typed in), else the plain address of the sign-in page. Null when neither is a web link, e.g. after a failure,
 * when the presenter puts an error message where the address goes.
 */
fun signInQrLink(fullSignInUrl: String?, address: String?): String? =
    listOf(fullSignInUrl, address).map { it.orEmpty().trim() }.firstOrNull { it.startsWith("https://") || it.startsWith("http://") }

/** One module per cell, with the quiet zone included. Null for no link or when the link does not fit a code. Nothing leaves the device. */
fun signInQrMatrix(link: String?): BitMatrix? {
    if (link.isNullOrBlank()) return null
    val hints = mapOf(EncodeHintType.MARGIN to QUIET_ZONE_MODULES, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.CHARACTER_SET to "UTF-8")
    return try {
        QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, 0, 0, hints)
    } catch (e: WriterException) {
        null
    }
}

/** ARGB pixels, black modules on white, each module [scale] pixels wide, row by row. */
fun signInQrPixels(matrix: BitMatrix, scale: Int): IntArray {
    val width = matrix.width * scale
    return IntArray(width * matrix.height * scale) { i ->
        if (matrix.get(i % width / scale, i / width / scale)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }
}
