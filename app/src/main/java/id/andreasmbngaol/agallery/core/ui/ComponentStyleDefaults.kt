package id.andreasmbngaol.agallery.core.ui

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import id.andreasmbngaol.agallery.domain.model.ComponentStyle

/**
 * Minimum SDK level required for the liquid-glass effect, which is backed by
 * `RuntimeShader` and therefore only available on Android 13 (API 33) and above.
 */
const val GLASS_MIN_SDK = 33

/**
 * Resolves the effective [ComponentStyle] to render for the given device.
 *
 * Two fallbacks are applied:
 * - a `null` choice defaults to [ComponentStyle.FROSTED];
 * - [ComponentStyle.GLASS] on a device below [GLASS_MIN_SDK] degrades to
 *   [ComponentStyle.FROSTED], since `RuntimeShader` is unavailable there.
 *
 * @param chosen the style selected by the user, or `null` when unset.
 * @param sdkInt the device's SDK level, typically [Build.VERSION.SDK_INT].
 * @return the style that should actually be rendered.
 */
fun resolveComponentStyle(chosen: ComponentStyle?, sdkInt: Int): ComponentStyle {
    val base = chosen ?: ComponentStyle.FROSTED
    return if (base == ComponentStyle.GLASS && sdkInt < GLASS_MIN_SDK) {
        ComponentStyle.FROSTED
    } else {
        base
    }
}

/**
 * Remembers the effective [ComponentStyle] for the current device, recomputing
 * only when [chosen] changes.
 *
 * @param chosen the style selected by the user, or `null` when unset.
 * @return the resolved style, stable across recompositions.
 */
@Composable
fun rememberEffectiveComponentStyle(chosen: ComponentStyle?): ComponentStyle =
    remember(chosen) { resolveComponentStyle(chosen, Build.VERSION.SDK_INT) }

/**
 * Returns whether the device can render the liquid-glass effect.
 *
 * @param sdkInt the device's SDK level, typically [Build.VERSION.SDK_INT].
 * @return `true` when [sdkInt] is at least [GLASS_MIN_SDK].
 */
fun isGlassSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= GLASS_MIN_SDK

/**
 * Returns whether this style draws a Kyant glass backdrop, i.e. renders the
 * content behind the surface into a layer and applies effects to it.
 *
 * [ComponentStyle.GLASS] and [ComponentStyle.FROSTED] both draw a backdrop,
 * while [ComponentStyle.SOLID] paints an opaque fill instead. Because the
 * backdrop relies on `RuntimeShader`, this is always `false` below
 * [GLASS_MIN_SDK] regardless of style, which forces the opaque fallback.
 *
 * @param sdkInt the device's SDK level, typically [Build.VERSION.SDK_INT].
 * @return `true` when a glass backdrop should be drawn.
 * @see usesBlur
 * @see usesLens
 */
fun ComponentStyle.drawsBackdrop(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
    this != ComponentStyle.SOLID && sdkInt >= GLASS_MIN_SDK

/**
 * Returns whether this style applies the lens/refraction ("liquid glass")
 * effect.
 *
 * Only [ComponentStyle.GLASS] does; [ComponentStyle.FROSTED] deliberately omits
 * it to keep a haze look with no distortion.
 */
fun ComponentStyle.usesLens(): Boolean = this == ComponentStyle.GLASS

/**
 * Returns whether this style applies a gaussian blur to the backdrop.
 *
 * Only [ComponentStyle.GLASS] does; [ComponentStyle.FROSTED] uses a tinted veil
 * instead, which also avoids the hexagonal artifacts the Kyant blur kernel can
 * produce on small round surfaces such as the sort and search buttons.
 */
fun ComponentStyle.usesBlur(): Boolean = this == ComponentStyle.GLASS

/**
 * Returns whether the backdrop is captured live on every frame.
 *
 * Only [ComponentStyle.GLASS] does, so its refraction tracks motion. For
 * [ComponentStyle.FROSTED] the snapshot is frozen during interaction to save
 * GPU work; callers that observe interaction disable capture at that time.
 */
fun ComponentStyle.usesLiveBackdrop(): Boolean = this == ComponentStyle.GLASS
