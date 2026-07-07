package id.andreasmbngaol.agallery.core.qr

import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

/**
 * Matriks QR pada level MODUL (bukan piksel). Tiap sel true = modul gelap.
 * Tidak menyertakan quiet zone -- margin digambar sendiri oleh renderer supaya
 * kita bebas mengatur gaya titik (kotak/titik/membulat) & logo tengah.
 */
class QrMatrix(
    val size: Int,
    private val cells: BooleanArray,
) {
    fun isDark(x: Int, y: Int): Boolean {
        if (x < 0 || y < 0 || x >= size || y >= size) return false
        return cells[y * size + x]
    }
}

/**
 * Pembungkus tipis di atas ZXing (`com.google.zxing:core`). Sengaja memakai API
 * level rendah [Encoder.encode] (bukan QRCodeWriter) supaya dapat akses ke
 * matriks per-modul -> bisa render titik bulat / kotak membulat sendiri di
 * Compose Canvas.
 *
 * ECC dipatok ke level H (toleransi ~30%) supaya QR TETAP terbaca walau bagian
 * tengahnya ditutup logo.
 */
object QrEncoder {
    fun encode(content: String): QrMatrix? {
        if (content.isBlank()) return null
        return try {
            val hints = mapOf<EncodeHintType, Any>(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
                EncodeHintType.CHARACTER_SET to "UTF-8",
            )
            val qrCode = Encoder.encode(content, ErrorCorrectionLevel.H, hints)
            val matrix = qrCode.matrix ?: return null
            val size = matrix.width
            val cells = BooleanArray(size * size)
            for (y in 0 until matrix.height) {
                for (x in 0 until matrix.width) {
                    cells[y * size + x] = matrix.get(x, y).toInt() == 1
                }
            }
            QrMatrix(size, cells)
        } catch (_: Throwable) {
            null
        }
    }
}
