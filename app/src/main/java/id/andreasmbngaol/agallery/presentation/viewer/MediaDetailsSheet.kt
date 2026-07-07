package id.andreasmbngaol.agallery.presentation.viewer

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.MapPin
import com.kyant.backdrop.Backdrop
import id.andreasmbngaol.agallery.R
import id.andreasmbngaol.agallery.domain.model.settings.ComponentStyle
import id.andreasmbngaol.agallery.domain.model.media.MediaDetails
import id.andreasmbngaol.agallery.domain.model.media.MediaItem
import id.andreasmbngaol.agallery.domain.model.media.MediaType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Bottom sheet with media metadata, opened via swipe up in the viewer
 * (used by both photos & videos).
 *
 * Some data (name, type, duration, date, folder) is already in [item];
 * the rest (size, dimensions, camera EXIF, location, video technicals) is loaded
 * on-demand via [loadDetails] so the grid query stays lightweight.
 *
 * Display rules (hybrid):
 * - CORE fields (General) always show; if empty -> "-" ("\u2026" while loading).
 * - OPTIONAL fields (Camera / Location / Video) only appear when data exists,
 *   and their section header also disappears when everything is empty.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailsSheet(
    item: MediaItem,
    loadDetails: suspend (String) -> MediaDetails?,
    onDismiss: () -> Unit,
    style: ComponentStyle,
    backdrop: Backdrop,
    onRemoveMetadata: (() -> Unit)? = null,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
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
        scrimColor = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.action_details),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(16.dp))

            val d = details
            val placeholder = if (loading) "\u2026" else "-"

            DetailRow(stringResource(R.string.detail_name), item.displayName.ifEmpty { placeholder })
            DetailRow(stringResource(R.string.detail_type), item.mimeType.ifEmpty { placeholder })
            if (item.type == MediaType.VIDEO && item.durationMs > 0L) {
                DetailRow(stringResource(R.string.detail_duration), formatDuration(item.durationMs))
            }
            DetailRow(
                stringResource(R.string.detail_size),
                d?.let { formatFileSize(it.sizeBytes) } ?: placeholder,
            )
            DetailRow(
                stringResource(R.string.detail_dimensions),
                d?.takeIf { it.width > 0 && it.height > 0 }
                    ?.let { stringResource(R.string.dimensions_format, it.width, it.height) }
                    ?: placeholder,
            )
            DetailRow(stringResource(R.string.detail_date), formatDate(item.dateAddedEpochSeconds))
            d?.dateTakenEpochSeconds?.let {
                DetailRow(stringResource(R.string.detail_date_taken), formatDate(it))
            }
            DetailRow(
                stringResource(R.string.detail_folder),
                item.bucketName.ifEmpty { d?.relativePath.orEmpty() }.ifEmpty { placeholder },
            )

            if (d != null) {
                val device = cameraDevice(d.cameraMake, d.cameraModel)
                val hasCamera = listOfNotNull(
                    device, d.aperture, d.shutterSpeed,
                    d.iso, d.focalLength, d.flashFired,
                ).isNotEmpty()
                if (hasCamera) {
                    SectionHeader(stringResource(R.string.detail_section_camera))
                    device?.let { DetailRow(stringResource(R.string.detail_camera_model), it) }
                    d.aperture?.let { DetailRow(stringResource(R.string.detail_aperture), it) }
                    d.shutterSpeed?.let { DetailRow(stringResource(R.string.detail_shutter_speed), it) }
                    d.iso?.let { DetailRow(stringResource(R.string.detail_iso), it) }
                    d.focalLength?.let { DetailRow(stringResource(R.string.detail_focal_length), it) }
                    d.flashFired?.let {
                        val v = if (it) stringResource(R.string.detail_flash_on)
                        else stringResource(R.string.detail_flash_off)
                        DetailRow(stringResource(R.string.detail_flash), v)
                    }
                }

                val lat = d.latitude
                val lng = d.longitude
                if (lat != null && lng != null) {
                    SectionHeader(stringResource(R.string.detail_section_location))
                    LocationRow(lat, lng)
                }

                val hasVideo = listOfNotNull(
                    d.frameRate, d.bitrate, d.videoCodec, d.audioCodec,
                ).isNotEmpty()
                if (hasVideo) {
                    SectionHeader(stringResource(R.string.detail_section_video))
                    d.frameRate?.let { DetailRow(stringResource(R.string.detail_frame_rate), it) }
                    d.bitrate?.let { DetailRow(stringResource(R.string.detail_bitrate), it) }
                    d.videoCodec?.let { DetailRow(stringResource(R.string.detail_video_codec), it) }
                    d.audioCodec?.let { DetailRow(stringResource(R.string.detail_audio_codec), it) }
                }
            }

            onRemoveMetadata?.let { onRemove ->
                Spacer(Modifier.height(24.dp))
                GlassActionButton(
                    text = stringResource(R.string.action_remove_metadata),
                    onClick = onRemove,
                    style = style,
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Small header to separate metadata sections. */
@Composable
private fun SectionHeader(text: String) {
    Spacer(Modifier.height(20.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(4.dp))
}

/** A single metadata row: left label (fixed width) + right value (fills the rest). */
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

/**
 * Location row: coordinates + a MapPin icon button that launches a map app via a
 * geo: intent (safe for a no-internet app — the app itself does not access the network).
 */
@Composable
private fun LocationRow(latitude: Double, longitude: Double) {
    val context = LocalContext.current
    val coords = String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.detail_coordinates),
            modifier = Modifier.width(110.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = coords,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        IconButton(onClick = {
            val geoUri = "geo:$latitude,$longitude?q=$latitude,$longitude".toUri()
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, geoUri)) }
        }) {
            Icon(
                imageVector = PhosphorIcons.Bold.MapPin,
                contentDescription = stringResource(R.string.action_open_in_maps),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Combine camera make + model into a friendly string, avoiding duplication. */
private fun cameraDevice(make: String?, model: String?): String? {
    val mk = make?.trim().orEmpty()
    val md = model?.trim().orEmpty()
    return when {
        mk.isEmpty() && md.isEmpty() -> null
        md.isEmpty() -> mk
        mk.isEmpty() -> md
        md.startsWith(mk, ignoreCase = true) -> md
        else -> "$mk $md"
    }
}

/** Bytes -> a human-readable string (B/KB/MB/GB/TB, base 1024). "-" when empty. */
private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "-"
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

/** Epoch seconds -> a human-readable local date-time. "-" when empty. */
private fun formatDate(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return "-"
    val formatter = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
    return Instant.ofEpochSecond(epochSeconds)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}

/** Milliseconds -> "m:ss" or "h:mm:ss". */
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
