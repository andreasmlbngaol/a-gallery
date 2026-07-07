package id.andreasmbngaol.agallery.core.ui

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import id.andreasmbngaol.agallery.domain.model.EdgeEffectMode

/**
 * Minimum SDK level for a real blur via `RenderEffect`. The
 * [EdgeEffectMode.BLURRY] mode requires API 32+; below that it degrades
 * automatically to [EdgeEffectMode.DARKEN].
 */
const val BLURRY_EDGE_MIN_SDK = 32

/**
 * Resolves the effective [EdgeEffectMode] for the given device.
 *
 * Two fallbacks are applied:
 * - a `null` choice defaults to [EdgeEffectMode.DARKEN], a light option that
 *   works on every device;
 * - [EdgeEffectMode.BLURRY] on a device below [BLURRY_EDGE_MIN_SDK] degrades to
 *   [EdgeEffectMode.DARKEN], since `RenderEffect` is unavailable there.
 *
 * @param chosen the mode selected by the user, or `null` when unset.
 * @param sdkInt the device's SDK level, typically [Build.VERSION.SDK_INT].
 * @return the mode that should actually be applied.
 */
fun resolveEdgeEffectMode(chosen: EdgeEffectMode?, sdkInt: Int): EdgeEffectMode {
    val base = chosen ?: EdgeEffectMode.DARKEN
    return if (base == EdgeEffectMode.BLURRY && sdkInt < BLURRY_EDGE_MIN_SDK) {
        EdgeEffectMode.DARKEN
    } else {
        base
    }
}

/**
 * Remembers the effective [EdgeEffectMode] for the current device, recomputing
 * only when [chosen] changes.
 *
 * @param chosen the mode selected by the user, or `null` when unset.
 * @return the resolved mode, stable across recompositions.
 */
@Composable
fun rememberEffectiveEdgeEffectMode(chosen: EdgeEffectMode?): EdgeEffectMode =
    remember(chosen) { resolveEdgeEffectMode(chosen, Build.VERSION.SDK_INT) }

/**
 * Returns whether the device can render a real [EdgeEffectMode.BLURRY] effect
 * (a `RenderEffect` blur).
 *
 * @param sdkInt the device's SDK level, typically [Build.VERSION.SDK_INT].
 * @return `true` when [sdkInt] is at least [BLURRY_EDGE_MIN_SDK].
 */
fun isBlurryEdgeSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
    sdkInt >= BLURRY_EDGE_MIN_SDK
