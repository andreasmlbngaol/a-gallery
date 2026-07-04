package id.andreasmbngaol.agallery.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import id.andreasmbngaol.agallery.domain.model.EdgeEffectMode

/**
 * Menggambar efek di area status bar (atas) & navigation bar (bawah) DI ATAS [content].
 *
 * - BLURRY  : blur kaca (Haze) + gradasi tint theme yang MENGUAT ke tepi.
 *             Blur meng-obscure detail, gradasi menambah kontras supaya
 *             teks/ikon overlay (TopAppBar) tetap terbaca meski di atas
 *             foto terang di light mode / foto gelap di dark mode.
 * - DARKEN  : gradasi tint theme saja (tanpa blur).
 * - OFF     : tanpa efek.
 *
 * ## Z-order (penting untuk BLURRY)
 *
 * Urutan menggambar (bawah → atas):
 * 1. [content]  — di-tag `hazeSource` saat mode BLURRY. Ini yang di-blur.
 * 2. Top & bottom [EdgeScrim] — dua sub-layer:
 *    a. hazeEffect  : sample warna dari layer 1, blur bertingkat.
 *    b. gradient   : tint theme (surface color) semi-transparan yang menguat
 *                    ke arah tepi. Menambah "matte" / darkening.
 * 3. [topOverlay] — slot untuk elemen yang HARUS tampil crisp DI ATAS scrim,
 *    mis. TopAppBar transparan. Karena digambar setelah scrim, konten
 *    overlay ini TIDAK ikut kena blur.
 *
 * @param topExtraHeight tambahan tinggi strip scrim atas di luar status bar
 * (biasanya = tinggi TopAppBar).
 * @param topOverlay slot yang digambar paling akhir, di atas scrim.
 */
@Composable
fun SystemBarScrim(
    mode: EdgeEffectMode,
    modifier: Modifier = Modifier,
    topExtraHeight: Dp = 0.dp,
    topOverlay: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable (contentModifier: Modifier) -> Unit,
) {
    val hazeState = remember { HazeState() }
    val surfaceColor = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(surfaceColor),
    ) {
        // Layer 1: konten yang di-tag hazeSource (kalau FROSTED).
        val sourceModifier =
            if (mode == EdgeEffectMode.BLURRY) Modifier.hazeSource(hazeState) else Modifier
        content(sourceModifier)

        // Layer 2: scrim atas & bawah.
        if (mode != EdgeEffectMode.OFF) {
            val topModifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .then(
                    if (topExtraHeight > 0.dp) {
                        val density = LocalDensity.current
                        val statusBarTopDp = with(density) {
                            WindowInsets.statusBars.getTop(density).toDp()
                        }
                        Modifier.height(statusBarTopDp + topExtraHeight)
                    } else {
                        Modifier.windowInsetsTopHeight(WindowInsets.statusBars)
                    }
                )

            EdgeScrim(
                mode = mode,
                hazeState = hazeState,
                scrimColor = surfaceColor,
                top = true,
                modifier = topModifier,
            )
            EdgeScrim(
                mode = mode,
                hazeState = hazeState,
                scrimColor = surfaceColor,
                top = false,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsBottomHeight(WindowInsets.navigationBars),
            )
        }

        // Layer 3: overlay crisp di atas scrim (mis. TopAppBar transparan).
        topOverlay?.invoke(this)
    }
}

// Blur maksimum di ujung tepi layar (tipis saja), lalu memudar ke 0 ke arah konten.
private val BlurryBlurRadius = 8.dp

// Alpha maksimum gradasi tint theme di tepi (mode BLURRY). Cukup besar buat menambah
// kontras teks TopAppBar di atas foto terang/gelap, tapi tidak sampai
// menutup foto sepenuhnya (blur masih terlihat).
private const val BlurryGradientAlpha = 0.22f
// Alpha gradasi pada mode DARKEN (tanpa blur) — lebih pekat, karena
// gradient adalah satu-satunya sumber kontras.
private const val DarkenGradientAlpha = 0.55f

@Composable
private fun EdgeScrim(
    mode: EdgeEffectMode,
    hazeState: HazeState,
    scrimColor: Color,
    top: Boolean,
    modifier: Modifier,
) {
    when (mode) {
        EdgeEffectMode.BLURRY -> {
            // Bungkus 2 sub-layer:
            // - hazeEffect (blur)
            // - gradient tint theme (menguat ke tepi, memudar ke konten)
            //
            // Modifier caller (align/fillMaxWidth/height) dipakai OUTER Box.
            // Sub-layer memakai matchParentSize supaya menutupi seluruh area
            // scrim tanpa ikut kontribusi ke pengukuran ulang.
            Box(modifier = modifier) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .hazeEffect(state = hazeState) {
                            blurRadius = BlurryBlurRadius
                            backgroundColor = scrimColor
                            tints = listOf(HazeTint(scrimColor.copy(alpha = 0.15f)))
                            // Intensitas blur bergradasi. verticalGradient: startIntensity di
                            // tepi ATAS strip, endIntensity di tepi BAWAH strip.
                            // Scrim atas  : kuat di atas (1f) -> 0 di bawah (arah konten).
                            // Scrim bawah : 0 di atas (arah konten) -> kuat di bawah (1f).
                            progressive = HazeProgressive.verticalGradient(
                                startIntensity = if (top) 1f else 0f,
                                endIntensity = if (top) 0f else 1f,
                                preferPerformance = true,
                            )
                        },
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = if (top) {
                                    listOf(
                                        scrimColor.copy(alpha = BlurryGradientAlpha),
                                        Color.Transparent,
                                    )
                                } else {
                                    listOf(
                                        Color.Transparent,
                                        scrimColor.copy(alpha = BlurryGradientAlpha),
                                    )
                                }
                            )
                        ),
                )
            }
        }

        EdgeEffectMode.DARKEN -> {
            val colors = if (top) {
                listOf(scrimColor.copy(alpha = DarkenGradientAlpha), Color.Transparent)
            } else {
                listOf(Color.Transparent, scrimColor.copy(alpha = DarkenGradientAlpha))
            }
            Box(modifier = modifier.background(Brush.verticalGradient(colors)))
        }

        EdgeEffectMode.OFF -> Unit
    }
}
