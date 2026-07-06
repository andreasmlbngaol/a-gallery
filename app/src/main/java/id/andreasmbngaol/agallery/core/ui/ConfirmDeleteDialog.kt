package id.andreasmbngaol.agallery.core.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import id.andreasmbngaol.agallery.R

/**
 * Dialog konfirmasi hapus permanen in-app. Dipakai TERUTAMA saat All-files
 * access aktif: dialog konfirmasi sistem tidak lagi muncul, jadi tanpa ini
 * hapus jadi instan tanpa jaring pengaman.
 */
@Composable
fun ConfirmDeleteDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_permanently_title)) },
        text = {
            Text(
                if (count <= 1) {
                    stringResource(R.string.delete_permanently_single)
                } else {
                    stringResource(R.string.delete_permanently_multiple, count)
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
