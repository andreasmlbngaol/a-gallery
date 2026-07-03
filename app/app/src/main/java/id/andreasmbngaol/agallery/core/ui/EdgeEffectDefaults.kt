package id.andreasmbngaol.agallery.core.ui

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import id.andreasmbngaol.agallery.domain.model.EdgeEffectMode

/**
 * Ambang perangkat yang bisa blur beneran lewat RenderEffect. Sesuai kesepakatan:
 * FROSTED jadi default untuk API >= 32.
 */
const val FROSTED_MIN_SDK = 32

/**
 * Resolusi "default cerdas" (hybrid):
 * - chosen null  -> FROSTED kalau API >= 32, selain itu GRADIENT.
 * - chosen FROSTED di perangkat < 32 -> auto turun ke GRADIENT (fallback).
 * - selain itu -> pakai pilihan user apa adanya.
 */
fun resolveEdgeEffectMode(chosen: EdgeEffectMode?, sdkInt: Int): EdgeEffectMode {
    val base = chosen
        ?: if (sdkInt >= FROSTED_MIN_SDK) EdgeEffectMode.FROSTED else EdgeEffectMode.GRADIENT
    return if (base == EdgeEffectMode.FROSTED && sdkInt < FROSTED_MIN_SDK) {
        EdgeEffectMode.GRADIENT
    } else {
        base
    }
}

@Composable
fun rememberEffectiveEdgeEffectMode(chosen: EdgeEffectMode?): EdgeEffectMode =
    remember(chosen) { resolveEdgeEffectMode(chosen, Build.VERSION.SDK_INT) }

/** True kalau perangkat sanggup efek frosted (blur) beneran. */
fun isFrostedSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= FROSTED_MIN_SDK
