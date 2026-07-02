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
import id.andreasmbngaol.agallery.core.ui.isFrostedSupported
import id.andreasmbngaol.agallery.core.ui.resolveEdgeEffectMode
import id.andreasmbngaol.agallery.domain.model.EdgeEffectMode
import id.andreasmbngaol.agallery.domain.model.MAX_GRID_COLUMNS
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
        gridColumns = settings.gridColumns,
        onSelectGridColumns = viewModel::onSelectGridColumns,
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

private val EdgeEffectChoices = listOf(
    EdgeEffectChoice(EdgeEffectMode.FROSTED, "Frosted"),
    EdgeEffectChoice(EdgeEffectMode.GRADIENT, "Gradient"),
    EdgeEffectChoice(EdgeEffectMode.OFF, "Off"),
)

@Composable
private fun SettingsContent(
    chosenMode: EdgeEffectMode?,
    onSelectMode: (EdgeEffectMode) -> Unit,
    gridColumns: Int,
    onSelectGridColumns: (Int) -> Unit,
) {
    val defaultMode = remember { resolveEdgeEffectMode(null, Build.VERSION.SDK_INT) }
    val shownSelection = chosenMode ?: defaultMode
    val frostedSupported = isFrostedSupported()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(20.dp))

        // --- Grid columns ---
        Text(text = "Grid columns", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(2.dp))
        Text(
            text = "How many photos per row in the gallery grid.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        GridColumnsSegmentedControl(
            selected = gridColumns,
            onSelect = onSelectGridColumns,
        )

        Spacer(Modifier.height(28.dp))

        // --- Screen edge effect ---
        Text(text = "Screen edge effect", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Effect over the status bar & navigation bar when photos scroll behind them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        EdgeEffectSegmentedControl(
            selected = shownSelection,
            onSelect = onSelectMode,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = edgeEffectDescription(shownSelection, frostedSupported),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
