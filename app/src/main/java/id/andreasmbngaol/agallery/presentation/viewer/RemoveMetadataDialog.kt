package id.andreasmbngaol.agallery.presentation.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import id.andreasmbngaol.agallery.domain.model.metadata.MetadataCategory

/**
 * Dialog "Hapus metadata" (1.4.0).
 *
 * - User pilih apa yang mau dibuang: Lokasi, Kamera, atau Semua (selektif).
 *   Kalau "Semua" dicentang, pilihan lain ikut ke-nonaktif (sudah tercakup).
 * - User pilih output: timpa file asli, atau simpan salinan bersih.
 *
 * [onConfirm] dipanggil dengan set kategori final + flag saveAsCopy.
 */
@Composable
fun RemoveMetadataDialog(
    onConfirm: (categories: Set<MetadataCategory>, saveAsCopy: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var location by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf(false) }
    var all by remember { mutableStateOf(true) }
    var saveAsCopy by remember { mutableStateOf(false) }

    val selected: Set<MetadataCategory> = when {
        all -> setOf(MetadataCategory.ALL)
        else -> buildSet {
            if (location) add(MetadataCategory.LOCATION)
            if (camera) add(MetadataCategory.CAMERA)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.remove_metadata_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.remove_metadata_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SectionLabel(stringResource(R.string.remove_metadata_what))
                CheckRow(
                    label = stringResource(R.string.remove_metadata_cat_all),
                    checked = all,
                    onCheckedChange = { all = it },
                )
                CheckRow(
                    label = stringResource(R.string.remove_metadata_cat_location),
                    checked = location && !all,
                    enabled = !all,
                    onCheckedChange = { location = it },
                )
                CheckRow(
                    label = stringResource(R.string.remove_metadata_cat_camera),
                    checked = camera && !all,
                    enabled = !all,
                    onCheckedChange = { camera = it },
                )

                SectionLabel(stringResource(R.string.remove_metadata_output_label))
                RadioRow(
                    label = stringResource(R.string.remove_metadata_output_overwrite),
                    selected = !saveAsCopy,
                    onSelect = { saveAsCopy = false },
                )
                RadioRow(
                    label = stringResource(R.string.remove_metadata_output_copy),
                    selected = saveAsCopy,
                    onSelect = { saveAsCopy = true },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selected, saveAsCopy) },
                enabled = selected.isNotEmpty(),
            ) { Text(stringResource(R.string.action_remove)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Header section, dibuat identik dgn SectionHeader di MediaDetailsSheet. */
@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(20.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun CheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RadioRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
