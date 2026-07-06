package id.andreasmbngaol.agallery.presentation.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import id.andreasmbngaol.agallery.R
import id.andreasmbngaol.agallery.core.ui.EdgeEffectTopBarScaffold
import id.andreasmbngaol.agallery.core.ui.FloatingTabBarHeight
import id.andreasmbngaol.agallery.core.ui.ScreenTopBarHeight
import id.andreasmbngaol.agallery.core.ui.isBlurryEdgeSupported
import id.andreasmbngaol.agallery.core.ui.isGlassSupported
import id.andreasmbngaol.agallery.core.ui.resolveComponentStyle
import id.andreasmbngaol.agallery.core.ui.resolveEdgeEffectMode
import id.andreasmbngaol.agallery.domain.model.ComponentStyle
import id.andreasmbngaol.agallery.domain.model.EdgeEffectMode
import id.andreasmbngaol.agallery.domain.model.MAX_GRID_COLUMNS
import id.andreasmbngaol.agallery.domain.model.PerformanceMode
import id.andreasmbngaol.agallery.domain.model.MIN_GRID_COLUMNS
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration.Companion.milliseconds

// ---- Tuning "frosted glass" segmented control ----
// Track: kaca tipis. Karena layar Settings latarnya rata (bukan foto), efek
// dibuat frosted/translucent, bukan refraction penuh seperti bar mengambang
// di atas grid (refraction butuh konten kaya di belakang biar kelihatan).
private const val TrackGlassAlpha = 0.4f
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
 * Satu pilihan mode efek tepi.label = teks pendek di segmen; deskripsi
 * panjang ada di [edgeEffectDescription] (helper text di bawah kontrol).
 */
private data class EdgeEffectChoice(
    val mode: EdgeEffectMode,
    @StringRes val labelRes: Int,
)

