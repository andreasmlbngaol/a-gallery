package id.andreasmbngaol.agallery.presentation.settings

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import id.andreasmbngaol.agallery.core.ui.FloatingTabBarHeight
import id.andreasmbngaol.agallery.core.ui.isFrostedSupported
import id.andreasmbngaol.agallery.core.ui.isGlassSupported
import id.andreasmbngaol.agallery.core.ui.resolveComponentStyle
import id.andreasmbngaol.agallery.core.ui.resolveEdgeEffectMode
import id.andreasmbngaol.agallery.domain.model.ComponentStyle
import id.andreasmbngaol.agallery.domain.model.EdgeEffectMode
import id.andreasmbngaol.agallery.domain.model.MAX_GRID_COLUMNS
import id.andreasmbngaol.agallery.domain.model.PerformanceMode
import id.andreasmbngaol.agallery.domain.model.MIN_GRID_COLUMNS
import org.koin.androidx.compose.koinViewModel

// ---- Tuning "frosted glass" segmented control ----
// Track: kaca tipis. Karena layar Settings latarnya rata (bukan foto), efek
// dibuat frosted/translucent, bukan refraction penuh seperti bar mengambang
// di atas grid (refraction butuh konten kaya di belakang biar kelihatan).
private const val TrackGlassAlpha = 0.4f
// Chip terpilih: kaca lebih pekat -> penanda selected sesuai bahasa glass.
private const val SelectedChipAlpha = 0.9f
private val SegmentedControlHeight = 48.dp
private val SegmentedTrackRadius = 24.dp
private val SegmentedChipRadius = 20.dp

// ---- Kartu section bergaya system settings (biar pemisahan jelas) ----
private val SettingsCardRadius = 20.dp
private val SettingsCardPadding = 16.dp
private val SettingsSectionGap = 24.dp

// Opsi jumlah kolom grid = [MIN_GRID_COLUMNS..MAX_GRID_COLUMNS] (3..5).
private val GridColumnChoices: List<Int> = (MIN_GRID_COLUMNS..MAX_GRID_COLUMNS).toList()

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    SettingsContent(
        chosenMode = settings.edgeEffectMode,
        onSelectMode = viewModel::onSelectEdgeEffect,
        componentStyle = settings.componentStyle,
        onSelectComponentStyle = viewModel::onSelectComponentStyle,
        gridColumns = settings.gridColumns,
        onSelectGridColumns = viewModel::onSelectGridColumns,
        performanceMode = settings.performanceMode,
        onSelectPerformanceMode = viewModel::onSelectPerformanceMode,
    )
}

/**
 * Satu pilihan mode efek tepi. [label] = teks pendek di segmen; deskripsi
 * panjang ada di [edgeEffectDescription] (helper text di bawah kontrol).
 */
private data class EdgeEffectChoice(
    val mode: EdgeEffectMode,
    val label: String,
)

// Urutan sengaja ringan -> berat (kiri -> kanan), konsisten dengan
// PerformanceMode (Low -> High): makin ke kanan = makin "berat"/intens.
private val EdgeEffectChoices = listOf(
    EdgeEffectChoice(EdgeEffectMode.OFF, "Off"),
    EdgeEffectChoice(EdgeEffectMode.GRADIENT, "Gradient"),
    EdgeEffectChoice(EdgeEffectMode.FROSTED, "Frosted"),
)

