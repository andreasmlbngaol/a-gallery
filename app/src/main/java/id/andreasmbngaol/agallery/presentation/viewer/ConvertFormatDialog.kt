package id.andreasmbngaol.agallery.presentation.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import id.andreasmbngaol.agallery.R
import id.andreasmbngaol.agallery.domain.model.conversion.ImageFormat
import kotlin.math.roundToInt

/**
 * "Convert format" dialog (1.5.0).
 *
 * - Choose the target format (the source format is disabled so it cannot convert
 *   to itself).
 * - The quality slider appears only for lossy formats (JPG/WEBP/HEIC); PNG is lossless.
 * - Choose the output: save a new file (original safe) or replace the original (original -> Trash).
 * - Notes are shown for transparency (black flatten) & HEIC (hardware-dependent).
 *
 * [onConfirm] is called with the target format, quality (1..100), and the delete-original flag.
 */
@Composable
fun ConvertFormatDialog(
    currentMime: String,
    onConfirm: (target: ImageFormat, quality: Int, deleteOriginal: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val currentFormat = ImageFormat.fromMime(currentMime)
    var target by remember {
        mutableStateOf(ImageFormat.entries.first { it != currentFormat })
    }
    var quality by remember { mutableStateOf(95f) }
    var deleteOriginal by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.convert_format_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.convert_format_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SectionLabelConvert(stringResource(R.string.convert_format_target))
                ImageFormat.entries.forEach { fmt ->
                    val isCurrent = fmt == currentFormat
                    val label = if (isCurrent) {
                        stringResource(R.string.convert_format_current, fmt.name)
                    } else {
                        fmt.name
                    }
                    RadioRowConvert(
                        label = label,
                        selected = target == fmt,
                        enabled = !isCurrent,
                        onSelect = { target = fmt },
                    )
                }

                if (target.lossy) {
                    SectionLabelConvert(
                        stringResource(R.string.convert_format_quality, quality.roundToInt()),
                    )
                    Slider(
                        value = quality,
                        onValueChange = { quality = it },
                        valueRange = 1f..100f,
                    )
                }

                SectionLabelConvert(stringResource(R.string.convert_format_output))
                RadioRowConvert(
                    label = stringResource(R.string.convert_format_output_keep),
                    selected = !deleteOriginal,
                    onSelect = { deleteOriginal = false },
                )
                RadioRowConvert(
                    label = stringResource(R.string.convert_format_output_replace),
                    selected = deleteOriginal,
                    onSelect = { deleteOriginal = true },
                )

                if (!target.supportsAlpha) {
                    NoteText(stringResource(R.string.convert_format_alpha_note))
                }
                if (target == ImageFormat.HEIC) {
                    NoteText(stringResource(R.string.convert_format_heic_note))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(target, quality.roundToInt(), deleteOriginal) },
                enabled = target != currentFormat,
            ) { Text(stringResource(R.string.action_convert)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Section header, made identical to SectionHeader in MediaDetailsSheet. */
@Composable
private fun SectionLabelConvert(text: String) {
    Spacer(Modifier.height(20.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun NoteText(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RadioRowConvert(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
