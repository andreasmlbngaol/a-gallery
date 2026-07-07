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
import id.andreasmbngaol.agallery.domain.model.settings.EdgeEffectMode

/**
 * Draws an effect over the status-bar (top) and navigation-bar (bottom) areas,
 * on top of [content].
 *
 * - **BLURRY**: a glass blur (Haze) plus a theme-tinted gradient that
 *   intensifies toward the edge. The blur obscures detail while the gradient
 *   adds contrast so overlay text/icons (a `TopAppBar`) stay readable over a
 *   bright photo in light mode or a dark photo in dark mode.
 * - **DARKEN**: the theme-tinted gradient only (no blur).
 * - **OFF**: no effect.
 *
 * ## Z-order (important for BLURRY)
 *
 * Draw order (bottom to top):
 * 1. [content] — tagged as `hazeSource` in BLURRY mode; this is what gets
 *    blurred.
 * 2. The top and bottom [EdgeScrim]s, each with two sub-layers:
 *    a. `hazeEffect`: samples color from layer 1 and blurs it progressively;
 *    b. gradient: a semi-transparent theme (surface) tint that strengthens
 *       toward the edge, adding a matte/darkening.
 * 3. [topOverlay] — a slot for elements that must stay crisp above the scrim
 *    (e.g. a transparent `TopAppBar`). Drawn after the scrim, so it is not
 *    blurred.
 *
 * @param mode the edge effect to apply.
 * @param modifier the modifier applied to the root box.
 * @param topExtraHeight extra height for the top scrim strip beyond the status
 *   bar (typically the `TopAppBar` height).
 * @param topOverlay a slot drawn last, above the scrim.
 * @param content the screen content, receiving the modifier to apply as the
 *   blur source.
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
        val sourceModifier =
            if (mode == EdgeEffectMode.BLURRY) Modifier.hazeSource(hazeState) else Modifier
        content(sourceModifier)

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

        topOverlay?.invoke(this)
    }
}

/** Maximum blur radius at the very screen edge, fading to 0 toward the content. */
private val BlurryBlurRadius = 8.dp

/**
 * Maximum tint-gradient alpha at the edge in BLURRY mode — enough to add
 * contrast for `TopAppBar` text over bright/dark photos, without fully hiding
 * the photo (the blur remains visible).
 */
private const val BlurryGradientAlpha = 0.22f

/**
 * Gradient alpha in DARKEN mode (no blur); denser, since the gradient is the
 * only source of contrast.
 */
private const val DarkenGradientAlpha = 0.55f

/**
 * A single edge scrim (top or bottom) rendered according to [mode].
 *
 * In BLURRY mode it stacks a progressive `hazeEffect` blur under a theme-tint
 * gradient that strengthens toward the edge; in DARKEN mode it draws the
 * gradient only; in OFF mode it draws nothing. The caller-provided [modifier]
 * sizes and positions the scrim, while the sub-layers use `matchParentSize` to
 * fill it without affecting measurement.
 *
 * @param mode the edge effect to apply.
 * @param hazeState the shared haze state sampling the content.
 * @param scrimColor the theme surface color used for the tint and background.
 * @param top whether this is the top scrim; controls the gradient direction.
 * @param modifier the modifier that sizes and positions the scrim.
 */
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
            Box(modifier = modifier) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .hazeEffect(state = hazeState) {
                            blurRadius = BlurryBlurRadius
                            backgroundColor = scrimColor
                            tints = listOf(HazeTint(scrimColor.copy(alpha = 0.15f)))
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