@Composable
private fun SettingsContent(
    chosenMode: EdgeEffectMode?,
    onSelectMode: (EdgeEffectMode) -> Unit,
    componentStyle: ComponentStyle?,
    onSelectComponentStyle: (ComponentStyle) -> Unit,
    gridColumns: Int,
    onSelectGridColumns: (Int) -> Unit,
    performanceMode: PerformanceMode,
    onSelectPerformanceMode: (PerformanceMode) -> Unit,
) {
    val defaultMode = remember { resolveEdgeEffectMode(null, Build.VERSION.SDK_INT) }
    val shownSelection = chosenMode ?: defaultMode
    val frostedSupported = isFrostedSupported()
    val defaultComponentStyle = remember { resolveComponentStyle(null, Build.VERSION.SDK_INT) }
    val shownComponentStyle = componentStyle ?: defaultComponentStyle
    val glassSupported = isGlassSupported()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .verticalScroll(rememberScrollState())
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                // Ruang ekstra di bawah supaya item terakhir tak ketutup floating
                // nav bar (Settings kini jadi salah satu tab di dalam scaffold).
                bottom = 12.dp + FloatingTabBarHeight,
            ),
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))

        // ===== Section: Gallery =====
        SettingsSectionHeader(title = "Gallery")
        SettingsCard {
            SettingsItem(
                title = "Grid columns",
                description = "How many photos per row in the gallery grid.",
            ) {
                GridColumnsSegmentedControl(
                    selected = gridColumns,
                    onSelect = onSelectGridColumns,
                )
            }
        }

        Spacer(Modifier.height(SettingsSectionGap))

        // ===== Section: Performance =====
        SettingsSectionHeader(title = "Performance")
        SettingsCard {
            SettingsItem(
                title = "Loading behavior",
                description = "How aggressively thumbnails are preloaded while scrolling.",
                helperText = performanceModeDescription(performanceMode),
            ) {
                PerformanceModeSegmentedControl(
                    selected = performanceMode,
                    onSelect = onSelectPerformanceMode,
                )
            }
        }

        Spacer(Modifier.height(SettingsSectionGap))

        // ===== Section: Appearance =====
        SettingsSectionHeader(title = "Appearance")
        SettingsCard {
            SettingsItem(
                title = "Component style",
                description = "Look of the floating bars, buttons & viewer islands across the app.",
                helperText = componentStyleDescription(shownComponentStyle, glassSupported),
            ) {
                ComponentStyleSegmentedControl(
                    selected = shownComponentStyle,
                    onSelect = onSelectComponentStyle,
                )
            }
        }

        Spacer(Modifier.height(SettingsSectionGap))

        SettingsCard {
            SettingsItem(
                title = "Screen edge effect",
                description = "Effect over the status bar & navigation bar when photos scroll behind them.",
                helperText = edgeEffectDescription(shownSelection, frostedSupported),
            ) {
                EdgeEffectSegmentedControl(
                    selected = shownSelection,
                    onSelect = onSelectMode,
                )
            }
        }
    }
}

// ---- Building block section bergaya system settings ----

/**
 * Label kategori kecil di atas tiap kartu. Pakai warna aksen + typography label
 * biar kebaca sebagai pemisah kelompok, mirip header di system settings.
 */
@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

/**
 * Kartu pembungkus satu section: rounded container solid (bukan kaca) supaya
 * batas antar kelompok setting tegas, seperti kartu di system settings.
 */
@Composable
private fun SettingsCard(
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SettingsCardRadius))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(SettingsCardPadding),
        content = content,
    )
}

/**
 * Satu baris setting di dalam kartu: judul, deskripsi, kontrol, lalu helper
 * text opsional ([helperText]).
 */
