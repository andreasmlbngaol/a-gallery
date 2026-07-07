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
 * Tab "Tools": hub berisi utilitas non-galeri (mis. QR Code Generator). Grid 2
 * kolom berisi kartu tool. Setiap tool nantinya punya layar sendiri; untuk saat
 * ini tool yang belum jadi ditampilkan sebagai kartu ber-badge "Segera hadir"
 * dan TIDAK bisa ditap (disabled).
 *
 * Registry tool bersifat DEKLARATIF (lihat [toolRegistry]) supaya menambah tool
 * baru cukup menambah satu entri + mengubah `enabled`/`onClick`-nya nanti,
 * tanpa mengutak-atik layout.
 *
 * Topbar & efek tepi mengikuti pola tab lain lewat [EdgeEffectTopBarScaffold].
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
                // Turun di bawah topbar (status bar + tinggi topbar).
                top = safeDrawing.calculateTopPadding() + ScreenTopBarHeight + 8.dp,
                // Ruang ekstra supaya kartu terakhir tak ketutup floating nav bar.
                bottom = safeDrawing.calculateBottomPadding() + 12.dp + FloatingTabBarHeight,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Section header "Utilities" membentang penuh (2 kolom).
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
 * Definisi satu tool di hub. [enabled]=false -> kartu tampil ber-badge "Segera
 * hadir" & tak bisa ditap. Nanti saat tool-nya jadi, set enabled=true dan isi
 * [onClick] (mis. navigasi ke layar tool).
 */
private data class ToolEntry(
    val icon: ImageVector,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val enabled: Boolean,
    val onClick: () -> Unit = {},
)

/**
 * Daftar tool (deklaratif). Untuk sekarang hanya QR Code Generator sebagai
 * placeholder disabled -- fungsionalitasnya menyusul setelah desain QR final.
 * Tambah tool baru cukup menambah [ToolEntry] di sini.
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

/** Label kategori kecil di atas grid, gaya sama dgn header section Settings. */
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
 * Kartu satu tool: ikon dalam "chip" warna, judul, deskripsi 1-2 baris, plus
 * badge "Segera hadir" bila belum aktif. Kartu disabled diredupkan (alpha) &
 * tak menerima klik.
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

/** Badge kecil "Segera hadir" untuk tool yang belum aktif. */
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
