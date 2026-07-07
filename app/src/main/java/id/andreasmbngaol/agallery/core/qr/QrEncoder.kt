package id.andreasmbngaol.agallery.core.qr

import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

/**
 * A QR code as a grid of *modules* (not pixels); each dark module is `true`.
 *
 * The quiet zone is intentionally excluded — the renderer draws its own margin
 * — so callers are free to style the dots (square, round, or rounded) and place
 * a center logo.
 *
 * @property size the side length of the matrix in modules.
 * @property cells the row-major module grid, `true` for dark modules.
 */
class QrMatrix(
    val size: Int,
    private val cells: BooleanArray,
) {
    /**
     * Returns whether the module at ([x], [y]) is dark, treating out-of-bounds
     * coordinates as light.
     *
     * @param x the column index.
     * @param y the row index.
     * @return `true` if the module is dark and within bounds.
     */
    fun isDark(x: Int, y: Int): Boolean {
        if (x < 0 || y < 0 || x >= size || y >= size) return false
        return cells[y * size + x]
    }
}

/**
 * Thin wrapper over ZXing (`com.google.zxing:core`).
 *
 * Uses the low-level [Encoder.encode] API (rather than `QRCodeWriter`) to gain
 * access to the per-module matrix, so rounded or dot-style modules can be drawn
 * directly on a Compose canvas. Error correction is pinned to level H (~30%
 * tolerance) so the code stays readable even when its center is covered by a
 * logo.
 */
object QrEncoder {
    /**
     * Encodes [content] into a [QrMatrix].
     *
     * @param content the text to encode.
     * @return the module matrix, or `null` if [content] is blank or encoding
     *   fails.
     */
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
