package id.andreasmbngaol.agallery.core.permission

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.FolderSimple
import com.adamglin.phosphoricons.regular.Image
import com.adamglin.phosphoricons.regular.WarningCircle

/**
 * Gate izin WAJIB yang membungkus SELURUH aplikasi (dipasang di MainActivity).
 * Dijalankan berurutan tiap app dibuka:
 *
 * 1. **Izin media (foto & video)** — TIDAK langsung memunculkan dialog sistem;
 *    ditampilkan dulu layar penjelasan + tombol "Allow access" supaya user
 *    paham konteksnya & tidak asal menolak. Setelah ditekan barulah dialog izin
 *    standar ("Allow all / Select photos / Don't allow") muncul. Kalau belum
 *    diberi PENUH, app TIDAK ditutup — diarahkan ke App Settings untuk memilih
 *    "Allow all" (batasan Android: dialog tak bisa dipaksa muncul lagi setelah
 *    ditolak). App cuma tertutup kalau user menekan "Exit".
 * 2. **All-files access (MANAGE_EXTERNAL_STORAGE)** — layar penjelasan + tombol
 *    "Grant access" yang mengarah ke Settings sistem. WAJIB supaya hapus/rename/
 *    pindah berjalan LANGSUNG tanpa dialog konfirmasi sistem tiap kali, plus
 *    auto-purge Trash 30 hari di background.
 *
 * [content] hanya tampil setelah KEDUA izin terpenuhi.
 */
@Composable
fun MediaPermissionGate(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    var mediaGranted by remember { mutableStateOf(MediaPermissions.hasEssential(context)) }
    var filesGranted by remember { mutableStateOf(AllFilesAccess.isGranted()) }

    when {
        !mediaGranted -> MediaStep(
            modifier = modifier,
            onGranted = {
                mediaGranted = true
                // Bisa saja all-files sudah pernah diberikan di sesi sebelumnya.
                filesGranted = AllFilesAccess.isGranted()
            },
            onExit = { activity?.finishAndRemoveTask() },
        )

        !filesGranted -> AllFilesStep(
            modifier = modifier,
            onGranted = { filesGranted = true },
            onExit = { activity?.finishAndRemoveTask() },
        )

        else -> content()
    }
}

@Composable
private fun MediaStep(
    modifier: Modifier,
    onGranted: () -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    // `denied` = sudah diminta tapi akses PENUH belum diberi.
    var denied by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        // Limited/selected/deny -> JANGAN tutup app; tampilkan penjelasan +
        // arahkan ke App Settings (jalur andal untuk memilih "Allow all").
        if (MediaPermissions.essentialGranted(result)) onGranted() else denied = true
    }
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // Balik dari App Settings -> cek ulang status izin.
        if (MediaPermissions.hasEssential(context)) onGranted() else denied = true
    }

    PermissionBlockingScreen(
        modifier = modifier,
        icon = if (denied) PhosphorIcons.Regular.WarningCircle else PhosphorIcons.Regular.Image,
        title = if (denied) "Full access needed" else "Photos & videos",
        message = if (denied) {
            "AGallery needs access to ALL your photos and videos. " +
                "\"Selected photos\" isn't enough.\n\nOpen app settings → Permissions → " +
                "Photos and videos → choose \"Allow all\"."
        } else {
            "AGallery shows and organizes the photos and videos on your device. " +
                "Allow access to get started."
        },
        primaryLabel = if (denied) "Open app settings" else "Allow access",
        onPrimary = {
            if (denied) {
                settingsLauncher.launch(appSettingsIntent(context))
            } else {
                permissionLauncher.launch(MediaPermissions.required().toTypedArray())
            }
        },
        onExit = onExit,
    )
}

@Composable
private fun AllFilesStep(
    modifier: Modifier,
    onGranted: () -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // Balik dari layar Settings sistem -> cek ulang status izinnya.
        // Kalau belum diberi, TETAP di layar ini (tidak menutup app).
        if (AllFilesAccess.isGranted()) onGranted()
    }
    PermissionBlockingScreen(
        modifier = modifier,
        icon = PhosphorIcons.Regular.FolderSimple,
        title = "All files access",
        message = "To delete, rename and organize your photos directly — and empty " +
            "Trash automatically — AGallery needs \"All files access\".",
        primaryLabel = "Grant access",
        onPrimary = {
            if (AllFilesAccess.isSupported()) {
                launcher.launch(AllFilesAccess.settingsIntent(context))
            } else {
                onGranted()
            }
        },
        onExit = onExit,
    )
}

/**
 * Layar blocking izin bergaya sesuai tema app (Material You): ikon dalam
 * lingkaran ber-warna primaryContainer, judul, penjelasan, tombol utama
 * filled, lalu "Exit" yang halus.
 */
@Composable
private fun PermissionBlockingScreen(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    message: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onExit: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(44.dp),
            )
        }

        Spacer(Modifier.height(28.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 340.dp),
        )

        Spacer(Modifier.height(36.dp))
        Button(
            onClick = onPrimary,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(
                text = primaryLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onExit) {
            Text(
                text = "Exit",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun appSettingsIntent(context: Context): Intent =
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    )

private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
