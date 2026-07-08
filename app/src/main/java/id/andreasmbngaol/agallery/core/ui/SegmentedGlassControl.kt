package id.andreasmbngaol.agallery.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import id.andreasmbngaol.agallery.domain.model.settings.ComponentStyle

private const val LegacyTrackAlpha = 0.4f
private val SegmentedControlHeight = 48.dp
private val SegmentedTrackRadius = 24.dp
private val SegmentedChipRadius = 20.dp

// Per-style track opacity. SOLID paints an opaque tonal fill; FROSTED a
// translucent haze; GLASS a lighter, more see-through veil. We deliberately do
// NOT use the Kyant `drawBackdrop` liquid-glass here: this track lives *inside*
// its screen's `layerBackdrop` source layer, and sampling that same layer from a
// child crashes the RenderThread (native SIGSEGV) on API 33+. Tonal opacity is
// the safe way to still reflect the chosen surface style.
private const val FrostedTrackAlpha = 0.55f
private const val GlassTrackAlpha = 0.3f

/**
 * A segmented control track shared so every segmented choice in the app
 * (component style, edge effect, grid columns, performance mode, and the
 * Background Remover quality toggle) looks identical.
 *
 * When [componentStyle] is supplied the track's fill reflects that surface
 * style: [ComponentStyle.SOLID] is opaque, [ComponentStyle.FROSTED] is a
 * translucent haze, and [ComponentStyle.GLASS] is a lighter, more see-through
 * veil. When it is `null` (the default) the track keeps its original static
 * frosted look, so existing callers such as the Settings screen are unchanged.
 *
 * @param modifier the modifier applied to the track row.
 * @param componentStyle the resolved surface style, or `null` for the static look.
 * @param content the row of [SegmentedGlassItem]s.
 */
@Composable
fun SegmentedGlassTrack(
    modifier: Modifier = Modifier,
    componentStyle: ComponentStyle? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val trackColor = when (componentStyle) {
        null -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = LegacyTrackAlpha)
        ComponentStyle.SOLID -> MaterialTheme.colorScheme.surfaceContainerHighest
        ComponentStyle.FROSTED ->
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = FrostedTrackAlpha)
        ComponentStyle.GLASS ->
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = GlassTrackAlpha)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SegmentedControlHeight)
            .clip(RoundedCornerShape(SegmentedTrackRadius))
            .background(trackColor)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * One selectable segment inside a [SegmentedGlassTrack]. The selected chip fills
 * with the secondary container color; the transition is animated. When
 * [enabled] is false the segment is dimmed and not clickable.
 */
@Composable
fun SegmentedGlassItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = tween(220),
        label = "segment-bg",
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            selected -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(220),
        label = "segment-fg",
    )
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(SegmentedChipRadius))
            .background(backgroundColor)
            .then(
                if (enabled) Modifier.clickable(onClick = onClick) else Modifier,
            )
            .semantics {
                role = Role.RadioButton
                if (!enabled) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
