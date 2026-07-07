package id.andreasmbngaol.agallery.presentation.tools

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.QrCode
import id.andreasmbngaol.agallery.R
import id.andreasmbngaol.agallery.core.ui.EdgeEffectTopBarScaffold
import id.andreasmbngaol.agallery.core.ui.FloatingTabBarHeight
import id.andreasmbngaol.agallery.core.ui.ScreenTopBarHeight
import id.andreasmbngaol.agallery.domain.model.settings.EdgeEffectMode

/**
 * The "Tools" tab: a hub of non-gallery utilities (e.g. QR Code Generator). A
 * 2-column grid of tool cards. Each tool will eventually get its own screen; for
 * now, tools that are not ready are shown as cards with a "Coming soon" badge and
 * CANNOT be tapped (disabled).
 *
 * The tool registry is DECLARATIVE (see [toolRegistry]) so adding a new tool only
 * requires adding one entry + wiring up its `enabled`/`onClick` later, without
 * touching the layout.
 *
 * The top bar & edge effect follow the other tabs' pattern via [EdgeEffectTopBarScaffold].
 */
@Composable
fun ToolsScreen(
    edgeEffectMode: EdgeEffectMode?,
    onOpenQrGenerator: () -> Unit = {},
) {
    val layoutDirection = LocalLayoutDirection.current
    val safeDrawing = WindowInsets.safeDrawing.asPaddingValues()
    val tools = remember(onOpenQrGenerator) { toolRegistry(onOpenQrGenerator) }

    EdgeEffectTopBarScaffold(
        title = stringResource(R.string.tab_tools),
        edgeEffectMode = edgeEffectMode,
    ) { contentModifier ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = contentModifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = safeDrawing.calculateStartPadding(layoutDirection) + 16.dp,
                end = safeDrawing.calculateEndPadding(layoutDirection) + 16.dp,
                top = safeDrawing.calculateTopPadding() + ScreenTopBarHeight + 8.dp,
                bottom = safeDrawing.calculateBottomPadding() + 12.dp + FloatingTabBarHeight,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ToolsSectionHeader(stringResource(R.string.tools_section_utilities))
            }
            items(tools) { tool ->
                ToolCard(tool = tool)
            }
        }
    }
}

/**
 * Definition of a single tool in the hub. [enabled]=false -> the card shows a
 * "Coming soon" badge & cannot be tapped. Later, when the tool is ready, set
 * enabled=true and fill in [onClick] (e.g. navigate to the tool screen).
 */
private data class ToolEntry(
    val icon: ImageVector,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val enabled: Boolean,
    val onClick: () -> Unit = {},
)

/**
 * Tool list (declarative). For now only the QR Code Generator, as a disabled
 * placeholder -- its functionality follows once the QR design is finalized. To
 * add a new tool, just add a [ToolEntry] here.
 */
private fun toolRegistry(
    onOpenQrGenerator: () -> Unit,
): List<ToolEntry> = listOf(
    ToolEntry(
        icon = PhosphorIcons.Bold.QrCode,
        titleRes = R.string.tool_qr_generator_title,
        descRes = R.string.tool_qr_generator_desc,
        enabled = true,
        onClick = onOpenQrGenerator,
    ),
)

/** Small category label above the grid, same style as the Settings section header. */
@Composable
private fun ToolsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp),
    )
}

/**
 * A single tool card: an icon in a colored "chip", title, 1-2 line description,
 * plus a "Coming soon" badge when not yet active. Disabled cards are dimmed
 * (alpha) & do not accept clicks.
 */
@Composable
private fun ToolCard(tool: ToolEntry) {
    val enabled = tool.enabled
    val contentAlpha = if (enabled) 1f else 0.45f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(enabled = enabled, onClick = tool.onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = contentAlpha),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = contentAlpha),
                    modifier = Modifier.size(24.dp),
                )
            }
            if (!enabled) {
                Spacer(Modifier.weight(1f))
                ComingSoonBadge()
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(tool.titleRes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(tool.descRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
            maxLines = 2,
        )
    }
}

/** Small "Coming soon" badge for tools that are not yet active. */
@Composable
private fun ComingSoonBadge() {
    Text(
        text = stringResource(R.string.tools_coming_soon),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
