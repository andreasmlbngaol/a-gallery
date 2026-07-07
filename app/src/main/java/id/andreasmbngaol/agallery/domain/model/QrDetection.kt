package id.andreasmbngaol.agallery.domain.model

/**
 * Hasil deteksi 1 QR dari sebuah foto. Pure Kotlin — JANGAN import android.* di
 * sini. [raw] selalu berisi teks mentah persis seperti tersimpan di QR (dipakai
 * tombol Copy); tiap subtipe menyimpan field terstruktur utk tampilan & aksi
 * kontekstual (Buka link / telepon / email / peta, dll).
 *
 * Dipakai fitur QR Detection 1.7.0.
 */
sealed interface QrContent {
    /** Teks mentah persis seperti tersimpan di QR (dipakai tombol Copy). */
    val raw: String

    data class Url(override val raw: String, val url: String) : QrContent

    data class Email(
        override val raw: String,
        val address: String,
        val subject: String?,
        val body: String?,
    ) : QrContent

    data class Phone(override val raw: String, val number: String) : QrContent

    data class Sms(override val raw: String, val number: String, val message: String?) : QrContent

    data class Wifi(
        override val raw: String,
        val ssid: String?,
        val password: String?,
        val encryption: String?,
    ) : QrContent

    data class Contact(
        override val raw: String,
        val name: String?,
        val phone: String?,
        val email: String?,
        val organization: String?,
    ) : QrContent

    data class Geo(
        override val raw: String,
        val latitude: Double,
        val longitude: Double,
    ) : QrContent

    data class Text(override val raw: String) : QrContent

    /**
     * URI untuk aksi "Buka" kontekstual, atau null kalau tak ada yang wajar
     * dibuka (WiFi/Contact/Text). Dibangun sebagai string murni supaya domain
     * tetap bebas dependensi Android; UI tinggal membungkusnya jadi Intent
     * ACTION_VIEW.
     */
    fun openUri(): String? = when (this) {
        is Url -> url
        is Email -> "mailto:$address"
        is Phone -> "tel:$number"
        is Sms -> "smsto:$number"
        is Geo -> "geo:$latitude,$longitude?q=$latitude,$longitude"
        is Wifi, is Contact, is Text -> null
    }
}
