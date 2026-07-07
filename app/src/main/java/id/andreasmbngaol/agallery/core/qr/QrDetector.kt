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
 * Wrapper around ML Kit Barcode Scanning (bundled, on-device, offline — no
 * `INTERNET` permission).
 *
 * Scans the currently open photo and converts each detected QR code into a
 * structured [QrContent] (URL, Wi-Fi, contact, email, phone, SMS, geo, or
 * plain text). Used by the QR Detection feature in the photo viewer.
 *
 * @property context context used to build [InputImage]s and the scanner client.
 */
class QrDetector(private val context: Context) {

    /**
     * Lazily created scanner limited to [Barcode.FORMAT_QR_CODE]: the feature
     * targets QR only, which is also faster and avoids false positives from 1D
     * barcodes.
     */
    private val scanner by lazy {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }

    /**
     * Detects every QR code in the image at [uri] (`content://` or `file://`).
     *
     * EXIF rotation is handled automatically by [InputImage.fromFilePath]. This
     * never throws: an empty list is returned when no QR code is found or the
     * image cannot be read.
     *
     * @param uri the image location to scan.
     * @return the structured contents of each detected QR code, possibly empty.
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

    /**
     * Suspends until the ML Kit scanner finishes processing [image].
     *
     * @param image the image to scan.
     * @return the raw barcodes detected by ML Kit.
     */
    private suspend fun awaitScan(image: InputImage): List<Barcode> =
        suspendCancellableCoroutine { cont ->
            scanner.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    /**
     * Maps an ML Kit [Barcode] to the app's [QrContent], or `null` when it has
     * no readable value. Unrecognized value types fall back to [QrContent.Text].
     */
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

    /**
     * Converts an ML Kit Wi-Fi encryption type constant to a display label, or
     * `null` when unknown.
     *
     * @param type the ML Kit `Barcode.WiFi` encryption type constant.
     * @return the human-readable label, or `null` if unrecognized.
     */
    private fun encryptionLabel(type: Int?): String? = when (type) {
        Barcode.WiFi.TYPE_OPEN -> "Open"
        Barcode.WiFi.TYPE_WPA -> "WPA"
        Barcode.WiFi.TYPE_WEP -> "WEP"
        else -> null
    }
}
