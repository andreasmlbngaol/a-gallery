package id.andreasmbngaol.agallery.core.ui

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import id.andreasmbngaol.agallery.domain.model.EdgeEffectMode

/**
 * Ambang perangkat yang bisa blur beneran lewat RenderEffect. Mode BLURRY butuh
 * API >= 32; di bawah itu turun otomatis ke DARKEN.
 */
const val BLURRY_EDGE_MIN_SDK = 32

/**
 * Resolusi default:
 * - chosen null -> DARKEN (default "rata tengah": ringan & jalan di semua perangkat).
 * - chosen BLURRY di perangkat < 32 -> auto turun ke DARKEN (fallback, RenderEffect absen).
 * - selain itu -> pakai pilihan user apa adanya.
 */
fun resolveEdgeEffectMode(chosen: EdgeEffectMode?, sdkInt: Int): EdgeEffectMode {
    val base = chosen ?: EdgeEffectMode.DARKEN
    return if (base == EdgeEffectMode.BLURRY && sdkInt < BLURRY_EDGE_MIN_SDK) {
        EdgeEffectMode.DARKEN
    } else {
        base
    }
}

@Composable
fun rememberEffectiveEdgeEffectMode(chosen: EdgeEffectMode?): EdgeEffectMode =
    remember(chosen) { resolveEdgeEffectMode(chosen, Build.VERSION.SDK_INT) }

/** True kalau perangkat sanggup efek BLURRY (blur RenderEffect) beneran. */
fun isBlurryEdgeSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
    sdkInt >= BLURRY_EDGE_MIN_SDK
