package id.andreasmbngaol.agallery.domain.model.qr

/**
 * Result of detecting a single QR from a photo. Pure Kotlin — do NOT import
 * android.* here. [raw] always holds the exact raw text as stored in the QR
 * (used by the Copy button); each subtype stores structured fields for display
 * and contextual actions (open link / call / email / map, etc.).
 *
 * Used by the QR Detection feature (1.7.0).
 */
sealed interface QrContent {
    /** Exact raw text as stored in the QR (used by the Copy button). */
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
     * URI for the contextual "Open" action, or null when there is nothing sensible
     * to open (WiFi/Contact/Text). Built as a plain string so the domain stays
     * free of Android dependencies; the UI simply wraps it into an ACTION_VIEW
     * intent.
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