// Urutan sengaja ringan -> berat (kiri -> kanan), konsisten dengan
// PerformanceMode (Low -> High): makin ke kanan = makin "berat"/intens.
private val EdgeEffectChoices = listOf(
    EdgeEffectChoice(EdgeEffectMode.OFF, R.string.edge_off),
    EdgeEffectChoice(EdgeEffectMode.DARKEN, R.string.edge_darken),
    EdgeEffectChoice(EdgeEffectMode.BLURRY, R.string.edge_blurry),
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
    // Pakai nilai TER-RESOLVE (bukan mentah) supaya opsi yang tak didukung
    // perangkat tak pernah tampak "terpilih" padahal sebenarnya jatuh ke fallback.
    val shownSelection = resolveEdgeEffectMode(chosenMode, Build.VERSION.SDK_INT)
    val blurrySupported = isBlurryEdgeSupported()
    val shownComponentStyle = resolveComponentStyle(componentStyle, Build.VERSION.SDK_INT)
    val glassSupported = isGlassSupported()

    val safeDrawing = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current

    // Topbar "Settings" seragam ala Gallery. Efek tepi (Off/Darken/Blurry) yang
    // dipilih user ikut diterapkan ke area topbar lewat SystemBarScrim.
    EdgeEffectTopBarScaffold(
        title = stringResource(R.string.tab_settings),
        edgeEffectMode = chosenMode,
    ) { contentModifier ->
    Column(
        modifier = contentModifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = safeDrawing.calculateStartPadding(layoutDirection) + 16.dp,
                end = safeDrawing.calculateEndPadding(layoutDirection) + 16.dp,
                // Turun di bawah topbar (status bar + tinggi topbar).
                top = safeDrawing.calculateTopPadding() + ScreenTopBarHeight + 8.dp,
                // Ruang ekstra di bawah supaya item terakhir tak ketutup floating
                // nav bar (Settings kini jadi salah satu tab di dalam scaffold).
                bottom = safeDrawing.calculateBottomPadding() + 12.dp + FloatingTabBarHeight,
            ),
    ) {
        // ===== Section: Gallery =====
        SettingsSectionHeader(title = stringResource(R.string.tab_gallery))
        SettingsCard {
            SettingsItem(
                title = stringResource(R.string.settings_grid_columns_title),
                description = stringResource(R.string.settings_grid_columns_desc),
            ) {
                GridColumnsSegmentedControl(
                    selected = gridColumns,
                    onSelect = onSelectGridColumns,
                )
            }
        }

        Spacer(Modifier.height(SettingsSectionGap))

        // ===== Section: Performance =====
        SettingsSectionHeader(title = stringResource(R.string.settings_section_performance))
        SettingsCard {
            SettingsItem(
                title = stringResource(R.string.settings_loading_title),
                description = stringResource(R.string.settings_loading_desc),
                helperText = performanceModeDescription(performanceMode),
                selectionKey = performanceMode,
            ) {
                PerformanceModeSegmentedControl(
                    selected = performanceMode,
                    onSelect = onSelectPerformanceMode,
                )
            }
        }

        Spacer(Modifier.height(SettingsSectionGap))

        // ===== Section: Appearance (SATU island, dua setting sekategori) =====
        SettingsSectionHeader(title = stringResource(R.string.settings_section_appearance))
        SettingsCard {
            SettingsItem(
                title = stringResource(R.string.settings_component_style_title),
                description = stringResource(R.string.settings_component_style_desc),
                helperText = componentStyleDescription(shownComponentStyle, glassSupported),
                selectionKey = shownComponentStyle,
                footnote = if (!glassSupported) {
                    stringResource(R.string.settings_glass_unavailable)
                } else {
                    null
                },
            ) {
                ComponentStyleSegmentedControl(
                    selected = shownComponentStyle,
                    onSelect = onSelectComponentStyle,
                    glassSupported = glassSupported,
                )
            }

            SettingsItemDivider()

            SettingsItem(
                title = stringResource(R.string.settings_edge_effect_title),
                description = stringResource(R.string.settings_edge_effect_desc),
                helperText = edgeEffectDescription(shownSelection, blurrySupported),
                selectionKey = shownSelection,
                footnote = if (!blurrySupported) {
                    stringResource(R.string.settings_blurry_unavailable)
                } else {
                    null
                },
            ) {
                EdgeEffectSegmentedControl(
                    selected = shownSelection,
                    onSelect = onSelectMode,
                    blurrySupported = blurrySupported,
                )
            }
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

/** Durasi helper text (penjelasan pilihan) tampil setelah user mengubah pilihan. */
private const val HelperVisibleMillis = 4000L

/**
 * Satu baris setting di dalam kartu: judul, deskripsi singkat, kontrol, lalu
 * helper text opsional ([helperText]) yang menjelaskan PILIHAN aktif.
 *
 * Helper text kini TRANSIEN: hanya muncul beberapa detik ketika user MENGUBAH
 * pilihan ([selectionKey] berubah), lalu otomatis menghilang. Tidak tampil saat
 * layar pertama dibuka sehingga daftar setting tetap ringkas.
 */
@Composable
private fun SettingsItem(
    title: String,
    description: String,
    helperText: String? = null,
    selectionKey: Any? = null,
    footnote: String? = null,
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
        // Catatan PERMANEN (mis. alasan opsi ter-nonaktif karena batas OS).
        if (footnote != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = footnote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
        if (helperText != null) {
            TransientHelperText(text = helperText, selectionKey = selectionKey)
        }
    }
}

/**
 * Helper text yang muncul sesaat (fade + expand) tiap kali [selectionKey]
 * berubah, lalu hilang sendiri setelah [HelperVisibleMillis]. Sengaja TIDAK
 * tampil pada komposisi pertama (hanya reaksi atas perubahan pilihan user).
 */
@Composable
private fun TransientHelperText(text: String, selectionKey: Any?) {
    var visible by remember { mutableStateOf(false) }
    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(selectionKey) {
        if (!initialized) {
            initialized = true
            return@LaunchedEffect
        }
        visible = true
        delay(HelperVisibleMillis.milliseconds)
        visible = false
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + expandVertically(tween(200)),
        exit = fadeOut(tween(200)) + shrinkVertically(tween(200)),
    ) {
        Column {
            Spacer(Modifier.height(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Garis pemisah tipis antar setting DI DALAM satu island (mis. Component style
 * <-> Screen edge effect di kategori Appearance).
 */
@Composable
private fun SettingsItemDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
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
    blurrySupported: Boolean,
) {
    SegmentedGlassTrack {
        EdgeEffectChoices.forEach { choice ->
            // BLURRY butuh API 32; di bawah itu di-nonaktifkan (bukan disembunyikan).
            val enabled = choice.mode != EdgeEffectMode.BLURRY || blurrySupported
            SegmentedGlassItem(
                label = stringResource(choice.labelRes),
                selected = selected == choice.mode,
                onClick = { onSelect(choice.mode) },
                enabled = enabled,
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
 * Satu pilihan mode performa. label = teks pendek di segmen; penjelasan
 * panjang ada di [performanceModeDescription] (helper text di bawah kontrol).
 */
private data class PerformanceChoice(
    val mode: PerformanceMode,
    @StringRes val labelRes: Int,
)

private val PerformanceChoices = listOf(
    PerformanceChoice(PerformanceMode.LOW, R.string.perf_low),
    PerformanceChoice(PerformanceMode.BALANCED, R.string.perf_balanced),
    PerformanceChoice(PerformanceMode.HIGH, R.string.perf_high),
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
                label = stringResource(choice.labelRes),
                selected = selected == choice.mode,
                onClick = { onSelect(choice.mode) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun performanceModeDescription(mode: PerformanceMode): String = when (mode) {
    PerformanceMode.LOW -> stringResource(R.string.perf_desc_low)
    PerformanceMode.BALANCED -> stringResource(R.string.perf_desc_balanced)
    PerformanceMode.HIGH -> stringResource(R.string.perf_desc_high)
}

private data class ComponentStyleChoice(
    val style: ComponentStyle,
    @StringRes val labelRes: Int,
)

// Urutan ringan -> berat (kiri -> kanan), konsisten dgn PerformanceMode.
private val ComponentStyleChoices = listOf(
    ComponentStyleChoice(ComponentStyle.SOLID, R.string.style_solid),
    ComponentStyleChoice(ComponentStyle.FROSTED, R.string.style_frosted),
    ComponentStyleChoice(ComponentStyle.GLASS, R.string.style_glass),
)

/** Segmented control gaya komponen (Solid/Frosted/Glass), gaya frosted glass. */
@Composable
private fun ComponentStyleSegmentedControl(
    selected: ComponentStyle,
    onSelect: (ComponentStyle) -> Unit,
    glassSupported: Boolean,
) {
    SegmentedGlassTrack {
        ComponentStyleChoices.forEach { choice ->
            // GLASS butuh API 33; di bawah itu di-nonaktifkan (bukan disembunyikan).
            val enabled = choice.style != ComponentStyle.GLASS || glassSupported
            SegmentedGlassItem(
                label = stringResource(choice.labelRes),
                selected = selected == choice.style,
                onClick = { onSelect(choice.style) },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun componentStyleDescription(
    style: ComponentStyle,
    glassSupported: Boolean,
): String = when (style) {
    ComponentStyle.SOLID -> stringResource(R.string.style_desc_solid)
    ComponentStyle.FROSTED -> stringResource(R.string.style_desc_frosted)
    ComponentStyle.GLASS -> if (glassSupported) {
        stringResource(R.string.style_desc_glass)
    } else {
        stringResource(R.string.style_desc_glass_unsupported)
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
    enabled: Boolean = true,
) {
    // Chip terpilih memakai secondaryContainer (warna solid, bukan sekadar
    // surface transparan) supaya KONTRAS-nya jelas dgn track di light mode.
    // Sebelumnya selected & track sama-sama surfaceContainerHighest -> nyaris
    // tak terlihat mana yg dipilih terutama di light mode.
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
            // Ter-nonaktif: teks diredupkan (tak bisa dipilih).
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

@Composable
private fun edgeEffectDescription(
    mode: EdgeEffectMode,
    blurrySupported: Boolean,
): String = when (mode) {
    EdgeEffectMode.BLURRY -> if (blurrySupported) {
        stringResource(R.string.edge_desc_blurry)
    } else {
        stringResource(R.string.edge_desc_blurry_unsupported)
    }
    EdgeEffectMode.DARKEN -> stringResource(R.string.edge_desc_darken)
    EdgeEffectMode.OFF -> stringResource(R.string.edge_desc_off)
}
