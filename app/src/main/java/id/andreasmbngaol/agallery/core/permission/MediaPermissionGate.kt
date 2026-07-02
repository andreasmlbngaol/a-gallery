package id.andreasmbngaol.agallery.core.permission

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState

/**
 * Gate izin media. Menampilkan [content] hanya jika ada akses media.
 *
 * Catatan: di Android 14+ akses "sebagian" (user memilih beberapa foto) hanya
 * meng-grant READ_MEDIA_VISUAL_USER_SELECTED. Karena itu kita anggap sudah punya
 * akses jika minimal SATU permission media granted, bukan harus semua.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MediaPermissionGate(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberMultiplePermissionsState(MediaPermissions.required())
    val hasAccess = state.permissions.any { it.status.isGranted }

    if (hasAccess) {
        content()
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "AGallery needs access to your photos & videos to show your gallery.",
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { state.launchMultiplePermissionRequest() }) {
                Text("Grant permission")
            }
        }
    }
}