@Composable
private fun SettingsItem(
    title: String,
    description: String,
    helperText: String? = null,
    control: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(2.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        control()
        if (helperText != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = helperText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Segmented control bergaya frosted glass (menggantikan M3 button group).
 * Track kaca tipis + chip terpilih yang lebih pekat, konsisten dengan pill
 * tab & tombol mengambang di galeri.
 */
@Composable
private fun EdgeEffectSegmentedControl(
    selected: EdgeEffectMode,
    onSelect: (EdgeEffectMode) -> Unit,
) {
    SegmentedGlassTrack {
        EdgeEffectChoices.forEach { choice ->
            SegmentedGlassItem(
                label = choice.label,
                selected = selected == choice.mode,
                onClick = { onSelect(choice.mode) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Segmented control untuk jumlah kolom grid (3/4/5), gaya frosted glass. */
@Composable
private fun GridColumnsSegmentedControl(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    SegmentedGlassTrack {
        GridColumnChoices.forEach { count ->
            SegmentedGlassItem(
                label = count.toString(),
                selected = selected == count,
                onClick = { onSelect(count) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Satu pilihan mode performa. [label] = teks pendek di segmen; penjelasan
 * panjang ada di [performanceModeDescription] (helper text di bawah kontrol).
 */
private data class PerformanceChoice(
    val mode: PerformanceMode,
    val label: String,
)

private val PerformanceChoices = listOf(
    PerformanceChoice(PerformanceMode.LOW, "Low"),
    PerformanceChoice(PerformanceMode.BALANCED, "Balanced"),
    PerformanceChoice(PerformanceMode.HIGH, "High"),
)

/** Segmented control untuk mode performa (Low/Balanced/High), gaya frosted glass. */
@Composable
private fun PerformanceModeSegmentedControl(
    selected: PerformanceMode,
    onSelect: (PerformanceMode) -> Unit,
) {
    SegmentedGlassTrack {
        PerformanceChoices.forEach { choice ->
            SegmentedGlassItem(
                label = choice.label,
                selected = selected == choice.mode,
                onClick = { onSelect(choice.mode) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun performanceModeDescription(mode: PerformanceMode): String = when (mode) {
    PerformanceMode.LOW ->
        "Lightest on RAM, CPU & GPU. Thumbnails load only as you reach them, so scrolling stays cheap on low-end devices \u2014 images may pop in slightly later."
    PerformanceMode.BALANCED ->
        "Balanced RAM, CPU & GPU use. Preloads a few rows ahead for smooth scrolling without decoding too much in the background."
    PerformanceMode.HIGH ->
        "Uses more RAM (bigger cache) plus more CPU & GPU to decode many rows ahead (and above) so scrolling rarely waits. Best on powerful devices; cache size changes apply after restart."
}

private data class ComponentStyleChoice(
    val style: ComponentStyle,
    val label: String,
)

// Urutan ringan -> berat (kiri -> kanan), konsisten dgn PerformanceMode.
private val ComponentStyleChoices = listOf(
    ComponentStyleChoice(ComponentStyle.SOLID, "Solid"),
    ComponentStyleChoice(ComponentStyle.FROSTED, "Frosted"),
    ComponentStyleChoice(ComponentStyle.GLASS, "Glass"),
)

/** Segmented control gaya komponen (Solid/Frosted/Glass), gaya frosted glass. */
@Composable
private fun ComponentStyleSegmentedControl(
    selected: ComponentStyle,
    onSelect: (ComponentStyle) -> Unit,
) {
    SegmentedGlassTrack {
        ComponentStyleChoices.forEach { choice ->
            SegmentedGlassItem(
                label = choice.label,
                selected = selected == choice.style,
                onClick = { onSelect(choice.style) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun componentStyleDescription(
    style: ComponentStyle,
    glassSupported: Boolean,
): String = when (style) {
    ComponentStyle.SOLID ->
        "Solid semi-transparent bars. Lightest \u2014 no blur, smoothest on any device."
    ComponentStyle.FROSTED ->
        "Frosted translucent glass. Smooth and still glassy \u2014 recommended for most devices."
    ComponentStyle.GLASS -> if (glassSupported) {
        "Live liquid glass that refracts the photo behind it. Looks best but is the HEAVIEST: it drives the GPU every frame and can cause lag on some devices."
    } else {
        "Needs Android 13+ \u2014 falls back to Frosted on this device."
    }
}

/** Track kaca yang membungkus segmen-segmen (dipakai ulang beberapa kontrol). */
@Composable
private fun SegmentedGlassTrack(
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SegmentedControlHeight)
            .clip(RoundedCornerShape(SegmentedTrackRadius))
            .background(
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = TrackGlassAlpha),
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun SegmentedGlassItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = SelectedChipAlpha)
        } else {
            Color.Transparent
        },
        animationSpec = tween(220),
        label = "segment-bg",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(220),
        label = "segment-fg",
    )
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(SegmentedChipRadius))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .semantics { role = Role.RadioButton },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private fun edgeEffectDescription(
    mode: EdgeEffectMode,
    frostedSupported: Boolean,
): String = when (mode) {
    EdgeEffectMode.FROSTED -> if (frostedSupported) {
        "Frosted glass behind the system bars."
    } else {
        "Needs Android 12+ \u2014 falls back to Gradient on this device."
    }
    EdgeEffectMode.GRADIENT -> "Subtle dark gradient, light on every device."
    EdgeEffectMode.OFF -> "No effect at the screen edges."
}
