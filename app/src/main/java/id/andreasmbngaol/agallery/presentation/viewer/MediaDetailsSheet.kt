package id.andreasmbngaol.agallery.presentation.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import id.andreasmbngaol.agallery.domain.model.MediaDetails
import id.andreasmbngaol.agallery.domain.model.MediaItem
import id.andreasmbngaol.agallery.domain.model.MediaType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Bottom sheet berisi metadata media, dibuka lewat swipe ke atas di viewer.
 *
 * Sebagian data (nama, tipe, durasi, tanggal, folder) sudah ada di [item];
 * sisanya (ukuran file, dimensi piksel, path) dimuat on-demand lewat
 * [loadDetails] biar query grid tetap ringan. Berlaku untuk foto & video.
 *
 * Selama data detail masih dimuat, field yang belum ada ditampilkan sebagai
 * "\u2026"; kalau gagal/nihil jadi "Unknown".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailsSheet(
    item: MediaItem,
    loadDetails: suspend (String) -> MediaDetails?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // key(item.id) -> reset state kalau sheet dipakai ulang utk item berbeda.
    var details by remember(item.id) { mutableStateOf<MediaDetails?>(null) }
    var loading by remember(item.id) { mutableStateOf(true) }

    LaunchedEffect(item.id) {
        loading = true
        details = loadDetails(item.uri)
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // Scrim transparan supaya foto/video yang bergeser ke atas tetap terlihat.
        scrimColor = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Details",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(16.dp))

            // Placeholder sementara data detail masih dimuat vs sudah gagal.
            val placeholder = if (loading) "\u2026" else "Unknown"

            DetailRow(label = "Name", value = item.displayName.ifEmpty { placeholder })
            DetailRow(label = "Type", value = item.mimeType.ifEmpty { placeholder })
            if (item.type == MediaType.VIDEO && item.durationMs > 0L) {
                DetailRow(label = "Duration", value = formatDuration(item.durationMs))
            }
            DetailRow(
                label = "Size",
                value = details?.let { formatFileSize(it.sizeBytes) } ?: placeholder,
            )
            DetailRow(
                label = "Dimensions",
                value = details
                    ?.takeIf { it.width > 0 && it.height > 0 }
                    ?.let { "${it.width} \u00d7 ${it.height}" }
                    ?: placeholder,
            )
            DetailRow(
                label = "Date",
                value = formatDate(item.dateAddedEpochSeconds),
            )
            DetailRow(
                label = "Folder",
                value = item.bucketName
                    .ifEmpty { details?.relativePath.orEmpty() }
                    .ifEmpty { placeholder },
            )
        }
    }
}

/** Satu baris metadata: label kiri (lebar tetap) + value kanan (mengisi sisa). */
@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.width(110.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Byte -> string ramah baca (B/KB/MB/GB/TB, basis 1024). */
private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "Unknown"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.size - 1) {
        value /= 1024.0
        unitIndex++
    }
    return if (unitIndex == 0) {
        "$bytes B"
    } else {
        String.format(Locale.US, "%.1f %s", value, units[unitIndex])
    }
}

/** Epoch detik -> tanggal-waktu lokal ramah baca. */
private fun formatDate(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return "Unknown"
    val formatter = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
    return Instant.ofEpochSecond(epochSeconds)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}

/** Milidetik -> "m:ss" atau "h:mm:ss". */
private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
