package id.andreasmbngaol.agallery.core.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

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
        title = { Text("Delete permanently?") },
        text = {
            Text(
                if (count <= 1) {
                    "This item will be permanently deleted from your device. This can't be undone."
                } else {
                    "These $count items will be permanently deleted from your device. This can't be undone."
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
