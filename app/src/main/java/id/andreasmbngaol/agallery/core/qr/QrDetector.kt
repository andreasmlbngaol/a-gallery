package id.andreasmbngaol.agallery.core.qr

import android.content.Context
import androidx.core.net.toUri
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import id.andreasmbngaol.agallery.domain.model.QrContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Pembungkus ML Kit Barcode Scanning (bundled, on-device, offline — tanpa izin
 * INTERNET). Men-scan foto yang sedang dibuka lalu mengubah tiap QR jadi
 * [QrContent] terstruktur (URL/WiFi/Kontak/Email/Telepon/SMS/Geo/Teks).
 *
 * Dipakai fitur QR Detection 1.7.0 di photo viewer.
 */
class QrDetector(private val context: Context) {

    // Batasi ke FORMAT_QR_CODE: fokus fitur = QR, sekaligus lebih cepat & minim
    // false-positive dari barcode 1D.
    private val scanner by lazy {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }

    /**
     * Deteksi semua QR di [uri] (content:// atau file://). Mengembalikan list
     * kosong kalau tidak ada QR / gambar gagal dibaca (TIDAK pernah throw).
     * Rotasi EXIF ditangani otomatis oleh [InputImage.fromFilePath].
     */
    suspend fun detect(uri: String): List<QrContent> = withContext(Dispatchers.IO) {
        val image = try {
            InputImage.fromFilePath(context, uri.toUri())
        } catch (_: Throwable) {
            return@withContext emptyList()
        }
        val barcodes = try {
            awaitScan(image)
        } catch (_: Throwable) {
            emptyList()
        }
        barcodes.mapNotNull { it.toQrContent() }
    }

    private suspend fun awaitScan(image: InputImage): List<Barcode> =
        suspendCancellableCoroutine { cont ->
            scanner.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    private fun Barcode.toQrContent(): QrContent? {
        val text = rawValue ?: displayValue ?: return null
        return when (valueType) {
            Barcode.TYPE_URL ->
                QrContent.Url(text, url?.url ?: text)

            Barcode.TYPE_EMAIL ->
                QrContent.Email(text, email?.address ?: text, email?.subject, email?.body)

            Barcode.TYPE_PHONE ->
                QrContent.Phone(text, phone?.number ?: text)

            Barcode.TYPE_SMS ->
                QrContent.Sms(text, sms?.phoneNumber ?: text, sms?.message)

            Barcode.TYPE_WIFI ->
                QrContent.Wifi(
                    raw = text,
                    ssid = wifi?.ssid,
                    password = wifi?.password,
                    encryption = encryptionLabel(wifi?.encryptionType),
                )

            Barcode.TYPE_CONTACT_INFO -> {
                val info = contactInfo
                QrContent.Contact(
                    raw = text,
                    name = info?.name?.formattedName?.takeIf { it.isNotBlank() },
                    phone = info?.phones?.firstOrNull()?.number,
                    email = info?.emails?.firstOrNull()?.address,
                    organization = info?.organization?.takeIf { it.isNotBlank() },
                )
            }

            Barcode.TYPE_GEO -> {
                val point = geoPoint
                if (point != null) {
                    QrContent.Geo(text, point.lat, point.lng)
                } else {
                    QrContent.Text(text)
                }
            }

            else -> QrContent.Text(text)
        }
    }

    private fun encryptionLabel(type: Int?): String? = when (type) {
        Barcode.WiFi.TYPE_OPEN -> "Open"
        Barcode.WiFi.TYPE_WPA -> "WPA"
        Barcode.WiFi.TYPE_WEP -> "WEP"
        else -> null
    }
}
