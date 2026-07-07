package id.andreasmbngaol.agallery.presentation.viewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.ArrowSquareOut
import com.adamglin.phosphoricons.bold.Copy
import com.kyant.backdrop.Backdrop
import id.andreasmbngaol.agallery.R
import id.andreasmbngaol.agallery.domain.model.settings.ComponentStyle
import id.andreasmbngaol.agallery.domain.model.qr.QrContent

/**
 * Chip kaca kecil yang muncul di pojok kanan-bawah foto saat ada QR terdeteksi
 * (hanya tampil kalau chrome/tombol tampil). Ditekan -> buka [QrResultSheet].
 * Pakai [GlassActionButton] biar seragam dgn tema app (SOLID/FROSTED/GLASS).
 */
@Composable
fun QrDetectedChip(
    count: Int,
    style: ComponentStyle,
    backdrop: Backdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = if (count > 1) {
        stringResource(R.string.qr_detect_chip_multi, count)
    } else {
        stringResource(R.string.qr_detect_chip)
    }
    GlassActionButton(
        text = label,
        onClick = onClick,
        style = style,
        backdrop = backdrop,
        modifier = modifier,
    )
}

/**
 * Bottom sheet berisi daftar SEMUA QR yang terdeteksi di foto. Tiap kartu
 * menampilkan tipe konten + nilai utama + field pendukung (SSID/kata sandi utk
 * WiFi, telepon/email utk kontak, dll), plus tombol Salin dan tombol Buka
 * kontekstual (link -> browser, telepon -> dialer, email -> mailto, geo -> peta).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrResultSheet(
    results: List<QrContent>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded), // skip PartiallyExpanded
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = if (results.size > 1) {
                    stringResource(R.string.qr_detect_sheet_title_multi, results.size)
                } else {
                    stringResource(R.string.qr_detect_sheet_title)
                },
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(16.dp))
            results.forEachIndexed { index, content ->
                QrResultCard(content)
                if (index != results.lastIndex) Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun QrResultCard(content: QrContent) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(content.typeLabelRes()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = content.primaryText(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            // Field pendukung terlokalisasi per tipe.
            when (content) {
                is QrContent.Wifi -> {
                    content.password?.let { DetailLine(stringResource(R.string.qr_wifi_password), it) }
                    content.encryption?.let { DetailLine(stringResource(R.string.qr_wifi_security), it) }
                }
                is QrContent.Contact -> {
                    content.phone?.let { DetailLine(stringResource(R.string.qr_contact_phone), it) }
                    content.email?.let { DetailLine(stringResource(R.string.qr_contact_email), it) }
                    content.organization?.let { DetailLine(stringResource(R.string.qr_contact_org), it) }
                }
                is QrContent.Email -> {
                    content.subject?.let { DetailLine(stringResource(R.string.qr_email_subject), it) }
                }
                else -> Unit
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { copyToClipboard(context, content.raw) }) {
                    Icon(
                        imageVector = PhosphorIcons.Bold.Copy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.qr_detect_copy))
                }
                content.openUri()?.let { uri ->
                    OutlinedButton(onClick = { openUri(context, uri) }) {
                        Icon(
                            imageVector = PhosphorIcons.Bold.ArrowSquareOut,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(content.openLabelRes()))
                    }
                }
            }
        }
    }
}

/** Baris "Label: value" kecil utk field pendukung (WiFi/kontak/email). */
@Composable
private fun DetailLine(label: String, value: String) {
    Spacer(Modifier.height(4.dp))
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Salin teks mentah QR ke clipboard sistem (framework, bukan Compose API). */
private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("QR", text))
    Toast.makeText(context, context.getString(R.string.qr_detect_copied), Toast.LENGTH_SHORT).show()
}

/** Buka [uri] via ACTION_VIEW; toast kalau tak ada app yg menangani. */
private fun openUri(context: Context, uri: String) {
    val ok = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()))
    }.isSuccess
    if (!ok) {
        Toast.makeText(context, context.getString(R.string.qr_detect_no_app), Toast.LENGTH_SHORT).show()
    }
}

/** Nilai utama yang ditonjolkan di kartu (baris pertama tebal). */
private fun QrContent.primaryText(): String = when (this) {
    is QrContent.Url -> url
    is QrContent.Email -> address
    is QrContent.Phone -> number
    is QrContent.Sms -> number
    is QrContent.Wifi -> ssid ?: raw
    is QrContent.Contact -> name ?: organization ?: raw
    is QrContent.Geo -> "$latitude, $longitude"
    is QrContent.Text -> raw
}

@StringRes
private fun QrContent.typeLabelRes(): Int = when (this) {
    is QrContent.Url -> R.string.qr_type_url
    is QrContent.Email -> R.string.qr_type_email
    is QrContent.Phone -> R.string.qr_type_phone
    is QrContent.Sms -> R.string.qr_type_sms
    is QrContent.Wifi -> R.string.qr_type_wifi
    is QrContent.Contact -> R.string.qr_type_contact
    is QrContent.Geo -> R.string.qr_type_geo
    is QrContent.Text -> R.string.qr_type_text
}

@StringRes
private fun QrContent.openLabelRes(): Int = when (this) {
    is QrContent.Url -> R.string.qr_open_link
    is QrContent.Email -> R.string.qr_open_email
    is QrContent.Phone -> R.string.qr_open_dial
    is QrContent.Sms -> R.string.qr_open_sms
    is QrContent.Geo -> R.string.qr_open_maps
    else -> R.string.qr_detect_open
}
