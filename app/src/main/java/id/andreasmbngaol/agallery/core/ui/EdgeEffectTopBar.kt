package id.andreasmbngaol.agallery.core.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.andreasmbngaol.agallery.domain.model.EdgeEffectMode

/**
 * Shared visual height of the top bar across tab screens (Settings & Albums),
 * matched to the Gallery `TopAppBar` (72dp) for consistency.
 */
val ScreenTopBarHeight = 72.dp

/**
 * Uniform top-bar scaffold for tab screens (Settings & Albums) that mirrors the
 * Gallery top bar: a transparent 26sp SemiBold title drawn on top of
 * [SystemBarScrim].
 *
 * Because it reuses the same [SystemBarScrim], the edge effect chosen in
 * Settings (Off / Darken / Blurry) is also applied to this screen's top-bar and
 * navigation-bar areas — exactly as in Gallery.
 *
 * @param title the title text shown in the top bar.
 * @param edgeEffectMode the chosen edge effect, or `null` to use the default.
 * @param modifier the modifier applied to the scaffold.
 * @param content the screen content; it must apply the provided
 *   `contentModifier` to its (ideally scrollable) content so the BLURRY mode can
 *   sample its blur source, and pad the top by at least the status bar plus
 *   [ScreenTopBarHeight] so it is not covered by the top bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EdgeEffectTopBarScaffold(
    title: String,
    edgeEffectMode: EdgeEffectMode?,
    modifier: Modifier = Modifier,
    content: @Composable (contentModifier: Modifier) -> Unit,
) {
    val effectiveMode = rememberEffectiveEdgeEffectMode(edgeEffectMode)
    SystemBarScrim(
        mode = effectiveMode,
        modifier = modifier,
        topExtraHeight = ScreenTopBarHeight,
        topOverlay = { ScreenTopBarTitle(title) },
        content = content,
    )
}

/**
 * The transparent top-bar title, drawn crisply above the scrim with the same
 * style as Gallery.
 *
 * @param title the title text to display.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoxScope.ScreenTopBarTitle(title: String) {
    TopAppBar(
        modifier = Modifier.align(Alignment.TopCenter),
        title = {
            Text(
                text = title,
                style = LocalTextStyle.current.copy(
                    fontSize = 26.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.35f),
                        offset = Offset(0f, 1f),
                        blurRadius = 4f,
                    ),
                ),
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
        windowInsets = WindowInsets.statusBars,
    )
}
