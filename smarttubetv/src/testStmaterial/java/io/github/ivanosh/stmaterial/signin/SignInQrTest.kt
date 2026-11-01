package io.github.ivanosh.stmaterial.signin

import com.google.zxing.BinaryBitmap
import com.google.zxing.LuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInQrTest {
    private val full = "https://youtube.com/qr/activate/ABC-DEF-GHI"
    private val address = "https://yt.be/activate"

    @Test
    fun prefersTheLinkThatCarriesTheCode() {
        assertEquals(full, signInQrLink(full, address))
    }

    @Test
    fun fallsBackToTheAddress() {
        assertEquals(address, signInQrLink(null, address))
        assertEquals(address, signInQrLink("  ", address))
        assertEquals(address, signInQrLink("not a link", address))
    }

    @Test
    fun noLinkMeansNoQr() {
        assertNull(signInQrLink(null, null))
        assertNull(signInQrLink("", ""))
        assertNull(signInQrLink(null, "Error: something"))
    }

    @Test
    fun matrixDecodesBackToTheLink() {
        val matrix = signInQrMatrix(full)
        assertNotNull(matrix)
        assertEquals(full, decode(matrix!!))
    }

    @Test
    fun matrixKeepsAQuietZone() {
        val m = signInQrMatrix(full)!!
        for (i in 0 until m.width) for (edge in 0 until 4) {
            assertTrue(!m.get(i, edge) && !m.get(i, m.height - 1 - edge) && !m.get(edge, i) && !m.get(m.width - 1 - edge, i))
        }
    }

    @Test
    fun blankLinkGivesNoMatrix() {
        assertNull(signInQrMatrix(""))
    }

    @Test
    fun pixelsAreDarkOnWhite() {
        val m = signInQrMatrix(full)!!
        val px = signInQrPixels(m, 3)
        assertEquals(m.width * 3 * m.height * 3, px.size)
        assertEquals(0xFFFFFFFF.toInt(), px[0]) // quiet zone corner
        assertTrue(px.any { it == 0xFF000000.toInt() })
    }

    private fun decode(m: com.google.zxing.common.BitMatrix): String {
        val scale = 4
        val w = m.width * scale
        val h = m.height * scale
        val lum = ByteArray(w * h) { i -> if (m.get(i % w / scale, i / w / scale)) 0 else 0xFF.toByte() }
        val source = object : LuminanceSource(w, h) {
            override fun getRow(y: Int, row: ByteArray?) = lum.copyOfRange(y * w, (y + 1) * w)
            override fun getMatrix() = lum
            override fun isCropSupported() = false
        }
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    }
}
