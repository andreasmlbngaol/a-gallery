package id.andreasmbngaol.agallery.presentation.tools.qr

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import id.andreasmbngaol.agallery.core.qr.QrMatrix
import id.andreasmbngaol.agallery.domain.model.QrDotStyle
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Renderer QR kustom di Compose Canvas. Menggambar matriks per-modul dgn gaya
 * [dotStyle] (kotak / titik). Untuk gaya titik, ketiga "mata" finder pattern
 * digambar sebagai bentuk membulat (bukan titik lepas) supaya tetap terbaca
 * scanner + terlihat modern.
 *
 * Logo tengah (foto ATAU ikon) digambar SEAMLESS: modul QR di area logo
 * di-skip (tidak digambar) sehingga logo menyatu dgn latar tanpa kotak putih --
 * penting utk PNG transparan biar tidak muncul "background" kotak. ECC level H
 * (di [QrMatrix]) menjaga QR tetap terbaca walau tengahnya tertutup. Foto
 * di-center-crop supaya tidak gepeng.
 */
@Composable
fun QrCodeView(
    matrix: QrMatrix,
    dotStyle: QrDotStyle,
    darkColor: Color,
    lightColor: Color,
    modifier: Modifier = Modifier,
    logoBitmap: ImageBitmap? = null,
    logoIcon: ImageVector? = null,
) {
    val logoPainter = logoIcon?.let { rememberVectorPainter(it) }

    Canvas(modifier = modifier) {
        val quiet = 2
        val n = matrix.size
        val dim = size.minDimension
        val total = n + quiet * 2
        val cell = dim / total
        val originX = (size.width - dim) / 2f
        val originY = (size.height - dim) / 2f

        fun left(x: Int): Float = originX + (quiet + x) * cell
        fun top(y: Int): Float = originY + (quiet + y) * cell
        fun isFinder(x: Int, y: Int): Boolean =
            (x < 7 && y < 7) || (x >= n - 7 && y < 7) || (x < 7 && y >= n - 7)

        // Area logo (kalau ada). Modul yang pusatnya jatuh di area ini di-skip
        // supaya logo tampil seamless tanpa kotak putih.
        val logoPresent = logoBitmap != null || logoPainter != null
        val logoSize = if (logoPresent) dim * 0.24f else 0f
        val logoLeft = originX + (dim - logoSize) / 2f
        val logoTop = originY + (dim - logoSize) / 2f
        val clearPad = cell * 0.6f
        val clearLeft = logoLeft - clearPad
        val clearTop = logoTop - clearPad
        val clearRight = logoLeft + logoSize + clearPad
        val clearBottom = logoTop + logoSize + clearPad

        // Latar (quiet zone) putih.
        drawRect(color = lightColor, topLeft = Offset(originX, originY), size = Size(dim, dim))

        // Modul data.
        for (y in 0 until n) {
            for (x in 0 until n) {
                if (!matrix.isDark(x, y)) continue
                if (dotStyle != QrDotStyle.SQUARE && isFinder(x, y)) continue
                val l = left(x)
                val t = top(y)
                if (logoPresent) {
                    val cx = l + cell / 2f
                    val cy = t + cell / 2f
                    if (cx in clearLeft..clearRight && cy in clearTop..clearBottom) continue
                }
                when (dotStyle) {
                    QrDotStyle.SQUARE ->
                        drawRect(darkColor, Offset(l, t), Size(cell, cell))
                    QrDotStyle.DOTS ->
                        drawCircle(
                            color = darkColor,
                            radius = cell * 0.46f,
                            center = Offset(l + cell / 2f, t + cell / 2f),
                        )
                }
            }
        }

        // Mata finder membulat (hanya utk gaya non-kotak).
        if (dotStyle != QrDotStyle.SQUARE) {
            val eyes = listOf(0 to 0, n - 7 to 0, 0 to (n - 7))
            eyes.forEach { (fx, fy) ->
                val ringLeft = left(fx) + cell / 2f
                val ringTop = top(fy) + cell / 2f
                val ringSize = cell * 6f
                drawRoundRect(
                    color = darkColor,
                    topLeft = Offset(ringLeft, ringTop),
                    size = Size(ringSize, ringSize),
                    cornerRadius = CornerRadius(cell * 1.75f),
                    style = Stroke(width = cell),
                )
                val inLeft = left(fx + 2)
                val inTop = top(fy + 2)
                val inSize = cell * 3f
                drawRoundRect(
                    color = darkColor,
                    topLeft = Offset(inLeft, inTop),
                    size = Size(inSize, inSize),
                    cornerRadius = CornerRadius(cell),
                )
            }
        }

        // Logo tengah (foto atau ikon) -- TANPA kotak putih (seamless).
        if (logoBitmap != null) {
            // Center-crop supaya foto tidak gepeng saat dipaksa ke area persegi.
            val srcDim = min(logoBitmap.width, logoBitmap.height)
            val srcX = (logoBitmap.width - srcDim) / 2
            val srcY = (logoBitmap.height - srcDim) / 2
            drawImage(
                image = logoBitmap,
                srcOffset = IntOffset(srcX, srcY),
                srcSize = IntSize(srcDim, srcDim),
                dstOffset = IntOffset(logoLeft.roundToInt(), logoTop.roundToInt()),
                dstSize = IntSize(logoSize.roundToInt(), logoSize.roundToInt()),
            )
        } else if (logoPainter != null) {
            translate(left = logoLeft, top = logoTop) {
                with(logoPainter) {
                    draw(size = Size(logoSize, logoSize), colorFilter = ColorFilter.tint(darkColor))
                }
            }
        }
    }
}
